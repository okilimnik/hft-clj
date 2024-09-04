(ns hft.dataset
  (:require [clojure.core.async :as a :refer [<!!]]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [hft.gcloud :as gcloud]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def MIN-QUANTITY 10)
(def PRICE-INTERVAL-FOR-INDEXING 100)
(def DATA-FOLDER "dataset")
(def opened-order? (atom false))

(defn get-image-column [min-price max-price price-interval prices]
  (loop [prices prices
         result (vec (repeat INPUT-SIZE 0))]
    (if (seq prices)
      (let [[price-str qty-str] (first prices)
            price (parse-double price-str)
            qty (parse-double qty-str)]
        (if (and (< price max-price) (>= price min-price))
          (let [level (int (/ (* (- price min-price) INPUT-SIZE) price-interval))]
            (recur (rest prices) (update result level #(+ (or % 0) qty))))
          result))
      result)))

(defn get-min-price [mid-price]
  (- mid-price (* PRICE-INTERVAL-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-max-price [mid-price]
  (+ mid-price (* PRICE-INTERVAL-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-levels-with-max-qty-sorted [prices]
  (->> (map-indexed vector prices)
       (sort-by second >)
       (take 3)
       vec))

(defn get-distance-from-terminator [levels terminator-level]
  (abs (- (ffirst levels) terminator-level)))

(defn get-first-distance [levels]
  (abs (- (ffirst levels) (first (second levels)))))

(defn get-terminator-level-and-qty [prices bids?]
  (let [f (if bids? dec inc)]
    (loop [i (if bids? (dec INPUT-SIZE) 0)]
      (let [qty (nth prices i)]
        (if (> qty MIN-QUANTITY)
          [i qty]
          (recur (f i)))))))

(defn first-qty-change-ratio [levels]
  (float (/ (second (first levels))
            (second (second levels)))))

(defn qty-change-ratio [levels terminator-qty]
  (float (/ (second (first levels))
            terminator-qty)))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))
        bid-levels-with-max-qty-sorted (get-levels-with-max-qty-sorted bids)
        ask-levels-with-max-qty (get-levels-with-max-qty-sorted asks)
        [asks-terminator-level asks-terminator-qty] (get-terminator-level-and-qty asks false)
        [bids-terminator-level bids-terminator-qty] (get-terminator-level-and-qty bids true)]
    {:bids bids
     :bids-terminator-level-and-qty [bids-terminator-level bids-terminator-qty]
     :bid-levels-of-max-qty bid-levels-with-max-qty-sorted
     :max-bid-distance (get-distance-from-terminator bid-levels-with-max-qty-sorted bids-terminator-level)
     :bid-first-distance (get-first-distance bid-levels-with-max-qty-sorted)
     :bid-qty-change-ratio (qty-change-ratio bid-levels-with-max-qty-sorted bids-terminator-qty)
     :bid-first-qty-change-ratio (first-qty-change-ratio bid-levels-with-max-qty-sorted)
     :asks asks
     :asks-terminator-level-and-qty [asks-terminator-level asks-terminator-qty]
     :ask-levels-of-max-qty ask-levels-with-max-qty
     :max-ask-distance (get-distance-from-terminator ask-levels-with-max-qty asks-terminator-level)
     :ask-first-distance (get-first-distance ask-levels-with-max-qty)
     :ask-qty-change-ratio (qty-change-ratio ask-levels-with-max-qty asks-terminator-qty)
     :ask-first-qty-change-ratio (first-qty-change-ratio ask-levels-with-max-qty)}))

(defn save! [inputs]
  (let [data (->> (for [k (keys (first inputs))]
                    [k (mapv k inputs)])
                  (into {}))
        path (str DATA-FOLDER "/" (System/currentTimeMillis))]
    (spit path (with-out-str (pprint data)))))

(defn upload-buy-alert-data! [start end]
  (doall
   (for [f (file-seq (io/file DATA-FOLDER))
         :let [file-time (parse-long (.getName f))]
         :when (and (.isFile f)
                    (> end file-time start))]
     (gcloud/upload-file! f)))
  (doseq [f (file-seq (io/file DATA-FOLDER))
          :when (.isFile f)]
    (io/delete-file f)))

(defn- init []
  (.mkdir (io/file DATA-FOLDER)))

(defn pipeline-v1 []
  (init)
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)]
    (<!!
     (a/merge
      [(scheduler/start!
        6000
        (fn []
          (let [order-book (bi/depth! SYMBOL 5000)]
            (swap! max-bids #(as-> % $
                               (conj $ (parse-double (ffirst (:bids order-book))))
                               (if (> (count $) INPUT-SIZE)
                                 (pop $)
                                 $)))
            (swap! inputs #(as-> % $
                             (conj $ (order-book->quantities-indexed-by-price-level order-book (apply max @max-bids)))
                             (if (> (count $) INPUT-SIZE)
                               (pop $)
                               $)))
            (when (= (count @inputs) INPUT-SIZE)
              (->> @inputs
                   save!)))))
       (scheduler/start!
        (* 5 60000)
        (fn []
          (let [mini-ticker-data (bi/mini-ticker! SYMBOL "15m")
                high-price (parse-double (:highPrice mini-ticker-data))
                low-price (parse-double (:lowPrice mini-ticker-data))
                max-price-change (- high-price low-price)
                price-change (parse-double (:priceChange mini-ticker-data))
                interval-end-time (System/currentTimeMillis)
                interval-start-time (- interval-end-time (* 15 60000))]
            (when (and (> price-change 0) (> max-price-change 200))
              (upload-buy-alert-data! interval-start-time interval-end-time)))))]))))