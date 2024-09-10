(ns hft.indi
  (:import [org.ta4j.core.indicators ParabolicSarIndicator])
  (:require [hft.market.binance :as bi]
            [clojure.string :as str]))

(defn psar
  "sym - symbol, like BTCUSDT
   interval - interval keyword, like :1m"
  [sym interval]
  
  #_(let [indi (ParabolicSarIndicator. series)]))