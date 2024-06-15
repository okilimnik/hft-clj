(ns hft.core
  (:gen-class)
  (:require [hft.dataset :as dataset]
            [hft.market.binance :as bi]
            [hft.trade :as trade]))

(defn -main [& args]
  (bi/init)
  (trade/update-state!)
  (dataset/start!))