(ns hft.api
  (:require [jsonista.core :as j]
            [clojure.core.async :refer [go]]
            [clojure.edn :as edn])
  (:import [com.binance.connector.client.impl SpotClientImpl WebSocketStreamClientImpl]
           (com.binance.connector.client.impl.spot Trade)))

(def trade-client (atom nil))
(def market-client (atom nil))
(def ws-client (atom nil))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(defn open-order! [params]
  (-> (.newOrder @trade-client params)
      jread))

(defn opened-orders! [symbol]
  (-> (.getOpenOrders @trade-client {"symbol" symbol
                                     "timestamp" (System/currentTimeMillis)})
      jread))

(defn cancel-order! [symbol id]
  (-> (.cancelOrder @trade-client {"symbol" symbol
                                   "orderId" id
                                   "timestamp" (System/currentTimeMillis)})
      jread))

(defn init []
  (let [config (:prod (edn/read-string (slurp "binance.config.edn")))]
    (reset! trade-client (.createTrade (SpotClientImpl. (:apiKey config) (:secret config) (:url config))))
    (reset! market-client (.createMarket (SpotClientImpl. (:url config))))
    (reset! ws-client (WebSocketStreamClientImpl.))))

(defn depth! [symbol]
  (-> (.depth @market-client {"symbol" symbol "limit" 5000})
      jread))

(defn best-price! [symbol]
  (go
    (-> (.bookTicker @market-client {"symbol" symbol})
        jread)))