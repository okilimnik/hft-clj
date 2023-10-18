(ns hft.dataset
  (:require [cheshire.core :refer [parse-string]]
            [clojure.core.async :refer [thread]]
            [clojure.java.io :as io]
            [clojure.math :as math]
            [clojure.set :as set]
            [hft.api :as api]
            [mikera.image.core :as i]
            [taoensso.timbre :as log]
            [hft.gcloud :refer [upload-file!]])
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]
           [java.awt Color]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 60)
(def PREDICTION-HEAD 10)
(def LEVEL-PRICE-CHANGE-PERCENT 0.04)
(def BTC-TRADING-AMOUNT 0.02)
(def STATES-MAX-SIZE (+ INPUT-SIZE PREDICTION-HEAD))
(def api-stream-id (atom nil))
(def image-counter (atom 0))
(def states (atom []))
(def MAX-PRICE-INTERVAL-ADDITION 0.000001)
(def QUANTITY-THRESHOLD 0.5)

(defn stop-preparation! []
  (.closeConnection @api/ws-client @api-stream-id))

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
   (reduce (fn [out k]
             (let [v (get out k)]
               (assoc! out k (assoc v :price-level (int (/ (- (parse-double k) min-price) level-shift))))))
           (transient m)
           (.keySet m))))

(defn calc-quantities-by-price-level [m]
  (let [unfiltered (persistent!
                    (reduce (fn [out k]
                              (let [v (get m k)
                                    level (:price-level v)]
                                (assoc! out level (+ (get out level 0) (:qty v)))))
                            (transient {})
                            (.keySet m)))]
    unfiltered
    #_(persistent!
       (reduce (fn [out level]
                 (let [qty (get out level 0)]
                   (if (< qty QUANTITY-THRESHOLD)
                     (dissoc! out level)
                     out)))
               (transient unfiltered)
               (.keySet unfiltered)))))

(defn remove-low-qty [m quantities-by-price-level threshold]
  (persistent!
   (reduce (fn [out k]
             (let [v (get m k)
                   level (:price-level v)
                   qty (get quantities-by-price-level level 0)]
               (if (< qty threshold)
                 (dissoc! out k)
                 out)))
           (transient m)
           (.keySet m))))

(defn get-price-extremums [series]
  (let [prices (concat (mapcat (comp keys :bids) series)
                       (mapcat (comp keys :asks) series))
        max-price (+ (parse-double (apply max-key parse-double prices)) MAX-PRICE-INTERVAL-ADDITION)
        min-price (parse-double (apply min-key parse-double prices))]
    [min-price max-price]))

(defn format-float [price]
  (format "%.6f" price))

(defn add-price-levels [series min-price max-price]
  (let [shift (- max-price min-price)
        level-shift (/ shift INPUT-SIZE)]
    (map (fn [s]
           (-> s
               (update :bids #(add-price-level % min-price level-shift))
               (update :asks #(add-price-level % min-price level-shift))))
         series)))

(defn denoise [series min-price max-price]
  (let [enriched-series (add-price-levels series min-price max-price)
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
    (if (and (= (format-float new-max-price) (format-float max-price))
             (= (format-float new-min-price) (format-float min-price)))
      [bid-quantities-by-price-level ask-quantities-by-price-level]
      (recur filtered-series new-min-price new-max-price))))

(defn calc-change-level [current next]
  (let [shift (abs (- next current))
        change-level (math/floor (/ (* shift 100.0) (* current LEVEL-PRICE-CHANGE-PERCENT)))]
    (if (> change-level 4)
      4
      change-level)))

(defn get-current-buy-price [state]
  (parse-double
   (->> state
        :asks
        (.keySet)
        (filter #(let [qty (get-in state [:asks % :qty])]
                   (> qty BTC-TRADING-AMOUNT)))
        (apply min-key parse-double))))

(defn get-next-sell-price [states]
  (parse-double
   (apply max-key parse-double
          (for [state states]
            (->> state
                 :bids
                 (.keySet)
                 (filter #(let [qty (get-in state [:bids % :qty])]
                            (> qty BTC-TRADING-AMOUNT)))
                 (apply max-key parse-double))))))

(defn get-current-sell-price [state]
  (parse-double
   (->> state
        :bids
        (.keySet)
        (filter #(let [qty (get-in state [:bids % :qty])]
                   (> qty BTC-TRADING-AMOUNT)))
        (apply max-key parse-double))))

(defn get-next-buy-price [states]
  (parse-double
   (apply min-key parse-double
          (for [state states]
            (->> state
                 :asks
                 (.keySet)
                 (filter #(let [qty (get-in state [:asks % :qty])]
                            (> qty BTC-TRADING-AMOUNT)))
                 (apply min-key parse-double))))))

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
                                   (str %1 "0")) "" (reverse (range 1 5)))
          label (str bearish_label bullish_label)]
      label)))

