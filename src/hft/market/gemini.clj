(ns hft.market.gemini
  (:require [jsonista.core :as j]
            [clj-http.client :as http]))

(defn jread [v]
  (j/read-value v j/keyword-keys-object-mapper))

(def symbol-mapper
  {"BTCUSDT" "btcusdt"})

(defn depth! [symbol]
  (-> (jread (:body (http/get (str "https://api.gemini.com/v1/book/" (get symbol-mapper symbol))
                              {:accept :json
                               :query-params {"limit_bids" 500
                                              "limit_asks" 500}})))
      (update :bids #(mapv (fn [x] [(:price x) (:amount x)]) %))
      (update :asks #(mapv (fn [x] [(:price x) (:amount x)]) %))))

;(prn (depth! "BTCUSDT"))