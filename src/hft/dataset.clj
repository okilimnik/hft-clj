(ns hft.dataset
  (:require [hft.binance :as bi]
            [hft.data :as du]
            [hft.scheduler :as scheduler]
            [clojure.string :as s]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def LABEL-QUEUE-SIZE 8)
(def MAX-QUANTITY 50)
(def PROFIT 40)

(defn calc-label [order-books]
  (let [current (first order-books)
        next (drop 1 order-books)]
    (throw (ex-info "Test error" {}))))

(defn get-price-level [price interval]
  (-> (/ price interval)
      int
      (* interval)))

(defn get-price-level-index [level min-level price-interval]
  (let [index (/ (- level min-level) price-interval)]
    index))

(defn order-book->quantities-by-price-level [price-interval order-book]
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
                         (conj $ (order-book->quantities-by-price-level 5 order-book))
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