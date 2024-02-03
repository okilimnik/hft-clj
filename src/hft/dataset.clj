(ns hft.dataset
  (:require [hft.binance :as bi]
            [hft.data :as du]
            [hft.scheduler :as scheduler]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def LABEL-QUEUE-SIZE 8)
(def MAX-QUANTITY 50)
(def PROFIT 40)

(defn calc-label [order-books]
  (let [current (first order-books)
        next (drop 1 order-books)]))

(defn order-book->quantities-by-price-level [price-interval order-book]
  [[]])

(defn pipeline-v1 []
  (let [input (atom clojure.lang.PersistentQueue/EMPTY)
        label-queue (atom clojure.lang.PersistentQueue/EMPTY)]
    (scheduler/start!
     3000
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
       (when (= (count @input) (+ INPUT-SIZE LABEL-QUEUE-SIZE))
         (let [image (du/->image {:data (take INPUT-SIZE @input)
                                  :max-value MAX-QUANTITY})
               label (calc-label @label-queue)]
           (when label
             (du/save-image {:image image
                             :dir "./dataset"
                             :filename label
                             :local? (System/getenv "LOCAL")}))))))))

(defn prepare! []
  (pipeline-v1))