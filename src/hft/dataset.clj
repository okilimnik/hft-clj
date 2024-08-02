(ns hft.dataset
  (:require [hft.async :refer [vthread]]
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

(defn get-image-column [min-level max-level price-interval prices]
  (loop [prices prices
         result (vec (repeat INPUT-SIZE 0))]
    (if (seq prices)
      (let [[price-str qty-str] (first prices)
            price (parse-double price-str)
            qty (parse-double qty-str)]
        (if (and (< price max-level) (>= price min-level))
          (let [level (int (/ (* (- price min-level) INPUT-SIZE) price-interval))]
            (recur (rest prices) (update result level #(+ (or % 0) qty))))
          result))
      result)))

(defn get-min-level [mid-level]
  (- mid-level (* PRICE-INTERVAL-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-max-level [mid-level]
  (+ mid-level (* PRICE-INTERVAL-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-level max-bid
        min-level (get-min-level mid-level)
        max-level (get-max-level mid-level)
        price-interval (- max-level min-level)
        b (get-image-column min-level max-level price-interval (:bids order-book))
        g (get-image-column min-level max-level price-interval (:asks order-book))]
    {:b b
     :skewness-b (get-skewness (subvec b 10))
     :kurtosis-b (get-kurtosis (subvec b 10))
     :g g
     :skewness-g (get-skewness (subvec g 0 10))
     :kurtosis-g (get-kurtosis (subvec g 0 10))}))

(defn save-order-books [market inputs ui? on-update]
  (prn "skewness-b: " (mapv :skewness-b inputs))
  (prn "kurtosis-b: " (mapv :kurtosis-b inputs))
  (prn "skewness-g: " (mapv :skewness-g inputs))
  (prn "kurtosis-g: " (mapv :kurtosis-g inputs))
  (let [image (du/->image {:data inputs
                           :max-value (get MAX-QUANTITY market)})
        label ""
        filepath  (du/save-image {:image image
                                  :dir "./dataset"
                                  :filename label
                                  :ui? ui?})]
    (on-update {:src filepath :label label})))

(defn pipeline-v1 [{:keys [on-update ui? market] :or {on-update (fn [_]) market :binance}}]
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     10000
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

(defn prepare! [market]
  (market/init market)
  (pipeline-v1 {:market market}))