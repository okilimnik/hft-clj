(ns hft.market
  (:require
   [hft.market.binance :as binance]
   [hft.market.kraken :as kraken]
   [hft.market.coinbase :as coinbase]))

(defn depth! [market sym limit]
  (case market
    :binance (binance/depth! sym limit)
    :kraken (kraken/depth! sym limit)
    :coinbase (coinbase/depth! sym limit)
    nil))

(defn init [& markets]
  (doseq [market markets]
    (case market
      :binance (binance/init)
      nil)))