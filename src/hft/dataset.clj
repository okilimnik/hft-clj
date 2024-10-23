(ns hft.dataset
  (:require [clojure.core.async :as a :refer [<!! thread]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [hft.gcloud :as gcloud]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]
            [hft.strategy :as strategy :refer [DATAFILE KLINES-SERIES-LENGTH SYMBOL]])
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]))

;; ["BNBUSDT" "BTCUSDT" "ETHUSDT" "SOLUSDT" "PEPEUSDT" "NEIROUSDT" "DOGSUSDT" "WIFUSDT" "FETUSDT" "SAGAUSDT"]

(def INPUT-SIZE 20)
(def PRICE-PERCENT-FOR-INDEXING 0.002)

(defn get-image-column [min-price max-price price-interval prices]
  (loop [prices prices
         result (vec (repeat INPUT-SIZE 0))]
    (if (seq prices)
      (let [[price-str qty-str] (first prices)
            price (parse-double price-str)
            qty (parse-double qty-str)]
        (if (and (< price max-price) (>= price min-price))
          (let [level (int (/ (* (- price min-price) INPUT-SIZE) price-interval))]
            (recur (rest prices) (update result level #(+ (or % 0) qty))))
          result))
      result)))

(defn get-min-price [mid-price]
  (- mid-price (* mid-price PRICE-PERCENT-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-max-price [mid-price]
  (+ mid-price (* mid-price PRICE-PERCENT-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))]
    {:bids bids
     :asks asks}))

(defn range-market-pipeline []
  (println "SYMBOL is: " SYMBOL)
  (.mkdirs (io/file "charts"))
  (.mkdirs (io/file "books"))
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)
        klines (atom clojure.lang.PersistentQueue/EMPTY)]
    (<!!
     (a/merge
      [(scheduler/start!
        6000
        (fn []
          (let [order-book (bi/depth! SYMBOL 5000)]
            (swap! max-bids #(as-> % $
                               (conj $ (parse-double (ffirst (:bids order-book))))
                               (if (> (count $) INPUT-SIZE)
                                 (pop $)
                                 $)))
            (swap! inputs #(as-> % $
                             (conj $ (order-book->quantities-indexed-by-price-level order-book (apply max @max-bids)))
                             (if (> (count $) INPUT-SIZE)
                               (pop $)
                               $))))))
       (thread
         (let [klines-1m (str (str/lower-case SYMBOL) "@kline_1m")]
           (bi/subscribe [klines-1m]
                         (reify WebSocketMessageCallback
                           ^void (onMessage [_ event-str]
                                            (try
                                              (let [event (bi/jread event-str)
                                                    data (:data event)]
                                                (cond

                                                  #_(= (:stream event) depth)
                                                  #_(if @order-book-warmed-up?
                                                      (update-order-book order-book data)
                                                      (do
                                                        (swap! order-book-warming-buffer conj data)
                                                        (when (> (count @order-book-warming-buffer) 10)
                                                          (init-order-book! order-book)
                                                          (doseq [buffered-data @order-book-warming-buffer]
                                                            (update-order-book order-book buffered-data))
                                                          (reset! order-book-warmed-up? true))))

                                                  (and (= (:stream event) klines-1m)
                                                       ;; kline closed
                                                       (:x (:k data)))
                                                  (let [kline (:k data)]
                                                    (swap! klines #(as-> % $
                                                                     (conj $ (-> kline
                                                                                 (select-keys [:t :o :h :l :c :v :n])
                                                                                 (update :o parse-double)
                                                                                 (update :h parse-double)
                                                                                 (update :l parse-double)
                                                                                 (update :c parse-double)
                                                                                 (update :v parse-double)))
                                                                     (if (> (count $) KLINES-SERIES-LENGTH)
                                                                       (pop $)
                                                                       $)))
                                                    (strategy/trade! {:klines @klines
                                                                      :inputs @inputs}))))
                                              (catch Exception e (prn e))))))))

       (scheduler/start!
        (* 3 60 60 1000) ;; every 3 hour
        (fn []
          (let [f (io/file DATAFILE)]
            (prn "checking data file")
            (when (.exists f)
              (prn "uploading data file")
              (gcloud/upload-file! f)))
          (doseq [f (file-seq (io/file "charts"))]
            (when-not (.isDirectory f)
              (gcloud/upload-file! f)
              (.delete f)))
          (doseq [f (file-seq (io/file "books"))]
            (when-not (.isDirectory f)
              (gcloud/upload-file! f)
              (.delete f)))))]))))

(defn trend-market-pipeline []
  (println "SYMBOL is: " SYMBOL))