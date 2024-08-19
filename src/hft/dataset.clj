(ns hft.dataset
  (:require [clojure.core.async :refer [thread]]
            [clojure.pprint :refer [pprint]]
            [hft.data :as du]
            [hft.market :as market]
            [hft.scheduler :as scheduler]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def MAX-QUANTITY {:binance 100 :kraken 20})
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

(defn get-distance-from-terminator [levels terminator]
  (abs (- (ffirst levels) terminator)))

(defn qty-change-ratio [levels prices terminator]
  (float (/ (second (first levels))
            (let [result (nth prices terminator)]
              (if (> result 0)
                result
                (if (= 9 terminator)
                  (let [result (nth prices 10)]
                    (if (> result 0)
                      result
                      (let [result (nth prices 8)]
                        result)))
                  (let [result (nth prices 9)]
                    (if (> result 0)
                      result
                      (let [result (nth prices 11)]
                        result)))))))))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))
        bid-levels-with-max-qty-sorted (get-levels-with-max-qty-sorted bids)
        ask-levels-with-max-qty (get-levels-with-max-qty-sorted asks)]
    {:bids bids
     :bid-levels-of-max-qty bid-levels-with-max-qty-sorted
     :max-bid-distance (get-distance-from-terminator bid-levels-with-max-qty-sorted 9)
     :bid-qty-change-ratio (qty-change-ratio bid-levels-with-max-qty-sorted bids 9)
     :asks asks
     :ask-levels-of-max-qty ask-levels-with-max-qty
     :max-ask-distance (get-distance-from-terminator ask-levels-with-max-qty 10)
     :ask-qty-change-ratio (qty-change-ratio ask-levels-with-max-qty asks 10)}))

(defn save-order-books [market inputs ui? on-update]
  (let [image (with-out-str (pprint inputs)) #_(du/->image {:data inputs
                                                            :max-value (get MAX-QUANTITY market)})
        label ""
        filepath  (when (or ui? (let [{:keys [max-bid-distance max-ask-distance bid-qty-change-ratio ask-qty-change-ratio]} (last inputs)
                                      bid-signal? (and (> max-bid-distance 2)
                                                       (> bid-qty-change-ratio 2))
                                      ask-signal? (and (> max-ask-distance 2)
                                                       (> ask-qty-change-ratio 2))]
                                  (when (or (> max-bid-distance 2) (> max-ask-distance 2))
                                    (prn "bid-qty-change-ratio: " bid-qty-change-ratio)
                                    (prn "ask-qty-change-ratio: " ask-qty-change-ratio))
                                  (if (and bid-signal? ask-signal?)
                                    (> (abs (- max-bid-distance max-ask-distance)) 2)
                                    (or bid-signal? ask-signal?))))
                    (du/save-image {:image image
                                    :metadata (with-out-str
                                                (pprint
                                                 {:ask-levels-of-max-qty (mapv :ask-levels-of-max-qty inputs)
                                                  :max-ask-distance (mapv :max-ask-distance inputs)
                                                  :ask-qty-change-ratio (mapv :ask-qty-change-ratio inputs)
                                                  :bid-levels-of-max-qty (mapv :bid-levels-of-max-qty inputs)
                                                  :max-bid-distance (mapv :max-bid-distance inputs)
                                                  :bid-qty-change-ratio (mapv :bid-qty-change-ratio inputs)}))
                                    :dataset-dir "./dataset"
                                    :folder (name market)
                                    :filename label
                                    :ui? ui?}))]
    (on-update {:src filepath :label label})))

(defn pipeline-v1 [{:keys [on-update ui? market] :or {on-update (fn [_]) market :binance}}]
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
                          (conj $ (order-book->quantities-indexed-by-price-level order-book (apply max @max-bids)))
                          (if (> (count $) INPUT-SIZE)
                            (pop $)
                            $)))
         (when (= (count @inputs) INPUT-SIZE)
           (save-order-books market @inputs ui? on-update))))
     keep-running?)))

(defn prepare! [& markets]
  #_(doseq [market (rest markets)]
      (thread (pipeline-v1 {:market market})))
  (pipeline-v1 {:market (first markets)}))