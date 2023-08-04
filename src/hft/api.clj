(ns hft.api
  (:require
   [cheshire.core :refer [parse-string]]
   [clojure.core.async :refer [go]]
   [clojure.edn :as edn])
  (:import
   [com.binance.connector.client.impl SpotClientImpl]))

(def SYMBOL "BTCUSDT")
(def trade-client (atom nil))
(def market-client (atom nil))

#_(defn open-order! [params]
    (-> (.newOrder trade-client params)
        (parse-string true)))

(defn init []
  (let [config (:prod (edn/read-string (slurp "binance.config.edn")))]
    (reset! trade-client (.createTrade (SpotClientImpl. (:apiKey config) (:secret config) (:url config))))
    (reset! market-client (.createMarket (SpotClientImpl. (:url config))))))

(defn depth! []
  (go
    (-> (.depth @market-client {"symbol" SYMBOL "limit" 5000})
        (parse-string true))))

(defn trades! []
  (go
    (-> (.trades @market-client {"symbol" SYMBOL})
        (parse-string true))))

(defn best-price! []
  (go
    (-> (.bookTicker @market-client {"symbol" SYMBOL})
        (parse-string true))))

(defn ticker! [& [params]]
  (go
    (-> (.ticker @market-client (merge {"symbol" SYMBOL} params))
        (parse-string true))))