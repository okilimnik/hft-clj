(ns hft.market.binance
  (:require [jsonista.core :as j]
            [clojure.edn :as edn])
  (:import [com.binance.connector.client.impl SpotClientImpl]))

(def trade-client (atom nil))
(def market-client (atom nil))

(defn init []
  (let [config (:prod (edn/read-string (slurp "binance.config.edn")))]
    (reset! trade-client (.createTrade (SpotClientImpl. (:apiKey config) (:secret config) (:url config))))
    (reset! market-client (.createMarket (SpotClientImpl. (:url config))))))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(defn open-order! [params]
  (jread (.newOrder @trade-client (java.util.HashMap. params))))

(defn opened-orders! [symbol]
  (jread (.getOpenOrders @trade-client (java.util.HashMap. {"symbol" symbol
                                                            "timestamp" (System/currentTimeMillis)}))))

(defn cancel-order! [symbol id]
  (-> (.cancelOrder @trade-client (java.util.HashMap. {"symbol" symbol
                                                       "orderId" id
                                                       "timestamp" (System/currentTimeMillis)}))
      jread))

(defn depth! [symbol limit]
  (jread (.depth @market-client (java.util.HashMap. {"symbol" symbol "limit" limit}))))

(defn best-price! [symbol]
  (jread (.bookTicker @market-client (java.util.HashMap. {"symbol" symbol}))))