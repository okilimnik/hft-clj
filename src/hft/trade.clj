(ns hft.trade
  (:require [hft.market.binance :as bi]
            [taoensso.timbre :as log]))

(def TRADE-AMOUNT-BTC 0.0005)
(def trading? (atom false))

(defn create-order-params [symbol side]
  {"symbol" symbol
   "side" side ;"BUY" "SELL"
   "type" "MARKET"
   "quantity" TRADE-AMOUNT-BTC})

(defn open-order! [symbol side]
  (log/debug "open-order! triggered")
  (bi/open-order! (create-order-params symbol side)))

(defn prediction->order-side [prediction]
  nil)

(defn trade! [symbol prediction]
  (when-let [side (prediction->order-side prediction)]
    (open-order! symbol side)))
