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

(defn add-price-level [col k min-price level-shift]
  (assoc! col k (assoc! (get col k) :price-level (int (/ (- k min-price) level-shift)))))

(def PRICE-LEVELS
  (->> (for [i (range INPUT-SIZE)]
         [i 0])
       (into {})))

(defn calc-quantities-by-price-level [col]
  (reduce (fn [res entry]
            (let [v (val entry)]
              (update res (:price-level v) + (:qty v))))
          PRICE-LEVELS
          col))

(defn low-qty? [price quantities-by-price-level]
  (let [v (val price)
        qty (get quantities-by-price-level (:price-level v))]
    (< qty 0.5)))

(defn denoise
  "This function takes too long, like 4 seconds."
  [series max-price min-price]
  (let [shift (- max-price min-price)
        level-shift (/ shift INPUT-SIZE)
        _ (doseq [s series]
            (doseq [b (keys (:bids s))]
              (add-price-level (:bids s) b min-price level-shift))
            (doseq [a (keys (:asks s))]
              (add-price-level (:asks s) a min-price level-shift)))
        bid-quantities-by-price-level (map (fn [s]
                                             (calc-quantities-by-price-level (:bids s)))
                                           series)
        ask-quantities-by-price-level (map (fn [s]
                                             (calc-quantities-by-price-level (:asks s)))
                                           series)
        _ (doseq [[idx s] (map-indexed vector series)]
            (doseq [b (keys (:bids s))]
              (when (low-qty? b (nth bid-quantities-by-price-level idx))
                (dissoc! (:bids s) b)))
            (doseq [a (keys (:asks s))]
              (when (low-qty? a (nth ask-quantities-by-price-level idx))
                (dissoc! (:asks s) a))))
        all-prices (mapcat #(concat (keys (persistent! (:bids %))) (keys (persistent! (:asks %)))) series)
        new-max-price (+ (apply max all-prices) 0.000001)
        new-min-price (apply min all-prices)]
    (if (and (= new-max-price max-price)
             (= new-min-price min-price))
      [bid-quantities-by-price-level ask-quantities-by-price-level]
      (recur series new-max-price new-min-price))))

(defn create-input-image [order-book-series]
  (let [all-prices (mapcat #(concat (keys (persistent! (:bids %))) (keys (persistent! (:asks %)))) order-book-series)
        max-price (+ (apply max all-prices) 0.000001)
        min-price (apply min all-prices)
        [bid-qties ask-qties] (time (denoise order-book-series max-price min-price))
        image (->image bid-qties ask-qties INPUT-SIZE INPUT-SIZE)
        dir (io/file "./dataset")]
    (when-not (.exists dir)
      (.mkdirs dir))
    (i/save image (str "./dataset/image_" (swap! image-counter inc) " .png"))
    (log/info "saved input image")))

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

(defn add-to-states [current-states event]
  (let [last-state (last current-states)]
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
    (swap! states #(add-to-states % event)))
  (when (= (count @states) STATES-MAX-SIZE)
    (let [data (mapv #(-> %
                          (update :bids seq)
                          (update :asks seq))
                     @states)]
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
;(prepare!)
;(stop-preparation!)