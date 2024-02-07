(ns hft.dataset
  (:require [hft.binance :as bi]
            [hft.data :as du]
            [hft.scheduler :as scheduler]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def LABEL-QUEUE-SIZE 6)
(def MAX-QUANTITY 50)
(def PRICE-INTERVAL-FOR-INDEXING 5)
(def TRADING-QTY 0.05)
(def OPEN-ORDER-LAG 0.5)
(def PROFIT 40)

(defn find-first [pred getter s]
  (loop [s' s]
    (let [el (first s')]
      (if (pred el)
        (getter el)
        (recur (rest s'))))))

(defn find-trade-price [prices]
  (find-first #(>= (parse-double (second %)) (+ TRADING-QTY OPEN-ORDER-LAG)) #(parse-double (first %)) prices))

(defn calc-label [order-books]
  (let [current (first order-books)
        nexts (drop 1 order-books)
        buy-price (find-trade-price (:asks current))
        sell-price (find-trade-price (:bids current))
        buy-profit-price (+ buy-price PROFIT)
        sell-profit-price (- sell-price PROFIT)]
    (cond
      (some #(>= % buy-profit-price) (map (comp find-trade-price :bids) nexts)) :buy
      (some #(<= % sell-profit-price) (map (comp find-trade-price :asks) nexts)) :sell)))

(defn get-price-level [price interval]
  (-> (/ price interval)
      int
      (* interval)))

(defn get-price-level-index [level min-level price-interval]
  (let [index (/ (- level min-level) price-interval)]
    index))

(defn order-book->quantities-indexed-by-price-level [price-interval order-book]
  (concat
   (let [max-bid (parse-double (ffirst (:bids order-book)))
         max-level (get-price-level max-bid price-interval)
         min-level (- max-level (* price-interval (dec (/ INPUT-SIZE 2))))
         result (loop [s (:bids order-book)
                       result (vec (repeat (/ INPUT-SIZE 2) 0))]
                  (let [[price-str qty-str] (first s)
                        price (parse-double price-str)
                        qty (parse-double qty-str)]
                    (if (>= price min-level)
                      (let [level (get-price-level price price-interval)]
                        (recur (rest s) (update result (get-price-level-index level min-level price-interval) #(+ (or % 0) qty))))
                      result)))]
     result)
   (let [min-ask (parse-double (ffirst (:asks order-book)))
         min-level (get-price-level min-ask price-interval)
         max-level (+ min-level (* price-interval (/ INPUT-SIZE 2)))
         result (loop [s (:asks order-book)
                       result (vec (repeat (/ INPUT-SIZE 2) 0))]
                  (let [[price-str qty-str] (first s)
                        price (parse-double price-str)
                        qty (parse-double qty-str)]
                    (if (< price max-level)
                      (let [level (get-price-level price price-interval)]
                        (recur (rest s) (update result (get-price-level-index level min-level price-interval) #(+ (or % 0) qty))))
                      result)))]
     result)))

(defn pipeline-v1 []
  (let [input (atom clojure.lang.PersistentQueue/EMPTY)
        label-queue (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     3000
     (fn []
       (let [order-book (bi/depth! SYMBOL 1000)]
         (swap! input #(as-> % $
                         (conj $ (order-book->quantities-indexed-by-price-level PRICE-INTERVAL-FOR-INDEXING order-book))
                         (if (> (count $) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
                           (pop $)
                           $)))
         (swap! label-queue #(as-> % $
                               (conj $ order-book)
                               (if (> (count $) LABEL-QUEUE-SIZE)
                                 (pop $)
                                 $)))
         (when (= (count @input) (dec (+ INPUT-SIZE LABEL-QUEUE-SIZE)))
           (let [image (du/->image {:data (take INPUT-SIZE @input)
                                    :max-value MAX-QUANTITY})
                 label (calc-label @label-queue)]
             (when label
               (du/save-image {:image image
                               :dir "./dataset"
                               :filename label
                               :local? (System/getenv "LOCAL")})))))))))

(defn prepare! []
  (pipeline-v1))