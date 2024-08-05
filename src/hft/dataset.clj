(ns hft.dataset
  (:require [clojure.pprint :refer [pprint]]
            [hft.data :as du]
            [hft.market :as market]
            [hft.scheduler :as scheduler])
  (:import [org.apache.commons.math3.stat.descriptive DescriptiveStatistics]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def MAX-QUANTITY {:binance 100 :kraken 20})
(def PRICE-INTERVAL-FOR-INDEXING 100)

(def keep-running? (atom true))


(defn get-skewness [x]
  (let [stats (DescriptiveStatistics. (into-array Double/TYPE x))]
    (.getSkewness stats)))

(defn get-kurtosis [x]
  (let [stats (DescriptiveStatistics. (into-array Double/TYPE x))]
    (.getKurtosis stats)))

(defn get-image-column [min-price max-price price-interval prices]
  (loop [prices prices
         result (vec (repeat INPUT-SIZE 0))]
    (if (seq prices)
      (let [[price-str qty-str] (first prices)
            price (parse-double price-str)
            qty (parse-double qty-str)]
        (if (and (< price max-price) (>= price min-price))
          (let [level (int (/ (* (- price min-price) INPUT-SIZE) price-interval))]
            (recur (rest prices) (update result level #(+ (or % 0) qty))))
          result))
      result)))

(defn get-min-price [mid-price]
  (- mid-price (* PRICE-INTERVAL-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-max-price [mid-price]
  (+ mid-price (* PRICE-INTERVAL-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))]
    {:bids bids
     :skewness-bids (get-skewness (subvec bids 0 10))
     :kurtosis-bids (get-kurtosis (subvec bids 0 10))
     :asks asks
     :skewness-asks (get-skewness (subvec asks 10))
     :kurtosis-asks (get-kurtosis (subvec asks 10))}))

(defn save-order-books [market inputs ui? on-update]
  (let [image (du/->image {:data inputs
                           :max-value (get MAX-QUANTITY market)})
        label ""
        filepath  (du/save-image {:image image
                                  :metadata (with-out-str
                                              (pprint
                                               {:skewness-asks (mapv :skewness-asks inputs)
                                                :skewness-bids (mapv :skewness-bids inputs)
                                                :kurtosis-bids (mapv :kurtosis-bids inputs)
                                                :kurtosis-asks (mapv :kurtosis-asks inputs)}))
                                  :dir "./dataset"
                                  :filename label
                                  :ui? ui?})]
    (on-update {:src filepath :label label})))

(defn pipeline-v1 [{:keys [on-update ui? market] :or {on-update (fn [_]) market :binance}}]
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     3000
     (fn []
       (let [order-book (market/depth! market SYMBOL 5000)]
         (swap! max-bids #(as-> % $
                            (conj $ (parse-double (ffirst (:bids order-book))))
                            (if (> (count $) INPUT-SIZE)
                              (pop $)
                              $)))
         (swap! inputs #(as-> % $
                          (conj $ (order-book->quantities-indexed-by-price-level order-book (apply max @max-bids)))
                          (if (> (count $) INPUT-SIZE)
                            (pop $)
                            $)))
         (when (= (count @inputs) INPUT-SIZE)
           (save-order-books market @inputs ui? on-update))))
     keep-running?)))

(defn prepare! [& markets]
  (doseq [market markets]
    (market/init market)
    (pipeline-v1 {:market market})))