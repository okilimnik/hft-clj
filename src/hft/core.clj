(ns hft.core
  (:gen-class)
  (:require [hft.dataset :refer [range-market-pipeline]]))

(defn -main [& _args]
  (range-market-pipeline))