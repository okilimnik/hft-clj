(ns hft.strategy
  (:require
   [clojure.string :as str]
   [hft.chart :as chart]
   [hft.image :as i])
  (:import
   [java.time Instant ZoneId ZonedDateTime]
   [org.ta4j.core BarSeries BaseBarSeries]
   [org.ta4j.core.indicators.helpers ClosePriceIndicator HighPriceIndicator LowPriceIndicator]
   [org.ta4j.core.indicators.ichimoku
    IchimokuChikouSpanIndicator
    IchimokuKijunSenIndicator
    IchimokuSenkouSpanAIndicator
    IchimokuSenkouSpanBIndicator
    IchimokuTenkanSenIndicator]
   [org.ta4j.core.rules IsRisingRule]))

(def order (atom nil))
(def SYMBOL (or (System/getenv "SYMBOL") "BTCUSDT"))
(def MAX-QUANTITY {"BTCUSDT" 100
                   "BNBUSDT" 100})
(def STOP-PROFIT-PRICE-PERCENT 0.0005)
(def STOP-LOSS-PRICE-PERCENT 0.003)
(def TREND-STRENGTH-THRESHOLD 50)
(def ICHIMOKU-PERIOD 30)
(def TENKAN-PERIOD 9)
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
  (let [price (:c (last klines))]
    (when (and @order
               (>= (- (:price @order) price)
                   (* price STOP-LOSS-PRICE-PERCENT)))
      ;; closing with loss
      (close-order price))
    (when (>= (count klines) KLINES-SERIES-LENGTH) ;; warmed-up
      (let [series (klines->series "1m" klines)
            chikou (IchimokuChikouSpanIndicator. series ICHIMOKU-PERIOD)
            kijun (IchimokuKijunSenIndicator. series ICHIMOKU-PERIOD)
            tenkan (IchimokuTenkanSenIndicator. series TENKAN-PERIOD)
            senkou-span-a (IchimokuSenkouSpanAIndicator. series TENKAN-PERIOD ICHIMOKU-PERIOD)
            senkou-span-b (IchimokuSenkouSpanBIndicator. series (* ICHIMOKU-PERIOD 2) ICHIMOKU-PERIOD)
            close-prices (ClosePriceIndicator. series)
            low-prices (LowPriceIndicator. series)
            high-prices (HighPriceIndicator. series)

            buy-signal? (and
                         (> price (.doubleValue (.getValue senkou-span-a (- KLINES-SERIES-LENGTH 1))))
                         (> price (.doubleValue (.getValue senkou-span-b (- KLINES-SERIES-LENGTH 1))))
                         ;; and it's trendy a bit
                         (.isSatisfied (IsRisingRule. tenkan 2) (- KLINES-SERIES-LENGTH 1) nil)
                         ;; bullishly
                         (< (.doubleValue (.getValue tenkan (- KLINES-SERIES-LENGTH 1)))
                            (.doubleValue (.getValue low-prices (- KLINES-SERIES-LENGTH 1))))
                         (or
                          ;; tenkan just has crossed the kijun up
                          (and (> (.doubleValue (.getValue tenkan (- KLINES-SERIES-LENGTH 1)))
                                  (.doubleValue (.getValue kijun (- KLINES-SERIES-LENGTH 1))))
                               (< (.doubleValue (.getValue tenkan (- KLINES-SERIES-LENGTH 2)))
                                  (.doubleValue (.getValue kijun (- KLINES-SERIES-LENGTH 2)))))
                         ;; chikou just has crossed the tenkan up
                          (and (> (.doubleValue (.getValue chikou (- KLINES-SERIES-LENGTH 1 ICHIMOKU-PERIOD)))
                                  (.doubleValue (.getValue tenkan (- KLINES-SERIES-LENGTH 1 ICHIMOKU-PERIOD))))
                               (< (.doubleValue (.getValue chikou (- KLINES-SERIES-LENGTH 2 ICHIMOKU-PERIOD)))
                                  (.doubleValue (.getValue tenkan (- KLINES-SERIES-LENGTH 2 ICHIMOKU-PERIOD)))))
                         ;; chikou just has crossed the high prices up
                          (and (> (.doubleValue (.getValue chikou (- KLINES-SERIES-LENGTH 1 ICHIMOKU-PERIOD)))
                                  (.doubleValue (.getValue high-prices (- KLINES-SERIES-LENGTH 1 ICHIMOKU-PERIOD))))
                               (< (.doubleValue (.getValue chikou (- KLINES-SERIES-LENGTH 2 ICHIMOKU-PERIOD)))
                                  (.doubleValue (.getValue high-prices (- KLINES-SERIES-LENGTH 2 ICHIMOKU-PERIOD)))))))

            sell-signal? (or
                          ;; kijun just has crossed the prices up
                          (and (> (.doubleValue (.getValue kijun (- KLINES-SERIES-LENGTH 1)))
                                  (.doubleValue (.getValue high-prices (- KLINES-SERIES-LENGTH 1))))
                               (< (.doubleValue (.getValue kijun (- KLINES-SERIES-LENGTH 2)))
                                  (.doubleValue (.getValue high-prices (- KLINES-SERIES-LENGTH 2)))))
                          ;; chikou just has crossed the prices down
                          (and (< (.doubleValue (.getValue chikou (- KLINES-SERIES-LENGTH 1 ICHIMOKU-PERIOD)))
                                  (.doubleValue (.getValue close-prices (- KLINES-SERIES-LENGTH 1 ICHIMOKU-PERIOD))))
                               (> (.doubleValue (.getValue chikou (- KLINES-SERIES-LENGTH 2 ICHIMOKU-PERIOD)))
                                  (.doubleValue (.getValue close-prices (- KLINES-SERIES-LENGTH 2 ICHIMOKU-PERIOD))))))]

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