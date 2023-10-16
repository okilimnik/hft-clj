(ns hft.dataset
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.math :as math]
            [taoensso.timbre :as log]
            [mikera.image.core :as i]
            [mikera.image.colours :as c]
            [cheshire.core :refer [parse-string]]
            [hft.api :as api])
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 60)
(def PREDICTION-HEAD 10)
(def LEVEL-PRICE-CHANGE-PERCENT 0.04)
(def BTC-TRADING-AMOUNT 0.02)
(def STATES-MAX-SIZE (+ INPUT-SIZE PREDICTION-HEAD))
(def api-stream-id (atom nil))
(def image-counter (atom 0))
(def states (atom []))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [bid-qties ask-qties width height]
  (let [all-qties (concat (mapcat vals bid-qties)
                          (mapcat vals ask-qties))
        max-qty (+ (apply max all-qties) 0.000001)
        min-qty (apply min all-qties)
        shift (/ (- max-qty min-qty) 255)
        image (i/new-image width height)
        pixels (i/get-pixels image)]
    (dotimes [idx width]
      (dotimes [level height]
        (aset pixels (* idx level) (let [pixel (c/argb
                                                (math/round (/ (get (nth bid-qties idx) level 0) shift))
                                                (math/round (/ (get (nth ask-qties idx) level 0) shift))
                                                0
                                                1)]
                                     pixel))))
    (i/set-pixels image pixels)
    image))

(defn add-price-level [m min-price level-shift]
  (persistent!
   (reduce (fn [out k]
             (let [v (get out k)]
               (assoc! out k (assoc v :price-level (int (/ (- k min-price) level-shift))))))
           (transient m)
           (.keySet m))))

(def PRICE-LEVELS
  (->> (for [i (range INPUT-SIZE)]
         [i 0])
       (into {})))

(defn calc-quantities-by-price-level [m]
  (persistent!
   (reduce (fn [out k]
             (let [v (get m k)
                   level (:price-level v)]
               (assoc! out level (+ (get out level) (:qty v)))))
           (transient PRICE-LEVELS)
           (.keySet m))))

(defn remove-low-qty [m quantities-by-price-level]
  (persistent!
   (reduce (fn [out k]
             (let [v (get m k)
                   level (:price-level v)
                   qty (get quantities-by-price-level level)]
               (if (< qty 0.5)
                 (dissoc! out k)
                 out)))
           (transient m)
           (.keySet m))))

(defn denoise
  "This function takes too long, like 4 seconds."
  [series max-price min-price]
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
                               (update :bids #(remove-low-qty % (nth bid-quantities-by-price-level idx)))
                               (update :asks #(remove-low-qty % (nth ask-quantities-by-price-level idx)))))
                         enriched-series)
        all-prices (concat (mapcat (comp keys :bids) series)
                           (mapcat (comp keys :asks) series))
        new-max-price (+ (apply max all-prices) 0.000001)
        new-min-price (apply min all-prices)]
    (if (and (= new-max-price max-price)
             (= new-min-price min-price))
      [bid-quantities-by-price-level ask-quantities-by-price-level]
      (recur filtered-series new-max-price new-min-price))))

(defn calc-change-level [current next]
  (let [shift (abs (- next current))
        change-level (math/floor (/ (* shift 100.0) (* current LEVEL-PRICE-CHANGE-PERCENT)))]
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

(defn get-next-sell-price [states]
  (apply max
         (for [state states]
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

(defn get-next-buy-price [states]
  (apply min
         (for [state states]
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
                                   (str %1 "0")) "" (reverse (range 1 5)))
          label (str bearish_label bullish_label)]
      (when-not (= label "00000000")
        label))))

(defn create-input-image [series]
  (let [all-prices (concat (mapcat (comp keys :bids) series)
                           (mapcat (comp keys :asks) series))
        max-price (+ (apply max all-prices) 0.000001)
        min-price (apply min all-prices)
        [bid-qties ask-qties] (time (denoise series max-price min-price))]
    (->image bid-qties ask-qties INPUT-SIZE INPUT-SIZE)))

(defn mapify-prices [order-book]
  (-> order-book
      (update :bids (fn [bids] (->> bids
                                    (mapv (fn [[price qty]]
                                            [(parse-double price) {:qty (parse-double qty)}]))
                                    (into {}))))
      (update :asks (fn [asks] (->> asks
                                    (mapv (fn [[price qty]]
                                            [(parse-double price) {:qty (parse-double qty)}]))
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
  (when (= (count @states) STATES-MAX-SIZE)
    (let [image (create-input-image (take INPUT-SIZE @states))
          label (calc-label (nth @states (dec INPUT-SIZE)) (drop INPUT-SIZE @states))
          dir (io/file "./dataset")]
      (when label
        (when-not (.exists dir)
          (.mkdirs dir))
        (i/save image (str "./dataset/" label "_" (swap! image-counter inc) " .png"))
        (log/info "saved input image")))))

(defn stop-preparation! []
  (.closeConnection @api/ws-client @api-stream-id))

(defn event->readable-event [event]
  (-> event
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
              (prn e)
              (throw e)))
          (prn "ws event: " event))))))

(defn prepare! []
  (reset! api-stream-id (.diffDepthStream @api/ws-client SYMBOL 1000 on-order-book-change)))

;(api/init)
;(prepare!)
;(stop-preparation!)