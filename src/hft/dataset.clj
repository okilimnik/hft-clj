(ns hft.dataset
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.math :as math]
            [clojure.core.async :refer [thread]]
            [taoensso.timbre :as log]
            [mikera.image.core :as i]
            [mikera.image.colours :as c]
            [cheshire.core :refer [parse-string]]
            [hft.api :as api])
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 60)
(def PREDICTION-HEAD 10)
(def STATES-MAX-SIZE (+ INPUT-SIZE PREDICTION-HEAD))
(def api-stream-id (atom nil))

(def image-counter (atom 0))

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
        (aset pixels (* idx level) (c/argb (math/round (/ (get (nth bid-qties idx) level) shift))
                                           (math/round (/ (get (nth ask-qties idx) level) shift))
                                           0
                                           1))))
    (i/set-pixels image pixels)
    image))

(defn add-price-level [min-price level-shift prices-map]
  (->> prices-map
       (mapv (fn [entry]
               (let [price (key entry)]
                 [price (merge (val entry) {:price-level (int (/ (- price min-price) level-shift))})])))
       (into {})))

(defn calc-quantities-by-price-level [prices-map]
  (reduce (fn [res entry]
            (let [v (val entry)]
              (update res (:price-level v) + (:qty v))))
          (->> (for [i (range INPUT-SIZE)]
                 [i 0])
               vec
               (into {}))
          prices-map))

(defn remove-low-qty [quantities-by-price-level prices-map]
  (->> prices-map
       (remove (fn [itm]
                 (let [qty (get quantities-by-price-level (:price-level (second itm)))]
                   (< qty 0.1))))
       (into {})))

(defn denoise 
  "This function takes too long, like 4 seconds." 
  [series]
  (let [all-prices (concat (mapcat (comp keys :bids) series)
                           (mapcat (comp keys :asks) series))
        max-price (+ (apply max all-prices) 0.000001)
        min-price (apply min all-prices)
        shift (- max-price min-price)
        level-shift (/ shift INPUT-SIZE)
        enriched-series (vec
                         (for [s series]
                           (-> s
                               (update :bids (partial add-price-level min-price level-shift))
                               (update :asks (partial add-price-level min-price level-shift)))))
        bid-quantities-by-price-level (vec
                                       (for [s enriched-series]
                                         (calc-quantities-by-price-level (:bids s))))
        ask-quantities-by-price-level (vec
                                       (for [s enriched-series]
                                         (calc-quantities-by-price-level (:asks s))))
        filtered-series (vec
                         (for [[idx s] (map-indexed vector enriched-series)]
                           (-> s
                               (update :bids (partial remove-low-qty (nth bid-quantities-by-price-level idx)))
                               (update :asks (partial remove-low-qty (nth ask-quantities-by-price-level idx))))))]
    (if (= series filtered-series)
      [filtered-series bid-quantities-by-price-level ask-quantities-by-price-level]
      (recur filtered-series))))

#_(defn validate-series [series]
    (let [ask-price-levels (->> (:asks series)
                                (map (fn [entry] (:price-level (val entry))))
                                set)
          bid-price-levels (->> (:bids series)
                                (map (fn [entry] (:price-level (val entry))))
                                set)]
      #_(when (seq (set/intersection ask-price-levels bid-price-levels))
          (throw (ex-info "Ask and bid price levels intersect!" {:intersected-levels (set/intersection ask-price-levels bid-price-levels)
                                                                 :ask-price-levels ask-price-levels
                                                                 :bid-price-levels bid-price-levels})))
      (try (m/validate Series series)
           (catch Exception _e
             (throw (ex-info "Series don't fit into the schema!" {:error (me/humanize (m/explain Series series))}))))))

(defn create-input-image [order-book-series]
  (let [[denoised-series bid-qties ask-qties] (time (denoise order-book-series))
        image (->image bid-qties ask-qties INPUT-SIZE INPUT-SIZE)
        dir (io/file "./dataset")]
    (when-not (.exists dir)
      (.mkdirs dir))
    (i/save image (str "./dataset/image_" (swap! image-counter inc) " .png"))))

(defn states-validator [states]
  (or (empty? states)
      (and (reduce (fn [lastUpdateId itm]
                     (when (and lastUpdateId (> (:lastUpdateId itm) lastUpdateId))
                       (:lastUpdateId itm))) (:lastUpdateId (first states)) (rest states))
           (<= (count states) STATES-MAX-SIZE))))

(def states (atom [] :validator states-validator))

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

(defn filter-pos-qty [price-map]
  (->> price-map
       (filter (comp pos? :qty val))
       (into {})))

(defn add-to-states [event current-states]
  (let [last-state (last @states)]
    (as-> current-states $
      (conj $ (-> last-state
                  (assoc :lastUpdateId (:finalUpdateId event))
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
  (when (> (:finalUpdateId event) (:lastUpdateId (last @states)))
    (swap! states (partial add-to-states event)))
  (when (= (count @states) STATES-MAX-SIZE)
    (let [data @states]
      (create-input-image data))))

(defn stop-preparation! []
  (.closeConnection @api/ws-client @api-stream-id))

(def on-order-book-change
  (reify WebSocketMessageCallback
    (onMessage [_this json]
      (let [event (parse-string json true)]
        (if (= (:e event) "depthUpdate")
          (try
            (calc-new-state (-> event
                                (set/rename-keys {:u :finalUpdateId
                                                  :b :bids
                                                  :a :asks})
                                mapify-prices))
            (catch Exception e
              (stop-preparation!)
              (prn e)
              (throw e)))
          (prn "ws event: " event))))))

(defn prepare! []
  (reset! api-stream-id (.diffDepthStream @api/ws-client SYMBOL 1000 on-order-book-change)))

;(api/init)
(prepare!)
(stop-preparation!)