(ns hft.dataset
  (:require
    [clojure.string :as str]
    [clojure.core.async :refer [<!! <! thread timeout]]
    [clojurewerkz.quartzite.jobs :refer [defjob]]
    [hft.firebase :refer [upload-file!]]
    [hft.api :as api]
    [hft.scheduler :refer [make-job]]))

(def ORDER-LIFE "1m")

(defn enum-duration->ms [v]
  (case (keyword v)
    :1m 60000))

(defn enum-duration->cron-expr [v]
  (case (keyword v)
    :1m "0 * * ? * *"))

(defn ->libsvm [best-prices order-book]
  (->> (concat
         [[(:bidPrice best-prices) (:bidQty best-prices)]
          [(:askPrice best-prices) (:askQty best-prices)]]
         (:bids order-book)
         (:asks order-book))
       flatten
       (map-indexed #(str (inc %1) ":" %2))
       (str/join " ")))

(defn wait-for-label! [enum-duration]
  (<!! (timeout (enum-duration->ms enum-duration)))
  (:priceChangePercent (<!! (api/ticker! {"windowSize" enum-duration}))))

(defjob FetchOrderBook [_]
        (thread
          (let [filename "order-book.txt"
                best-prices (api/best-price!)
                order-book (api/depth!)
                row (->libsvm (<!! best-prices) (<!! order-book))
                label (wait-for-label! ORDER-LIFE)
                labeled-row (str label " " row)]
            (spit filename (str labeled-row "\n") :append true)
            (upload-file! filename filename))))

(defn -main [& args]
  (let [job (make-job FetchOrderBook (enum-duration->cron-expr ORDER-LIFE))]))