(ns hft.dataset
  (:require [clojure.core.async :refer [<!! >!! chan sliding-buffer thread]]
            [clojure.java.io :as io]
            [clojure.math :as math]
            [hft.api :as api]
            [hft.gcloud :refer [upload-file!]]
            [mikera.image.core :as i]
            [taoensso.timbre :as log])
  (:import [java.awt Color]))

(def SYMBOL "BTCUSDT")
(def DECIMALS 2)
(def INPUT-SIZE 60)
(def PREDICTION-HEAD 10)
(def LEVEL-PRICE-CHANGE-PERCENT 0.04)
(def BTC-TRADING-AMOUNT 0.02)
(def STATES-MAX-SIZE (+ INPUT-SIZE PREDICTION-HEAD))
;; using vector for states (and subvec fn) causes HeapOutOfMemory errors
(def states (atom clojure.lang.PersistentQueue/EMPTY))
(def MAX-PRICE-INTERVAL-ADDITION 0.1)
(def QUANTITY-THRESHOLD 0.5)
(def input-chan (chan (sliding-buffer 1)))
(def consuming-running? (atom false))
(def image-counter (atom nil))
(def image-counter-file "./image-counter.txt")

(defn get-image-number! []
  (let [new-val (swap! image-counter inc)]
    (spit image-counter-file (str new-val))
    new-val))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [bid-qties ask-qties width height]
  (let [all-qties (concat (mapcat vals bid-qties)
                          (mapcat vals ask-qties))
        max-qty (+ (apply max all-qties) MAX-PRICE-INTERVAL-ADDITION)
        min-qty (apply min all-qties)
        shift (/ (- max-qty min-qty) 255)
        image (i/new-image width height)
        pixels (i/get-pixels image)]
    (dotimes [idx (* width height)]
      (let [level (int (/ idx width))
            series-idx (mod idx width)]
        (aset pixels idx
              (.getRGB (Color.
                        (long (/ (- (get (nth bid-qties series-idx) level min-qty) min-qty) shift))
                        (long (/ (- (get (nth ask-qties series-idx) level min-qty) min-qty) shift))
                        (long 0))))))
    (i/set-pixels image pixels)
    image))

(defn add-price-level [m min-price level-shift]
  (persistent!
   (reduce-kv
    (fn [out k v]
      (assoc! out k (assoc v :price-level (int (/ (- k min-price) level-shift)))))
    (transient m)
    m)))

(defn calc-quantities-by-price-level [m]
  (persistent!
   (reduce-kv (fn [out _k v]
                (let [level (:price-level v)]
                  (assoc! out level (+ (get out level 0) (:qty v)))))
              (transient {})
              m)))

(defn remove-low-qty [m quantities-by-price-level threshold]
  (persistent!
   (reduce-kv (fn [out k v]
                (let [level (:price-level v)
                      qty (get quantities-by-price-level level 0)]
                  (if (< qty threshold)
                    (dissoc! out k)
                    out)))
              (transient m)
              m)))

(defn get-price-extremums [series]
  (let [prices (concat (mapcat (comp keys :bids) series)
                       (mapcat (comp keys :asks) series))
        max-price (+ (apply max prices) MAX-PRICE-INTERVAL-ADDITION)
        min-price (apply min prices)]
    [min-price max-price]))

