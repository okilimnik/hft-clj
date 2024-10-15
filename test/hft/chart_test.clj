(ns hft.chart-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [hft.chart :as sut]
            [hft.dataset :as dataset]) 
  (:import [org.ta4j.core.indicators.ichimoku IchimokuChikouSpanIndicator IchimokuKijunSenIndicator]))

(deftest chart-test
  (let [kline {:o 4
               :h 10
               :l 3
               :c 3
               :v 10
               :n 100
               :t (System/currentTimeMillis)}
        klines (->> (take 50 (repeat kline))
                    (map-indexed #(-> (update %2 :t + (* %1 1000))
                                      (update :o inc)
                                      (update :h inc)
                                      (update :l inc)
                                      (update :c inc)
                                      (update :v inc)))
                    vec)
        series (dataset/klines->series "1m" klines)]
    (-> (sut/->chart "Buy signal" klines)
        (sut/with-indicator (IchimokuKijunSenIndicator. series 50) :overlay :line 0)
        (sut/with-indicator (IchimokuChikouSpanIndicator. series 50) :overlay :line 1)
        (sut/->image "chart.png"))
    (is (= true (.exists (io/file "chart.png"))))
    (.delete (io/file "chart.png"))))