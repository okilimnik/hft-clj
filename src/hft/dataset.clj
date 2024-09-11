(ns hft.dataset
  (:require [clojure.core.async :as a :refer [<!!]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hft.async :refer [vthread]]
            [hft.gcloud :as gcloud]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]) 
  (:import [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]
           [java.time Instant ZoneId ZonedDateTime]
           [org.ta4j.core BarSeries BaseBarSeries]
           [org.ta4j.core.indicators ParabolicSarIndicator]))

(def SYMBOL "BTCUSDT")
(def INPUT-SIZE 20)
(def MIN-QUANTITY 10)
(def PRICE-INTERVAL-FOR-INDEXING 100)
(def STOP-LOSS 100)
(def DATAFILE "data.tsv")

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

(def order (atom nil))

(defn open-order [price inputs]
  (reset! order {:price price
                 :inputs inputs}))

(defn ->tsv [label data]
  (spit DATAFILE (str/join " " (concat [label] (mapcat :bids data) (mapcat :asks data) ["\n"])) :append true))

(defn close-order [price]
  (if (> (:price @order) price)
    (->tsv 1 (:inputs @order))
    (->tsv 0 (:inputs @order)))
  (reset! order nil))

(defn pipeline-v1 []
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)
        klines (atom clojure.lang.PersistentQueue/EMPTY)]
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
                               $))))))
       (vthread
        (let [klines-1m (str (str/lower-case SYMBOL) "@kline_1s")
              series-length 50]
          (bi/subscribe [klines-1m]
                        (reify WebSocketMessageCallback
                          ^void (onMessage [_ event-str]
                                           (try
                                             (let [event (bi/jread event-str)
                                                   data (:k (:data event))
                                                   kline-closed? (:x data)]
                                               (when (and @order
                                                          (>= (- (:price @order) (:c (first @klines)))
                                                              STOP-LOSS))
                                                 (close-order (:c (first @klines))))
                                               (cond
                                                 (and (= klines-1m (:stream event))
                                                      kline-closed?)
                                                 (do (swap! klines #(as-> % $
                                                                      (conj $ (-> data
                                                                                  (select-keys [:t :o :h :l :c :v :n])
                                                                                  (update :o parse-double)
                                                                                  (update :h parse-double)
                                                                                  (update :l parse-double)
                                                                                  (update :c parse-double)
                                                                                  (update :v parse-double)))
                                                                      (if (> (count $) series-length)
                                                                        (pop $)
                                                                        $)))
                                                     (when (>= (count @klines) series-length) ;; warmed-up

                                                       (let [series (BaseBarSeries. "1m")]
                                                         (doseq [{:keys [o h l c t v n]} @klines]
                                                           (let [date (ZonedDateTime/ofInstant
                                                                       (Instant/ofEpochMilli t)
                                                                       (ZoneId/systemDefault))]
                                                             (.addBar ^BarSeries series date o h l c v n)))
                                                         (let [psar (ParabolicSarIndicator. series)
                                                               prev-index (- series-length 2)
                                                               prev-value (.doubleValue (.getValue psar prev-index))
                                                               curr-index (dec series-length)
                                                               curr-value (.doubleValue (.getValue psar curr-index))]
                                                           (cond
                                                             (and (> curr-value (:h (nth @klines curr-index))) (< prev-value (:l (nth @klines prev-index))))
                                                             (do (prn "sell signal")
                                                                 (when @order
                                                                   (close-order (:c (nth @klines curr-index))))
                                                                 #_(->> @inputs
                                                                        save!))
                                                             (and (< curr-value (:l (nth @klines curr-index))) (> prev-value (:h (nth @klines prev-index))))
                                                             (do (prn "buy signal")
                                                                 (open-order (:c (nth @klines curr-index)) @inputs)))))))))
                                             (catch Exception e (prn e))))))))

       (scheduler/start!
        (* 3 60 60 1000) ;; every 3 hour
        (fn []
          (let [f (io/file DATAFILE)]
            (when (.exists f)
              (gcloud/upload-file! f)))))]))))