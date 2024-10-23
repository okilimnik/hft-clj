(ns hft.market.binance
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [hft.async :refer [vthread]]
            [jsonista.core :as j])
  (:import [com.binance.connector.client.impl SpotClientImpl WebSocketStreamClientImpl]
           [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]
           [java.util ArrayList]))

(def trade-client (atom nil))
(def market-client (atom nil))
(def ws-client (atom nil))

(defn init []
  #_(reset! trade-client (.createTrade (SpotClientImpl. (System/getenv "BINANCE_API_KEY")
                                                        (System/getenv "BINANCE_SECRET"))))
  (reset! market-client (.createMarket (SpotClientImpl.))))

(defn subscribe [streams callback]
  (when-not @ws-client
    (reset! ws-client (WebSocketStreamClientImpl.)))
  (.combineStreams @ws-client (ArrayList. streams) callback))

(defn unsubscribe [stream]
  (when-not @ws-client
    (reset! ws-client (WebSocketStreamClientImpl.)))
  (.closeAllConnections @ws-client))

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

(defn mini-ticker! [symbol window]
  (when-not @market-client (init))
  (jread (.ticker @market-client {"symbol" symbol "windowSize" window "type" "FULL"})))

(def order-book (atom nil))

(defn- update-prices [book price-changes]
  (doseq [[price volume] price-changes]
    (if (> (parse-double volume) 1)
      (assoc! book price volume)
      (dissoc! book price))))

(defn- update-order-book [sym data]
  (when (> (:u data) (:lastUpdateId (get @order-book sym)))
    (swap! order-book update sym #(-> %
                                      (assoc :lastUpdateId (:u data))
                                      (update :bids (fn [bids] (update-prices bids (:b data))))
                                      (update :asks (fn [asks] (update-prices asks (:a data))))))))

(defn init-order-book! [symbols]
  (->> symbols
       (mapv #(let [data (depth! % 5000)]
                (vthread (update-order-book % (set/rename-keys data {:asks :a :bids :b :lastUpdateId :u})))))
       (mapv deref)))

(defn order-book-server [symbols]
  (reset! order-book (atom (into {} (map #(vector % {:lastUpdateId 0 :bids (transient {})  :asks (transient {})}) symbols))))
  (let [streams (mapv #(str (str/lower-case %) "@depth") symbols)
        order-book-warming-buffer (atom (into {} (map #(vector % (transient [])) symbols)))
        order-book-warmed-up? (atom false)]
    #_(subscribe streams
               (reify WebSocketMessageCallback
                 ^void (onMessage [_ event-str]
                                  (try
                                    (let [event (jread event-str)
                                          data (:data event)]
                                      (cond
                                        (= (:stream event) depth)
                                        (if @order-book-warmed-up?
                                          (update-order-book order-book data)
                                          (do
                                            (swap! order-book-warming-buffer conj data)
                                            (when (> (count @order-book-warming-buffer) 10)
                                              (init-order-book! order-book)
                                              (doseq [buffered-data @order-book-warming-buffer]
                                                (update-order-book order-book buffered-data))
                                              (reset! order-book-warmed-up? true))))))
                                    (catch Exception e (prn e))))))))