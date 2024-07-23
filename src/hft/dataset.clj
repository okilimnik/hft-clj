(ns hft.dataset
  (:require [hft.data :as du]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]
            [hft.trade :refer [trade!]]))

(def SYMBOL "BTCUSDT")
(def DATA-FETCH-INTERVAL 3000)
(def INPUT-SIZE 20)
(def MAX-QUANTITY 50)
(def PRICE-INTERVAL-FOR-INDEXING 25)
(def THRESHOLD 0.09)
(def CLOSE-THRESHOLD 0.06)

(defn get-prediction [data]
  (let [sum-b (apply + (map #(apply + %) (map :b data)))
        sum-g (apply + (map #(apply + %) (map :g data)))
        diff (- 1 (if (> sum-b sum-g)
                    (/ sum-g sum-b)
                    (/ sum-b sum-g)))
        sum-b-6-20 (apply + (map #(apply + (subvec % 6 20)) (map :b data)))
        sum-g-0-14 (apply + (map #(apply + (subvec % 0 14)) (map :g data)))
        diff-6-20-0-14 (- 1 (if (> sum-b-6-20 sum-g-0-14)
                              (/ sum-g-0-14 sum-b-6-20)
                              (/ sum-b-6-20 sum-g-0-14)))
        sum-b-8-20 (apply + (map #(apply + (subvec % 8 20)) (map :b data)))
        sum-g-0-12 (apply + (map #(apply + (subvec % 0 12)) (map :g data)))
        diff-8-20-0-12 (- 1 (if (> sum-b-8-20 sum-g-0-12)
                              (/ sum-g-0-12 sum-b-8-20)
                              (/ sum-b-8-20 sum-g-0-12)))
        sum-b-last (apply + (last (map :b data)))
        sum-g-last (apply + (last (map :g data)))
        diff-last (- 1 (if (> sum-b-last sum-g-last)
                         (/ sum-g-last sum-b-last)
                         (/ sum-b-last sum-g-last)))
        sum-b-last-6-20 (apply + (subvec (last (map :b data)) 6 20))
        sum-g-last-0-14 (apply + (subvec (last (map :g data)) 0 14))
        diff-last-6-20-0-14 (- 1 (if (> sum-b-last-6-20 sum-g-last-0-14)
                                   (/ sum-g-last-0-14 sum-b-last-6-20)
                                   (/ sum-b-last-6-20 sum-g-last-0-14)))
        sum-b-last-8-20 (apply + (subvec (last (map :b data)) 8 20))
        sum-g-last-0-12 (apply + (subvec (last (map :g data)) 0 12))
        diff-last-8-20-0-12 (- 1 (if (> sum-b-last-8-20 sum-g-last-0-12)
                                   (/ sum-g-last-0-12 sum-b-last-8-20)
                                   (/ sum-b-last-8-20 sum-g-last-0-12)))]
    (prn {:diff diff ;; 0.03 0.01 / 0.02 0.15 0.07
          :diff-6-20-0-14 diff-6-20-0-14 ;; 0.3 0.15 / 0.19 0.31 0.17
          :diff-8-20-0-12 diff-8-20-0-12 ;; 0.43 0.25 / 0.42 0.43 0.30
          :diff-last diff-last ;; 0.05 0.07 / 0.12 0.12 0.05
          :diff-last-6-20-0-14 diff-last-6-20-0-14 ;; 0.23 0.05 / 0.29 0.28 0.17
          :diff-last-8-20-0-12 diff-last-8-20-0-12 ;; 0.36 0.14 / 0.57 0.45 0.35
          })

    (cond-> {:sell? false :buy? false :wait? false :close-sell? false :close-buy? false}
      (> sum-b-last sum-g-last) (assoc :close-buy? true)
      (< sum-b-last sum-g-last) (assoc :close-sell? true)
      (and
       (> sum-b-last sum-g-last)
       (<= diff-last CLOSE-THRESHOLD)) (assoc :close-sell? true)
      (and
       (< sum-b-last sum-g-last)
       (<= diff-last CLOSE-THRESHOLD)) (assoc :close-buy? true)
      (< diff-last THRESHOLD) (assoc :wait? true)
      (and
       (< sum-b-last sum-g-last)
       (> diff-last THRESHOLD)) (assoc :buy? true)
      (and
       (> sum-b-last sum-g-last)
       (> diff-last THRESHOLD)) (assoc :sell? true))))

(defn get-price-level [price interval]
  (-> (/ price interval)
      int
      (* interval)))

(defn get-price-level-index [level min-level price-interval]
  (let [index (/ (- level min-level) price-interval)]
    index))

(defn order-book->quantities-indexed-by-price-level [price-interval order-book]
  (let [max-bid (parse-double (ffirst (:bids order-book)))
        level-10 (get-price-level max-bid price-interval)
        min-level (- level-10 (* price-interval (dec (/ INPUT-SIZE 2))))
        max-level (+ level-10 (* price-interval (dec (/ INPUT-SIZE 2))) price-interval)]
    {:b (let [result (loop [s (:bids order-book)
                            result (vec (repeat INPUT-SIZE 0))]
                       (if (seq s)
                         (let [[price-str qty-str] (first s)
                               price (parse-double price-str)
                               qty (parse-double qty-str)]
                           (if (>= price min-level)
                             (let [level (get-price-level price price-interval)]
                               (recur (rest s) (if (< price max-level)
                                                 (update result (get-price-level-index level min-level price-interval) #(+ (or % 0) qty))
                                                 result)))
                             result))
                         result))]
          result)
     :g (let [result (loop [s (:asks order-book)
                            result (vec (repeat INPUT-SIZE 0))]
                       (if (seq s)
                         (let [[price-str qty-str] (first s)
                               price (parse-double price-str)
                               qty (parse-double qty-str)]
                           (if (< price max-level)
                             (let [level (get-price-level price price-interval)]
                               (recur (rest s) (if (>= price min-level)
                                                 (update result (get-price-level-index level min-level price-interval) #(+ (or % 0) qty))
                                                 result)))
                             result))
                         result))]
          result)}))

(def keep-running? (atom true))

(defn pipeline-v2 [{:keys [on-update ui?] :or {on-update (fn [_])}}]
  (let [input (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     DATA-FETCH-INTERVAL
     (fn []
       (let [order-book (bi/depth! SYMBOL 5000)]
         (swap! input #(as-> % $
                         (conj $ (order-book->quantities-indexed-by-price-level PRICE-INTERVAL-FOR-INDEXING order-book))
                         (if (> (count $) INPUT-SIZE)
                           (pop $)
                           $)))
         (when (= (count @input) INPUT-SIZE)
           (let [{:keys [buy? sell?] :as prediction} (get-prediction (take INPUT-SIZE @input))
                 label (cond
                         buy? "buy"
                         sell? "sell"
                         :else "wait")]
             (if ui?
               (let [image (du/->image {:data (take INPUT-SIZE @input)
                                        :max-value MAX-QUANTITY})
                     filepath (du/save-image {:image image
                                              :dir "./dataset"
                                              :filename label
                                              :ui? ui?})]
                 (on-update {:src filepath :label label}))
               (on-update prediction))))))
     keep-running?)))

(defn start! []
  (pipeline-v2 {:on-update (partial trade! SYMBOL)}))