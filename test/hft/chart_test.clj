(ns hft.chart-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [hft.chart :as sut]
   [hft.strategy :as strategy]) 
  (:import
   [org.ta4j.core.indicators.helpers ClosePriceIndicator]
   [org.ta4j.core.indicators.ichimoku
    IchimokuChikouSpanIndicator
    IchimokuKijunSenIndicator
    IchimokuSenkouSpanAIndicator
    IchimokuSenkouSpanBIndicator
    IchimokuTenkanSenIndicator]))

(deftest chart-test
  (let [kline {:o 4
               :h 10
               :l 3
               :c 3
               :v 10
               :n 100
               :t (System/currentTimeMillis)}
        klines (->> (take 100 (repeat kline))
                    (map-indexed #(if (even? (int (/ %1 20)))
                                    (-> (update %2 :t + (* %1 1000))
                                        (update :o + (mod %1 20))
                                        (update :h + (mod %1 20))
                                        (update :l + (mod %1 20))
                                        (update :c + (mod %1 20))
                                        (update :v + (mod %1 20)))
                                    (-> (update %2 :t + (* %1 1000))
                                        (update :o - (mod %1 20))
                                        (update :h - (mod %1 20))
                                        (update :l - (mod %1 20))
                                        (update :c - (mod %1 20))
                                        (update :v - (mod %1 20)))))
                    vec)
        ICHIMOKU-PERIOD 26
        TENKAN-PERIOD 9
        series (strategy/klines->series "1m" klines)
        close-prices (ClosePriceIndicator. series)
        chikou (IchimokuChikouSpanIndicator. series ICHIMOKU-PERIOD)
        tenkan (IchimokuTenkanSenIndicator. series TENKAN-PERIOD)
        kijun (IchimokuKijunSenIndicator. series ICHIMOKU-PERIOD)
        senkou-span-a (IchimokuSenkouSpanAIndicator. series TENKAN-PERIOD ICHIMOKU-PERIOD)
        senkou-span-b (IchimokuSenkouSpanBIndicator. series (* ICHIMOKU-PERIOD 2) ICHIMOKU-PERIOD)
        chart (-> (sut/->chart "Buy signal" klines)
                  (sut/with-indicator kijun :overlay :line 0)
                  (sut/with-indicator chikou :overlay :line 1)
                  (sut/with-indicator tenkan :overlay :line 2))]
    (is (= 22.0 (.doubleValue (.getValue chikou (- (count klines) 1 ICHIMOKU-PERIOD)))))
    (is (= [] (mapv #(.doubleValue (.getValue senkou-span-a %)) (range (count klines)))))
    (is (= [] (mapv #(.doubleValue (.getValue senkou-span-b %)) (range (count klines)))))
    (sut/->image chart "chart.png")
    (is (= true (.exists (io/file "chart.png"))))
    (.delete (io/file "chart.png"))))