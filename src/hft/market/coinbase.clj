(ns hft.market.coinbase
  (:require [jsonista.core :as j]
            [clj-http.client :as http]))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(def symbol-mapper 
  {"BTCUSDT" "btc-usdt"})

(defn depth! [symbol]
  (jread (:body (http/get (str "https://api.exchange.coinbase.com/products/" (get symbol-mapper symbol) "/book?level=2") {:accept :json}))))

;(prn (depth! "BTCUSDT"))