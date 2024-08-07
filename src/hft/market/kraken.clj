(ns hft.market.kraken
  (:require [jsonista.core :as j]
            [clj-http.lite.client :as http]))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(def symbol-mapper
  {"BTCUSDT" "XBTUSDT"})

(defn depth! [sym limit]
  (get (:result (jread (:body (http/get (str "https://api.kraken.com/0/public/Depth")
                                        {:accept :json
                                         :query-params {"pair" (get symbol-mapper sym)
                                                        "count" limit}}))))
       (keyword (get symbol-mapper sym))))