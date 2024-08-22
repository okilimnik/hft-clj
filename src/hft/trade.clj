(ns hft.trade
  (:require [hft.market.binance :as bi]))

(def TRADE-AMOUNT-BTC 0.001)
(def opened-order (atom nil))

(defn create-buy-params [symbol]
  {"symbol" symbol
   "side" "BUY"
   "type" "MARKET"
   "timeInForce" "GTC"
   "quantity" TRADE-AMOUNT-BTC})

(defn create-sell-params [symbol]
  {"symbol" symbol
   "side" "SELL"
   "type" "MARKET"
   "timeInForce" "GTC"
   "quantity" TRADE-AMOUNT-BTC})

(defn trade! [symbol analysis]
  (cond
    (and @opened-order (#{:wait :sell} analysis)) (do
                                                    (prn "taking profit")
                                                    (bi/open-order! (create-sell-params symbol))
                                                    (reset! opened-order false))
    (and (not @opened-order) (#{:buy} analysis)) (do
                                                   (prn "buying")
                                                   (bi/open-order! (create-buy-params symbol))
                                                   (reset! opened-order true))))