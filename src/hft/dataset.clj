(ns hft.dataset
  (:require [clojure.core.async :as a :refer [<!! thread]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [hft.chart :refer [->chart with-indicator] :as chart]
            [hft.gcloud :as gcloud]
            [hft.image :as i]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler])
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]
           [java.time Instant ZoneId ZonedDateTime]
           [org.ta4j.core BarSeries BaseBarSeries]
           [org.ta4j.core.indicators.helpers ClosePriceIndicator]
           [org.ta4j.core.indicators.ichimoku IchimokuChikouSpanIndicator IchimokuKijunSenIndicator IchimokuTenkanSenIndicator]
           [org.ta4j.core.rules CrossedDownIndicatorRule CrossedUpIndicatorRule IsRisingRule]))

;; ["BNBUSDT" "BTCUSDT" "ETHUSDT" "SOLUSDT" "PEPEUSDT" "NEIROUSDT" "DOGSUSDT" "WIFUSDT" "FETUSDT" "SAGAUSDT"]
(def SYMBOL (or (System/getenv "SYMBOL") "BTCUSDT"))
(def MAX-QUANTITY {"BTCUSDT" 100
                   "BNBUSDT" 100})
(def INPUT-SIZE 20)
(def DATAFILE (str SYMBOL ".tsv"))
(def PRICE-PERCENT-FOR-INDEXING 0.002)
(def STOP-PROFIT-PRICE-PERCENT 0.0005)
(def STOP-LOSS-PRICE-PERCENT 0.003)
(def TREND-STRENGTH-THRESHOLD 50)
(def KLINES-SERIES-LENGTH 110)
(def ICHIMOKU-PERIOD 30)

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

(defn open-order [price inputs chart]
  (reset! order {:price price
                 :inputs inputs
                 :chart chart
                 :timestamp (System/currentTimeMillis)}))

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
  (let [label (if (>= (- price (:price @order)) (* price STOP-PROFIT-PRICE-PERCENT)) 1 0)
        suffix (str (:timestamp @order) "_" label "_" price ".png")]
    (->tsv label (:inputs @order))
    (chart/->image (:chart @order) (str "charts/chart_" suffix))
    (let [image (i/->image {:data (:inputs @order)
                            :max-value (get MAX-QUANTITY SYMBOL)})]
      (i/save image (str "books/book_ " suffix))))
  (reset! order nil))

(defn update-prices [book price-changes]
  (doseq [[price volume] price-changes]
    (if (> (parse-double volume) 1)
      (assoc! book price volume)
      (dissoc! book price))))

(defn update-order-book [order-book data]
  (when (> (:u data) (:lastUpdateId @order-book))
    (swap! order-book #(-> %
                           (assoc :lastUpdateId (:u data))
                           (update :bids (fn [bids] (update-prices bids (:b data))))
                           (update :asks (fn [asks] (update-prices asks (:a data))))))))

(defn init-order-book! [order-book]
  (let [data (bi/depth! SYMBOL 5000)]
    (update-order-book order-book (set/rename-keys data {:asks :a :bids :b :lastUpdateId :u}))))

(defn klines->series [name klines]
  (let [series (BaseBarSeries. name)]
    (doseq [{:keys [o h l c t v n]} klines]
      (let [date (ZonedDateTime/ofInstant
                  (Instant/ofEpochMilli t)
                  (ZoneId/systemDefault))]
        (.addBar ^BarSeries series date o h l c v n)))
    series))

(defn range-market-pipeline []
  (println "SYMBOL is: " SYMBOL)
  (.mkdirs (io/file "charts"))
  (.mkdirs (io/file "books"))
  (let [;order-book (atom {:lastUpdateId 0 :bids (transient {})  :asks (transient {})})
        inputs (atom clojure.lang.PersistentQueue/EMPTY)
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
         ;(init-order-book! order-book)
         (let [klines-1m (str (str/lower-case SYMBOL) "@kline_1m")
               depth (str (str/lower-case SYMBOL) "@depth")
               order-book-warming-buffer (atom (transient []))
               order-book-warmed-up? (atom false)]
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
                                                    (let [price (:c (last @klines))]
                                                      (when (and @order
                                                                 (>= (- (:price @order) price)
                                                                     (* price STOP-LOSS-PRICE-PERCENT)))
                                                         ;; closing with loss
                                                        (close-order price))
                                                      (when (>= (count @klines) KLINES-SERIES-LENGTH) ;; warmed-up
                                                        (let [series (klines->series "1m" @klines)
                                                              chikou (IchimokuChikouSpanIndicator. series ICHIMOKU-PERIOD)
                                                              kijun (IchimokuKijunSenIndicator. series ICHIMOKU-PERIOD)
                                                              tenkan (IchimokuTenkanSenIndicator. series 9)
                                                              close-prices (ClosePriceIndicator. series)
                                                              buy-rule-1 (CrossedDownIndicatorRule. chikou close-prices)
                                                              buy-rule-2 (IsRisingRule. tenkan 2)

                                                              buy-signal? (and
                                                                           ;; chikou just has crossed the prices
                                                                           (.isSatisfied buy-rule-1 (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD) nil)
                                                                           (not (.isSatisfied buy-rule-1 (dec (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD)) nil))
                                                                           ;; it's trendy now
                                                                           (.isSatisfied buy-rule-2 (dec KLINES-SERIES-LENGTH) nil))
                                                              sell-rule-1 (CrossedDownIndicatorRule. kijun close-prices)
                                                              sell-rule-2 (CrossedUpIndicatorRule. chikou close-prices)
                                                              sell-signal? (or
                                                                            ;; kijun just has crossed the prices
                                                                            (.isSatisfied sell-rule-1 (dec KLINES-SERIES-LENGTH) nil)
                                                                            (not (.isSatisfied sell-rule-1 (dec (dec KLINES-SERIES-LENGTH)) nil))
                                                                            ;; chikou just has crossed the prices
                                                                            (.isSatisfied sell-rule-2 (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD) nil)
                                                                            (not (.isSatisfied sell-rule-2 (dec (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD)) nil)))]

                                                          (cond
                                                            buy-signal?
                                                            (do (prn "buy signal")
                                                                (when-not @order
                                                                  (let [chart (-> (->chart "Buy signal" @klines)
                                                                                  (with-indicator chikou :overlay :line 0)
                                                                                  (with-indicator kijun :overlay :line 1)
                                                                                  (with-indicator tenkan :overlay :line 2))]
                                                                    (open-order price @inputs chart))))

                                                            sell-signal?
                                                            (do (prn "sell signal")
                                                                (when @order
                                                                  (close-order price)))))

                                                         ;; logic for trend market
                                                        #_(let [psar (ParabolicSarIndicator. series)
                                                                adx (ADXIndicator. series INPUT-SIZE)
                                                                curr-index (dec KLINES-SERIES-LENGTH)
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