(ns hft.market.binance
  (:require [jsonista.core :as j]
            [clojure.edn :as edn]
            [clj-http.lite.client :as http]))

(def trade-client (atom nil))
(def market-client (atom nil))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(defn open-order! [params]
  (jread (.newOrder @trade-client (java.util.HashMap. params))))

(defn get-order! [symbol order-id]
  (jread (.getOrder @trade-client (java.util.HashMap. {"symbol" symbol
                                                       "orderId" order-id
                                                       "timestamp" (System/currentTimeMillis)}))))

(defn opened-orders! [symbol]
  (jread (.getOpenOrders @trade-client (java.util.HashMap. {"symbol" symbol
                                                            "timestamp" (System/currentTimeMillis)}))))

(defn cancel-order! [symbol id]
  (-> (.cancelOrder @trade-client (java.util.HashMap. {"symbol" symbol
                                                       "orderId" id
                                                       "timestamp" (System/currentTimeMillis)}))
      jread))

(defn depth! [symbol limit]
  (jread (:body (http/get (str symbol limit) {:accept :json}))))
