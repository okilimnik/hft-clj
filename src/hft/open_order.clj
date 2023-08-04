(ns hft.open-order
  (:require
   [clojure.string :as str]
   [hft.api :as api]))

;; we want our order not to match any existent order
;; so it would be a `maker` order (less exchange fee + more profit by default)
(def ^:private MAKER-ORDER-SHIFT 1)

(defn- get-maker-price [price side]
  ((case side :buy - +) price MAKER-ORDER-SHIFT))

(defn- get-best-price [side]
  (let [best-prices (api/best-price!)]
    (-> (case side :buy :askPrice :bidPrice)
        best-prices
        parse-double)))

(defn create-params [{:keys [side price]}]
  {:symbol ""
   :side (str/upper-case side)
   :type "LIMIT"
   :timeInForce "GTC"
   :quantity ""
   :price price})

(defn open-order [side]
  (let [maker-price (-> (get-best-price side)
                        (get-maker-price side))
        order-params (create-params {:side side :price maker-price})]))

;(open-order :sell)