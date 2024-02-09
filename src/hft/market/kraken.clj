(ns hft.market.kraken
  (:require [jsonista.core :as j]
            [clj-http.client :as http]))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(def symbol-mapper
  {"BTCUSDT" "XBTUSDT"})

(defn depth! [symbol]
  (get (:result (jread (:body (http/get (str "https://api.kraken.com/0/public/Depth")
                                        {:accept :json
                                         :query-params {"pair" (get symbol-mapper symbol)
                                                        "count" 500}}))))
       (keyword (get symbol-mapper symbol))))

;(prn (depth! "BTCUSDT"))