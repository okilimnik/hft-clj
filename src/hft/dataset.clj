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
(def DATA-FOLDER "./dataset")

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

(defn get-terminator-level-and-qty [prices bids?]
  (let [f (if bids? dec inc)]
    (loop [i (if bids? (dec INPUT-SIZE) 0)]
      (let [qty (nth prices i)]
        (if (> qty MIN-QUANTITY)
          [i qty]
          (recur (f i)))))))

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
     :bid-qty-change-ratio (qty-change-ratio bid-levels-with-max-qty-sorted bids-terminator-qty)
     :asks asks
     :asks-terminator-level-and-qty [asks-terminator-level asks-terminator-qty]
     :ask-levels-of-max-qty ask-levels-with-max-qty
     :max-ask-distance (get-distance-from-terminator ask-levels-with-max-qty asks-terminator-level)
     :ask-qty-change-ratio (qty-change-ratio ask-levels-with-max-qty asks-terminator-qty)}))

(defn analyze [inputs]
  (let [data {:ask-levels-of-max-qty (mapv :ask-levels-of-max-qty inputs)
              :max-ask-distance (mapv :max-ask-distance inputs)
              :ask-qty-change-ratio (mapv :ask-qty-change-ratio inputs)
              :asks-terminator-level-and-qty (mapv :asks-terminator-level-and-qty inputs)
              :bid-levels-of-max-qty (mapv :bid-levels-of-max-qty inputs)
              :max-bid-distance (mapv :max-bid-distance inputs)
              :bid-qty-change-ratio (mapv :bid-qty-change-ratio inputs)
              :bids-terminator-level-and-qty (mapv :bids-terminator-level-and-qty inputs)}
        data-folder (io/file DATA-FOLDER)]
    (.mkdir data-folder)
    (spit (str DATA-FOLDER "/" (System/currentTimeMillis)) (with-out-str (pprint data))))
  :wait)

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

(defn pipeline-v1 []
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
                   analyze
                   #_(trade! SYMBOL))))))
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