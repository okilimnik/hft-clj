(ns hft.binance
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
  (when-not @market-client (init))
  (jread (.newOrder @trade-client params)))

(defn opened-orders! [symbol]
  (when-not @market-client (init))
  (jread (.getOpenOrders @trade-client {"symbol" symbol
                                        "timestamp" (System/currentTimeMillis)})))

(defn cancel-order! [symbol id]
  (when-not @market-client (init))
  (-> (.cancelOrder @trade-client {"symbol" symbol
                                   "orderId" id
                                   "timestamp" (System/currentTimeMillis)})
      jread))

(defn depth! [symbol limit]
  (when-not @market-client (init))
  (jread (.depth @market-client {"symbol" symbol "limit" limit})))

(defn best-price! [symbol]
  (when-not @market-client (init))
  (jread (.bookTicker @market-client {"symbol" symbol})))