(ns hft.dataset
  (:require [clojure.core.async :as a :refer [<!!]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hft.async :refer [vthread]]
            [hft.gcloud :as gcloud]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler])
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]
           [java.time Instant ZoneId ZonedDateTime]
           [org.ta4j.core BarSeries BaseBarSeries]
           [org.ta4j.core.indicators ParabolicSarIndicator]
           [org.ta4j.core.indicators.adx ADXIndicator]))

;; ["BNBUSDT" "BTCUSDT" "ETHUSDT" "SOLUSDT" "PEPEUSDT" "NEIROUSDT" "DOGSUSDT" "WIFUSDT" "FETUSDT" "SAGAUSDT"]
(def SYMBOL (System/getenv "SYMBOL"))
(def INPUT-SIZE 20)
(def DATAFILE (str SYMBOL ".tsv"))
(def PRICE-PERCENT-FOR-INDEXING 0.002)
(def STOP-PROFIT-PRICE-PERCENT 0.0005)
(def STOP-LOSS-PRICE-PERCENT 0.001)
(def TREND-STRENGTH-THRESHOLD 20)

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

(def order (atom nil))

(defn open-order [price inputs]
  (reset! order {:price price
                 :inputs inputs}))

(defn ->tsv [label data]
  (let [qties (concat (mapcat :bids data)
                      (mapcat :asks data))
        max-qty (apply max qties)
        min-qty (apply min qties)
        ratio (/ 255.0 (- max-qty min-qty))
        scale #(-> %
                   (- min-qty)
                   (* ratio)
                   long)]
    (spit DATAFILE (str/join " " (concat [label]
                                         (mapcat #(map scale (:bids %)) data)
                                         (mapcat #(map scale (:asks %)) data)
                                         ["\n"])) :append true)))

(defn close-order [price]
  (if (>= (- price (:price @order)) (* price STOP-PROFIT-PRICE-PERCENT))
    (->tsv 1 (:inputs @order))
    (->tsv 0 (:inputs @order)))
  (reset! order nil))

(defn pipeline-v1 []
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
       (vthread
        (let [series-length 50
              klines-1m (str (str/lower-case SYMBOL) "@kline_1m")]
          (bi/subscribe [klines-1m]
                        (reify WebSocketMessageCallback
                          ^void (onMessage [_ event-str]
                                           (try
                                             (let [event (bi/jread event-str)
                                                   data (:k (:data event))
                                                   kline-closed? (:x data)
                                                   current-price (:c (first @klines))]
                                               (when (and @order
                                                          (>= (- (:price @order) current-price)
                                                              (* current-price STOP-LOSS-PRICE-PERCENT)))
                                                 ;; closing with loss
                                                 (close-order (:c (first @klines))))
                                               (cond
                                                 (and (= klines-1m (:stream event))
                                                      kline-closed?)
                                                 (do (swap! klines #(as-> % $
                                                                      (conj $ (-> data
                                                                                  (select-keys [:t :o :h :l :c :v :n])
                                                                                  (update :o parse-double)
                                                                                  (update :h parse-double)
                                                                                  (update :l parse-double)
                                                                                  (update :c parse-double)
                                                                                  (update :v parse-double)))
                                                                      (if (> (count $) series-length)
                                                                        (pop $)
                                                                        $)))
                                                     (when (>= (count @klines) series-length) ;; warmed-up

                                                       (let [series (BaseBarSeries. "1m")]
                                                         (doseq [{:keys [o h l c t v n]} @klines]
                                                           (let [date (ZonedDateTime/ofInstant
                                                                       (Instant/ofEpochMilli t)
                                                                       (ZoneId/systemDefault))]
                                                             (.addBar ^BarSeries series date o h l c v n)))
                                                         (let [psar (ParabolicSarIndicator. series)
                                                               adx (ADXIndicator. series INPUT-SIZE)
                               
                                                               curr-index (dec series-length)
                                                               adx-value (.longValue (.getValue adx curr-index))
                                                               psar-value (.doubleValue (.getValue psar curr-index))]
                                                           (cond
                                                             (> psar-value (:h (nth @klines curr-index)))
                                                             (do (prn "sell signal")
                                                                 (when @order
                                                                   (close-order (:c (nth @klines curr-index))))
                                                                 #_(->> @inputs
                                                                        save!))
                                                             (and (< psar-value (:l (nth @klines curr-index)))
                                                                  (> adx-value TREND-STRENGTH-THRESHOLD))
                                                             (do (prn "buy signal")
                                                                 (open-order (:c (nth @klines curr-index)) @inputs)))))))))
                                             (catch Exception e (prn e))))))))

       (scheduler/start!
        (* 3 60 60 1000) ;; every 3 hour
        (fn []
          (let [f (io/file DATAFILE)]
            (when (.exists f)
              (gcloud/upload-file! f)))))]))))