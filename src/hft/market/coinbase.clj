(ns hft.market.coinbase
  (:require [jsonista.core :as j]
            [clj-http.lite.client :as http]))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(def symbol-mapper 
  {"BTCUSDT" "btc-usdt"})

(defn depth! [sym _limit]
  (jread (:body (http/get (str "https://api.exchange.coinbase.com/products/" (get symbol-mapper sym) "/book?level=3") {:accept :json}))))