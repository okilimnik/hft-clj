(ns hft.dataset
  (:require
   [clojure.core.async :as a :refer [<!! thread]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hft.chart :as chart]
   [hft.gcloud :as gcloud]
   [hft.market.binance :as bi]
   [hft.xtdb :as db]
   [hft.scheduler :as scheduler]
   [hft.strategy :as strategy :refer [DATAFILE klines->series
                                      KLINES-SERIES-LENGTH SYMBOL]])
  (:import
   [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]
   [org.ta4j.core.indicators RSIIndicator]
   [org.ta4j.core.indicators.helpers ClosePriceIndicator]))

;; ["BNBUSDT" "BTCUSDT" "ETHUSDT" "SOLUSDT" "PEPEUSDT" "NEIROUSDT" "DOGSUSDT" "WIFUSDT" "FETUSDT" "SAGAUSDT"]

(def INPUT-SIZE 20)
(def PRICE-PERCENT-FOR-INDEXING 0.002)
(def FETCH-INTERVAL-SECONDS 6)

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

(defn get-levels-with-max-qty-sorted [prices]
  (->> (map-indexed vector prices)
       (sort-by second >)
       (take 3)
       vec))

(defn get-distance-from-terminator [prices terminator]
  (abs (- (ffirst (get-levels-with-max-qty-sorted prices)) terminator)))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))]
    {:bids bids
     :asks asks
     :max-ask (second (first (get-levels-with-max-qty-sorted asks)))
     :max-bid (second (first (get-levels-with-max-qty-sorted bids)))
     :max-ask-distance (get-distance-from-terminator asks 10)
     :max-bid-distance (get-distance-from-terminator bids 9)}))

(defn range-market-pipeline [{:keys [on-update]}]
  (println "SYMBOL is: " SYMBOL)
  ;(.delete (io/file DATAFILE))
  (.mkdirs (io/file "charts"))
  (.mkdirs (io/file "books"))
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)
        klines (atom clojure.lang.PersistentQueue/EMPTY)]
    (<!!
     (a/merge
      [(scheduler/start!
        (* 1000 FETCH-INTERVAL-SECONDS)
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
                               $)))
            (when (= (count @inputs) INPUT-SIZE)
              (db/put! {:xt/id (str SYMBOL "-" FETCH-INTERVAL-SECONDS "-" INPUT-SIZE "-" (System/currentTimeMillis))
                        :order-book/symbol SYMBOL
                        :order-book/bids (vec
                                          (map-indexed (fn [level volume]
                                                         {:price/level level
                                                          :price/quantity volume})
                                                       (-> @inputs last :bids)))
                        :order-book/asks (vec
                                          (map-indexed (fn [level volume]
                                                         {:price/level level
                                                          :price/quantity volume})
                                                       (-> @inputs last :asks)))})))))
       #_(thread
           (let [klines-1m (str (str/lower-case SYMBOL) "@kline_1m")]
             (bi/subscribe [klines-1m]
                           (reify WebSocketMessageCallback
                             ^void (onMessage [_ event-str]
                                              (try
                                                (let [event (bi/jread event-str)
                                                      data (:data event)]
                                                  (cond

                                                    (= (:stream event) klines-1m)
                                                    (let [kline (:k data)
                                                          new-klines (as-> @klines $
                                                                       (conj $ (-> kline
                                                                                   (select-keys [:t :o :h :l :c :v :n])
                                                                                   (update :o parse-double)
                                                                                   (update :h parse-double)
                                                                                   (update :l parse-double)
                                                                                   (update :c parse-double)
                                                                                   (update :v parse-double)))
                                                                       (if (> (count $) KLINES-SERIES-LENGTH)
                                                                         (pop $)
                                                                         $))
                                                          price-level-size (* PRICE-PERCENT-FOR-INDEXING (:c (last new-klines)))]
                                                      (when (:x (:k data)) ;; kline closed
                                                        (reset! klines new-klines))
                                                      #_(strategy/trade! {:klines new-klines
                                                                          :inputs @inputs
                                                                          :price-level-size price-level-size})
                                                    ;; update ui
                                                      #_(when (and on-update (>= (count new-klines) KLINES-SERIES-LENGTH))
                                                          (let [series (klines->series "1m" klines)
                                                                close-prices (ClosePriceIndicator. series)
                                                                rsi (RSIIndicator. close-prices 15)
                                                                chart (-> (chart/->chart "Buy signal" klines)
                                                                          (chart/with-indicator rsi :subplot :line 0))
                                                                chart-path (str "charts/chart.png")]
                                                            (chart/->image chart chart-path)
                                                            (on-update {:chart chart-path}))))))
                                                (catch Exception e (prn e))))))))

       (scheduler/start!
        (* 1 60 60 1000) ;; every 1 hour
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