(defn denoise [series min-price max-price]
  (let [shift (- max-price min-price)
        level-shift (/ shift INPUT-SIZE)
        enriched-series (map (fn [s]
                               (-> s
                                   (update :bids #(add-price-level % min-price level-shift))
                                   (update :asks #(add-price-level % min-price level-shift))))
                             series)
        bid-quantities-by-price-level (map (fn [s]
                                             (calc-quantities-by-price-level (:bids s)))
                                           enriched-series)
        ask-quantities-by-price-level (map (fn [s]
                                             (calc-quantities-by-price-level (:asks s)))
                                           enriched-series)
        filtered-series (map-indexed
                         (fn [idx s]
                           (-> s
                               (update :bids #(remove-low-qty % (nth bid-quantities-by-price-level idx) QUANTITY-THRESHOLD))
                               (update :asks #(remove-low-qty % (nth ask-quantities-by-price-level idx) QUANTITY-THRESHOLD))))
                         enriched-series)
        [new-min-price new-max-price] (get-price-extremums filtered-series)]
    (if (and (= new-max-price max-price)
             (= new-min-price min-price))
      [bid-quantities-by-price-level ask-quantities-by-price-level]
      (recur filtered-series new-min-price new-max-price))))

(defn calc-change-level [current next]
  (let [shift (abs (- next current))
        change-level (int (math/floor (/ (* shift 100.0) (* current LEVEL-PRICE-CHANGE-PERCENT))))]
    (if (> change-level 4)
      4
      change-level)))

(defn get-current-buy-price [state]
  (->> state
       :asks
       (.keySet)
       (filter #(let [qty (get-in state [:asks % :qty])]
                  (> qty BTC-TRADING-AMOUNT)))
       (apply min)))

(defn get-next-sell-price [snapshot]
  (apply max
         (for [state snapshot]
           (->> state
                :bids
                (.keySet)
                (filter #(let [qty (get-in state [:bids % :qty])]
                           (> qty BTC-TRADING-AMOUNT)))
                (apply max)))))

(defn get-current-sell-price [state]
  (->> state
       :bids
       (.keySet)
       (filter #(let [qty (get-in state [:bids % :qty])]
                  (> qty BTC-TRADING-AMOUNT)))
       (apply max)))

(defn get-next-buy-price [snapshot]
  (apply min
         (for [state snapshot]
           (->> state
                :asks
                (.keySet)
                (filter #(let [qty (get-in state [:asks % :qty])]
                           (> qty BTC-TRADING-AMOUNT)))
                (apply min)))))

(defn calc-label [current-state next-states]
  (let [current-buy-price (get-current-buy-price current-state)
        next-sell-price (get-next-sell-price next-states)
        current-sell-price (get-current-sell-price current-state)
        next-buy-price (get-next-buy-price next-states)
        bullish-change-level (atom 0)
        bearish-change-level (atom 0)]
    (when (> next-sell-price current-buy-price)
      (reset! bullish-change-level (calc-change-level current-buy-price next-sell-price)))
    (when (< next-buy-price current-sell-price)
      (reset! bearish-change-level (calc-change-level current-sell-price next-buy-price)))
    (when (= @bullish-change-level @bearish-change-level)
      (reset! bullish-change-level 0)
      (reset! bearish-change-level 0))
    (let [bullish_label (reduce #(if (= %2 @bullish-change-level)
                                   (str %1 "1")
                                   (str %1 "0")) "" (range 1 5))
          bearish_label (reduce #(if (= %2 @bearish-change-level)
                                   (str %1 "1")
                                   (str %1 "0")) "" (reverse (range 1 5)))]
      (str bearish_label bullish_label))))

(defn create-input-image [series]
  (let [[min-price max-price] (get-price-extremums series)
        [bid-qties ask-qties] (time (denoise series min-price max-price))]
    (->image bid-qties ask-qties INPUT-SIZE INPUT-SIZE)))

(defn ->int [price]
  (int (* (parse-double price) (Math/pow 10 DECIMALS))))

(defn mapify-prices [order-book]
  (-> order-book
      (update :bids (fn [bids] (->> bids
                                    (mapv (fn [[price qty]]
                                            [(->int price) {:qty (parse-double qty)}]))
                                    (into {}))))
      (update :asks (fn [asks] (->> asks
                                    (mapv (fn [[price qty]]
                                            [(->int price) {:qty (parse-double qty)}]))
                                    (into {}))))))

(defn calc-new-state [chs]
  (let [event (api/depth! SYMBOL)]
    (when (> (:lastUpdateId event) (or (:lastUpdateId (last @states)) 0))
      (swap! states (fn [s]
                      (as-> s $
                        (conj $ (mapify-prices event))
                        (if (> (count $) STATES-MAX-SIZE)
                          (pop $)
                          $))))
      (let [snapshot @states]
        (doseq [ch chs]
          (>!! ch snapshot))))))

(defn start-consumer! []
  (reset! consuming-running? true)
  (thread
    (while @consuming-running?
      (let [snapshot (<!! input-chan)]
        (when (= (count snapshot) STATES-MAX-SIZE)
          (try
            (let [image (create-input-image (take INPUT-SIZE snapshot))
                  label (calc-label (nth snapshot (dec INPUT-SIZE)) (drop INPUT-SIZE snapshot))]
              (log/debug "Created and labeled input data " (if (= label "00000000") "as noise" "as valid"))
              (when (or (= label "10000000")
                        (= label "00000001"))

                (let [train-dir (io/file "./dataset")]
                  (when-not (.exists train-dir)
                    (.mkdirs train-dir))
                  (let [train-filename (str label "_" (get-image-number!) ".png")
                        train-filepath (str "./dataset/" train-filename)]
                    (i/save image train-filepath)
                    (upload-file! train-filename train-filepath)
                    (io/delete-file train-filepath)))))
            
            (catch Exception e
              (log/error e))))))))

(defn start-producer! [chs]
  (let [interval 3000]
    (loop [t (System/currentTimeMillis)]
      (let [delta (- (System/currentTimeMillis) t)]
        (if (>= delta interval)
          (do (calc-new-state chs)
              (recur (System/currentTimeMillis)))
          (do (Thread/sleep (- interval delta))
              (recur t)))))))

(defn init-image-counter []
  (let [init-val (parse-long
                  (try
                    (slurp image-counter-file)
                    (catch Exception _
                      "0")))]
    (reset! image-counter init-val)))

(defn prepare! []
  (api/init)
  (init-image-counter)
  (start-consumer!)
  (start-producer! [input-chan]))

;(api/init)
;(prepare!)
;(stop-preparation!)