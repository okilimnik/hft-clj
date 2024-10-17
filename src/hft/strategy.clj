(ns hft.strategy
  (:require [clojure.string :as str]
            [hft.chart :as chart]
            [hft.image :as i])
  (:import [java.time Instant ZoneId ZonedDateTime]
           [org.ta4j.core BarSeries BaseBarSeries]
           [org.ta4j.core.indicators.helpers ClosePriceIndicator HighPriceIndicator]
           [org.ta4j.core.indicators.ichimoku IchimokuChikouSpanIndicator IchimokuKijunSenIndicator IchimokuTenkanSenIndicator]
           [org.ta4j.core.rules CrossedDownIndicatorRule IsRisingRule]))

(def order (atom nil))
(def SYMBOL (or (System/getenv "SYMBOL") "BTCUSDT"))
(def MAX-QUANTITY {"BTCUSDT" 100
                   "BNBUSDT" 100})
(def STOP-PROFIT-PRICE-PERCENT 0.0005)
(def STOP-LOSS-PRICE-PERCENT 0.003)
(def TREND-STRENGTH-THRESHOLD 50)
(def ICHIMOKU-PERIOD 30)
(def DATAFILE (str SYMBOL ".tsv"))
(def KLINES-SERIES-LENGTH 110)

(defn open-order [price inputs chart]
  (reset! order {:price price
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
  (let [label (if (>= (- price (:price @order)) (* price STOP-PROFIT-PRICE-PERCENT)) 1 0)
        suffix (str (:timestamp @order) "_" label "_" price ".png")]
    (->tsv label (:inputs @order))
    (chart/->image (:chart @order) (str "charts/chart_" suffix))
    (let [image (i/->image {:data (:inputs @order)
                            :max-value (get MAX-QUANTITY SYMBOL)})]
      (i/save image (str "books/book_ " suffix))))
  (reset! order nil))

(defn trade! [{:keys [klines inputs]}]
  (let [price (:c (last @klines))]
    (when (and @order
               (>= (- (:price @order) price)
                   (* price STOP-LOSS-PRICE-PERCENT)))
      ;; closing with loss
      (close-order price))
    (when (>= (count @klines) KLINES-SERIES-LENGTH) ;; warmed-up
      (let [series (klines->series "1m" klines)
            chikou (IchimokuChikouSpanIndicator. series ICHIMOKU-PERIOD)
            kijun (IchimokuKijunSenIndicator. series ICHIMOKU-PERIOD)
            tenkan (IchimokuTenkanSenIndicator. series 9)
            close-prices (ClosePriceIndicator. series)
            high-prices (HighPriceIndicator. series)
            buy-rule-1 (CrossedDownIndicatorRule. chikou high-prices)
            buy-rule-2 (IsRisingRule. tenkan 2)

            buy-signal? (and
                        ;; chikou just has crossed the prices
                         (.isSatisfied buy-rule-1 (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD) nil)
                         (not (.isSatisfied buy-rule-1 (dec (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD)) nil))

                         ;; it's trendy now
                         #_(.isSatisfied buy-rule-2 (dec KLINES-SERIES-LENGTH) nil))
            sell-rule-1 (CrossedDownIndicatorRule. tenkan close-prices)
                                                                ;sell-rule-2 (CrossedUpIndicatorRule. chikou close-prices)
            sell-signal? (or
                                                                              ;; kijun just has crossed the prices
                          (.isSatisfied sell-rule-1 (dec KLINES-SERIES-LENGTH) nil)
                          (not (.isSatisfied sell-rule-1 (dec (dec KLINES-SERIES-LENGTH)) nil))
                                                                              ;; chikou just has crossed the prices
                          #_(.isSatisfied sell-rule-2 (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD) nil)
                          #_(not (.isSatisfied sell-rule-2 (dec (- KLINES-SERIES-LENGTH ICHIMOKU-PERIOD)) nil)))]

        (cond
          buy-signal?
          (do (prn "buy signal")
              (when-not @order
                (let [chart (-> (chart/->chart "Buy signal" klines)
                                (chart/with-indicator chikou :overlay :line 0)
                                (chart/with-indicator kijun :overlay :line 1)
                                (chart/with-indicator tenkan :overlay :line 2))]
                  (open-order price inputs chart))))

          sell-signal?
          (do (prn "sell signal")
              (when @order
                (close-order price))))))))