(defn create-input-image [series]
  (let [[min-price max-price] (get-price-extremums series)
        [bid-qties ask-qties] (denoise series min-price max-price)]
    (->image bid-qties ask-qties INPUT-SIZE INPUT-SIZE)))

(defn mapify-prices [order-book]
  (-> order-book
      (update :bids (fn [bids] (->> bids
                                    (mapv (fn [[price qty]]
                                            [price {:qty (parse-double qty)}]))
                                    (into {}))))
      (update :asks (fn [asks] (->> asks
                                    (mapv (fn [[price qty]]
                                            [price {:qty (parse-double qty)}]))
                                    (into {}))))))

(defn filter-pos-qty [m]
  (persistent!
   (reduce (fn [out k]
             (let [v (get out k)]
               (if (pos? (:qty v))
                 out
                 (dissoc! out k))))
           (transient m)
           (.keySet m))))

(defn add-to-states [current-states event]
  (let [last-state (last current-states)]
    (as-> current-states $
      (conj $ (-> last-state
                  (assoc :lastUpdateId (:lastUpdateId event))
                  (update :asks merge (:asks event))
                  (update :asks filter-pos-qty)
                  (update :bids merge (:bids event))
                  (update :bids filter-pos-qty)))
      (if (> (count $) STATES-MAX-SIZE)
        (vec (drop 1 $))
        $))))

(defn calc-new-state [event]
  (when (empty? @states)
    (swap! states conj (-> (api/depth! SYMBOL)
                           mapify-prices)))
  (when (> (:lastUpdateId event) (:lastUpdateId (last @states)))
    (swap! states #(add-to-states % event)))
  (let [snapshot @states]
    (when (= (count snapshot) STATES-MAX-SIZE)
      (thread
        (try
          (let [image (create-input-image (take INPUT-SIZE snapshot))
                label (calc-label (nth snapshot (dec INPUT-SIZE)) (drop INPUT-SIZE snapshot))
                dir (io/file "./dataset")]
            (when (not= label "00000000")
              (when-not (.exists dir)
                (.mkdirs dir))
              (let [filename (str label "_" (swap! image-counter inc) " .png")
                    filepath (str "./dataset/" filename)]
                (i/save image filepath)
                (upload-file! filename filepath)
                (io/delete-file filepath))))
          (catch Exception e
            (stop-preparation!)
            (prn e)))))))

(defn event->readable-event [event]
  (-> event
      (select-keys [:u :b :a])
      (set/rename-keys {:u :lastUpdateId
                        :b :bids
                        :a :asks})
      mapify-prices))

(def on-order-book-change
  (reify WebSocketMessageCallback
    (onMessage [_this json]
      (let [event (parse-string json true)]
        (if (= (:e event) "depthUpdate")
          (try
            (calc-new-state (event->readable-event event))
            (catch Exception e
              (stop-preparation!)
              (prn e)))
          (prn "ws event: " event))))))

(defn prepare! []
  (reset! api-stream-id (.diffDepthStream @api/ws-client SYMBOL 1000 on-order-book-change)))

;(api/init)
;(prepare!)
;(stop-preparation!)