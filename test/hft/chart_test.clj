(ns hft.chart-test
  (:require [clojure.test :refer [deftest is]]
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
                    (map-indexed #(update %2 :t + (* %1 1000)))
                    vec)
        series (dataset/klines->series "1m" klines)]
    (is (= :ok (try (-> (sut/->chart "Buy signal" klines)
                        (sut/with-indicator (IchimokuKijunSenIndicator. series 50) :overlay :line)
                        (sut/with-indicator  (IchimokuChikouSpanIndicator. series 50) :overlay :line))
                    :ok
                    (catch Exception e e))))))