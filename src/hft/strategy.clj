(ns hft.strategy
  (:require
   [clojure.string :as str]
   [hft.chart :as chart]
   [hft.image :as i])
  (:import
   [java.time Instant ZoneId ZonedDateTime]
   [org.ta4j.core BarSeries BaseBarSeries]
   [org.ta4j.core.indicators RSIIndicator]
   [org.ta4j.core.indicators.helpers ClosePriceIndicator]))

(def order (atom nil))
(def SYMBOL (or (System/getenv "SYMBOL") "BTCUSDT"))
(def MAX-QUANTITY {"BTCUSDT" 100
                   "BNBUSDT" 100})
(def STOP-LOSS-PRICE-PERCENT 0.003)
(def DATAFILE (str SYMBOL ".tsv"))
(def KLINES-SERIES-LENGTH 110)

(defn open-order [price stop-profit-price inputs chart]
  (reset! order {:price price
                 :stop-profit-price stop-profit-price
                 :inputs inputs
                 :chart chart
                 :timestamp (System/currentTimeMillis)}))

(defn klines->series [name klines]
  (let [series (BaseBarSeries. name)]
    (doseq [{:keys [o h l c t v n]} klines]
      (let [date (ZonedDateTime/ofInstant
                  (Instant/ofEpochMilli t)
                  (ZoneId/systemDefault))]
        (.addBar ^BarSeries series date o h l c v n)))
    series))

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
  (let [label (if (> price (:price @order)) 1 0)
        suffix (str (:timestamp @order) "_" label "_" price ".png")]
    (->tsv label (:inputs @order))
    (chart/->image (:chart @order) (str "charts/chart_" suffix))
    (let [image (i/->image {:data (:inputs @order)
                            :max-value (get MAX-QUANTITY SYMBOL)})]
      (i/save image (str "books/book_ " suffix))))
  (reset! order nil))

(defn trade! [{:keys [klines inputs price-level-size]}]
  (let [price (:c (last klines))]
    (when (and @order
               (or
                ;; loss
                (>= (- (:price @order) price)
                    (* price STOP-LOSS-PRICE-PERCENT))
                ;; profit
                (>= price (:stop-profit-price @order))))
      (close-order price))
    (when (>= (count klines) KLINES-SERIES-LENGTH) ;; warmed-up
      (let [series (klines->series "1m" klines)
            close-prices (ClosePriceIndicator. series)
            rsi (RSIIndicator. close-prices 15)
            max-ask-distance (:max-ask-distance (last inputs))

            buy-signal? (and
                         (> max-ask-distance 2)
                         (< 50 (.doubleValue (.getValue rsi (- KLINES-SERIES-LENGTH 1))))
                         (>= 50 (.doubleValue (.getValue rsi (- KLINES-SERIES-LENGTH 2)))))]

        (when buy-signal?
          (prn "buy signal")
          (when-not @order
            (let [chart (-> (chart/->chart "Buy signal" klines)
                            (chart/with-indicator rsi :overlay :line 0))
                  stop-profit-price (+ price (* price-level-size max-ask-distance))]
              (open-order price stop-profit-price inputs chart))))))))