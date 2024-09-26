(ns hft.core
  (:gen-class)
  (:require [hft.dataset :refer [range-market-pipeline trend-market-pipeline]]))

(defn -main [& _args]
  (let [market-state (or (System/getenv "MARKET_STATE") "range")]
    (println "market state is: " market-state)
    (case (keyword market-state)
      :range (range-market-pipeline)
      :trend (trend-market-pipeline)
      (prn (str "Handling " market-state " market is not implemented")))))