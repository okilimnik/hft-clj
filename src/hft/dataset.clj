(ns hft.dataset
  (:require [environ.core :refer [env]]
            [hft.data :as du]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]
            [hft.trade :refer [trade!]]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def LABEL-QUEUE-SIZE 6)
(def MAX-QUANTITY 50)
(def PRICE-INTERVAL-FOR-INDEXING 25)
(def THRESHOLD  (or (env :threshold) 200))
(def CLOSE-THRESHOLD  (or (env :close-threshold) 50))

(defn get-prediction [data]
  (let [sum-b (apply + (map #(apply + %) (map :b data)))
        sum-g (apply + (map #(apply + %) (map :g data)))
        diff (abs (- sum-b sum-g))]
    (prn "sum-b: " sum-b)
    (prn "sum-g: " sum-g)
    (prn "diff: " diff)
    (cond-> {:sell? false :buy? false :wait? false :close-sell? false :close-buy? false}
      (> sum-b sum-g) (assoc :close-buy? true)
      (< sum-b sum-g) (assoc :close-sell? true)
      (and
       (> sum-b sum-g)
       (<= diff CLOSE-THRESHOLD)) (assoc :close-sell? true)
      (and
       (< sum-b sum-g)
       (<= diff CLOSE-THRESHOLD)) (assoc :close-buy? true)
      (< diff THRESHOLD) (assoc :wait? true)
      (and
       (< sum-b sum-g)
       (> diff THRESHOLD)) (assoc :buy? true)
      (and
       (> sum-b sum-g)
       (> diff THRESHOLD)) (assoc :sell? true))))

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
     3000
     (fn []
       (let [order-book (bi/depth! SYMBOL 5000)]
         (swap! input #(as-> % $
                         (conj $ (order-book->quantities-indexed-by-price-level PRICE-INTERVAL-FOR-INDEXING order-book))
                         (if (> (count $) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
                           (pop $)
                           $)))
         (when (= (count @input) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
           (let [image (du/->image {:data (take INPUT-SIZE @input)
                                    :max-value MAX-QUANTITY})
                 {:keys [buy? sell?] :as prediction} (get-prediction (take INPUT-SIZE @input))
                 label (cond
                         buy? "buy"
                         sell? "sell"
                         :else "wait")]
             (if ui?
               (let [filepath (du/save-image {:image image
                                              :dir "./dataset"
                                              :filename label
                                              :ui? ui?})]
                 (on-update {:src filepath :label label}))
               (on-update prediction))))))
     keep-running?)))

(defn start! []
  (pipeline-v2 {:on-update (partial trade! SYMBOL)}))