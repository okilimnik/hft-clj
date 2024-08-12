(ns hft.market.binance
  (:require [jsonista.core :as j]
            [clj-http.lite.client :as http]))

(def url "https://api4.binance.com")

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(defn depth! [symbol limit]
  (prn "requesting binance data")
  (jread (:body (http/get (str url "/api/v3/depth") {:accept :json
                                                     :query-params {"symbol" symbol
                                                                    "limit" limit}}))))
