(ns hft.trade
  (:require [environ.core :refer [env]]
            [hft.market.binance :as bi]
            [taoensso.timbre :as log]
            [clojure.test :as t]
            [clojure.string :as str]))

(def TRADE-AMOUNT-BTC 0.0005)
(def opened-order (atom (keyword (or (env :opened-order) "none"))))

(defn create-order-params [symbol side]
  {"symbol" symbol
   "side" (str/upper-case (name side)) ;"BUY" "SELL"
   "type" "MARKET"
   "quantity" TRADE-AMOUNT-BTC})

(defn open-order! [symbol side]
  (log/debug "open-order! triggered")
  (bi/open-order! (create-order-params symbol side))
  (if (= @opened-order :none)
    (reset! opened-order side)
    (reset! opened-order :none)))

(defn prediction->order-side [prediction]
  (cond
    (and (= @opened-order :none)
         (not= prediction :none)) prediction
    (and (= @opened-order :buy)
         (#{:none :sell} prediction)) :sell
    (and (= @opened-order :sell)
         (#{:none :buy} prediction)) :buy
    :else nil))

(defn trade! [symbol prediction]
  (when-let [side (prediction->order-side prediction)]
    (open-order! symbol side)))
