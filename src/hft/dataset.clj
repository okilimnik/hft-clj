(ns hft.dataset
  (:require [clojure.core.async :refer [thread]]
            [clojure.pprint :refer [pprint]]
            [hft.data :as du]
            [hft.market :as market]
            [hft.scheduler :as scheduler]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def MAX-QUANTITY {:binance 100 :kraken 20})
(def MIN-QUANTITY {:binance 10 :kraken 5})
(def PRICE-INTERVAL-FOR-INDEXING 100)

(def keep-running? (atom true))

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

(defn get-levels-with-max-qty-sorted [prices]
  (->> (map-indexed vector prices)
       (sort-by second >)
       (take 3)
       vec))

(defn get-distance-from-terminator [levels terminator-level]
  (abs (- (ffirst levels) terminator-level)))

(defn get-terminator-level-and-qty [market prices bids?]
  (let [f (if bids? dec inc)]
    (loop [i (if bids? (dec INPUT-SIZE) 0)]
      (let [qty (nth prices i)]
        (if (> qty (get MIN-QUANTITY market))
          [i qty]
          (recur (f i)))))))

(defn qty-change-ratio [levels terminator-qty]
  (float (/ (second (first levels))
            terminator-qty)))

(defn order-book->quantities-indexed-by-price-level [market order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))
        bid-levels-with-max-qty-sorted (get-levels-with-max-qty-sorted bids)
        ask-levels-with-max-qty (get-levels-with-max-qty-sorted asks)
        [asks-terminator-level asks-terminator-qty] (get-terminator-level-and-qty market asks false)
        [bids-terminator-level bids-terminator-qty] (get-terminator-level-and-qty market bids true)]
    {:bids bids
     :bid-levels-of-max-qty bid-levels-with-max-qty-sorted
     :max-bid-distance (get-distance-from-terminator bid-levels-with-max-qty-sorted bids-terminator-level)
     :bid-qty-change-ratio (qty-change-ratio bid-levels-with-max-qty-sorted bids-terminator-qty)
     :asks asks
     :ask-levels-of-max-qty ask-levels-with-max-qty
     :max-ask-distance (get-distance-from-terminator ask-levels-with-max-qty asks-terminator-level)
     :ask-qty-change-ratio (qty-change-ratio ask-levels-with-max-qty asks-terminator-qty)}))

(defn print-analysis! [inputs]
  (let [{:keys [max-bid-distance max-ask-distance]} (last inputs)]
    (when (or (> max-bid-distance 2) (> max-ask-distance 2))
      (pprint
       {:ask-levels-of-max-qty (mapv :ask-levels-of-max-qty inputs)
        :max-ask-distance (mapv :max-ask-distance inputs)
        :ask-qty-change-ratio (mapv :ask-qty-change-ratio inputs)
        :bid-levels-of-max-qty (mapv :bid-levels-of-max-qty inputs)
        :max-bid-distance (mapv :max-bid-distance inputs)
        :bid-qty-change-ratio (mapv :bid-qty-change-ratio inputs)}))))

(defn pipeline-v1 [{:keys [market] :or {market :binance}}]
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     6000
     (fn []
       (let [order-book (market/depth! market SYMBOL 5000)]
         (swap! max-bids #(as-> % $
                            (conj $ (parse-double (ffirst (:bids order-book))))
                            (if (> (count $) INPUT-SIZE)
                              (pop $)
                              $)))
         (swap! inputs #(as-> % $
                          (conj $ (order-book->quantities-indexed-by-price-level market order-book (apply max @max-bids)))
                          (if (> (count $) INPUT-SIZE)
                            (pop $)
                            $)))
         (when (= (count @inputs) INPUT-SIZE)
           (print-analysis! @inputs))))
     keep-running?)))

(defn prepare! [& markets]
  #_(doseq [market (rest markets)]
      (thread (pipeline-v1 {:market market})))
  (pipeline-v1 {:market (first markets)}))