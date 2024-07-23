(ns hft.trade
  (:require [hft.market.binance :as bi]
            [taoensso.timbre :as log]))

(def TRADE-AMOUNT-BTC 0.0005)
(def opened-order (atom nil))

(defn create-order-params [symbol side]
  {"symbol" symbol
   "side" side ;"BUY" "SELL"
   "type" "MARKET"
   "quantity" TRADE-AMOUNT-BTC})

(defn open-order! [symbol side]
  (log/debug "open-order! triggered")
  (bi/open-order! (create-order-params symbol side))
  (if (nil? @opened-order)
    (reset! opened-order side)
    (reset! opened-order nil)))

(defn prediction->order-side [{:keys [buy? sell? wait? close-buy? close-sell?]}]
  (cond
    (and (nil? @opened-order)
         (not wait?)
         buy?) "BUY"
    (and (nil? @opened-order)
         (not wait?)
         sell?) "SELL"
    (and (= @opened-order "BUY")
         close-buy?) "SELL"
    (and (= @opened-order "SELL")
         close-sell?) "BUY"))

(defn trade! [symbol prediction]
  (prn "prediction: " prediction)
  (when-let [side (prediction->order-side prediction)]
    (open-order! symbol side)))

(defn update-state! []
  (let [btc-amount (parse-double (:free (first (filter #(= (:asset %) "BTC") (:balances (bi/account! {}))))))]
    (cond
      (> btc-amount 0.00075) (reset! opened-order "BUY")
      (< btc-amount 0.00075) (reset! opened-order "SELL"))))
