(ns hft.dataset
  (:require [hft.data :as du]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def LABEL-QUEUE-SIZE 6)
(def MAX-QUANTITY 60)
(def PROFIT 40)
(def PRICE-INTERVAL-FOR-INDEXING 50)
(def TRADING-QTY 0.05)
(def OPEN-ORDER-LAG 0.5)


(defn find-first [pred getter s]
  (loop [s' s]
    (let [el (first s')]
      (if (pred el)
        (getter el)
        (recur (rest s'))))))

(defn find-trade-price [prices]
  (find-first #(>= (parse-double (second %)) (+ TRADING-QTY OPEN-ORDER-LAG)) #(parse-double (first %)) prices))

(defn calc-label [order-books]
  #_(let [current (first order-books)
        nexts (drop 1 order-books)
        buy-price (find-trade-price (:asks current))
        sell-price (find-trade-price (:bids current))
        buy-profit-price (+ buy-price PROFIT)
        sell-profit-price (- sell-price PROFIT)]
    (cond
      (some #(>= % buy-profit-price) (map (comp find-trade-price :bids) nexts)) "buy"
      (some #(<= % sell-profit-price) (map (comp find-trade-price :asks) nexts)) "sell"
      :else "wait"))
  "")

(defn order-book->quantities-indexed-by-price-level [price-interval order-book max-bid]
  (let [mid-level max-bid
        min-level (- mid-level (* price-interval (/ INPUT-SIZE 2)))
        max-level (+ mid-level (* price-interval (/ INPUT-SIZE 2)))
        price-interval (- max-level min-level)]
    {:b (let [result (loop [s (:bids order-book)
                            result (vec (repeat INPUT-SIZE 0))]
                       (if (seq s)
                         (let [[price-str qty-str] (first s)
                               price (parse-double price-str)
                               qty (parse-double qty-str)]
                           (if (>= price min-level)
                             (let [level (int (/ (* (- price min-level) INPUT-SIZE) price-interval))]
                               (recur (rest s) (if (< price max-level)
                                                 (update result level #(+ (or % 0) qty))
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
                             (let [level (int (/ (* (- price min-level) INPUT-SIZE) price-interval))]
                               (recur (rest s) (if (>= price min-level)
                                                 (update result level #(+ (or % 0) qty))
                                                 result)))
                             result))
                         result))]
          result)}))

(def keep-running? (atom true))
(def noise-counter (atom 0))

(defn pipeline-v1 [{:keys [on-update ui?] :or {on-update (fn [_])}}]
  (let [input (atom clojure.lang.PersistentQueue/EMPTY)
        label-queue (atom clojure.lang.PersistentQueue/EMPTY)
        order-books (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     10000
     (fn []
       (let [order-book (bi/depth! SYMBOL 5000)]
         (swap! order-books #(as-> % $
                               (conj $ order-book)
                               (if (> (count $) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
                                 (pop $)
                                 $)))
         (swap! input #(as-> % $
                         (conj $ (order-book->quantities-indexed-by-price-level PRICE-INTERVAL-FOR-INDEXING order-book (apply max (map (fn [order-book] (parse-double (ffirst (:bids order-book)))) @order-books))))
                         (if (> (count $) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
                           (pop $)
                           $)))
         #_(swap! label-queue #(as-> % $
                                 (conj $ order-book)
                                 (if (> (count $) LABEL-QUEUE-SIZE)
                                   (pop $)
                                   $)))
         (when (= (count @input) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
           (let [image (du/->image {:data (take INPUT-SIZE @input)
                                    :max-value MAX-QUANTITY})
                 label (calc-label @label-queue)
                 filepath   (if (and (= label "wait") (not ui?))
                              (let [counter (swap! noise-counter inc)]
                                (when (= counter 20)
                                  (reset! noise-counter 0)
                                  (du/save-image {:image image
                                                  :dir "./dataset"
                                                  :filename label})))
                              (du/save-image {:image image
                                              :dir "./dataset"
                                              :filename label
                                              :ui? ui?}))]
             (on-update {:src filepath :label label})))))
     keep-running?)))

(defn prepare! []
  (bi/init)
  (pipeline-v1 {}))

;(prepare!)
;(reset! keep-running? false)