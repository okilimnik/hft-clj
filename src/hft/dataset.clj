(ns hft.dataset
  (:require
   [clojure.core.async :as a :refer [<!! thread]]
   [clojure.string :as str]
   [hft.market.binance :as bi]
   [hft.xtdb :as db]
   [hft.scheduler :as scheduler]
   [hft.strategy :as strategy :refer [SYMBOL]])
  (:import
   [com.binance.connector.client.utils.websocketcallback WebSocketMessageCallback]))

;; ["BNBUSDT" "BTCUSDT" "ETHUSDT" "SOLUSDT" "PEPEUSDT" "NEIROUSDT" "DOGSUSDT" "WIFUSDT" "FETUSDT" "SAGAUSDT"]

(def INPUT-SIZE 20)
(def PRICE-PERCENT-FOR-INDEXING 0.002)
(def FETCH-INTERVAL-SECONDS 5)

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
  (- mid-price (* mid-price PRICE-PERCENT-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-max-price [mid-price]
  (+ mid-price (* mid-price PRICE-PERCENT-FOR-INDEXING (/ INPUT-SIZE 2))))

(defn get-levels-with-max-qty-sorted [prices]
  (->> (map-indexed vector prices)
       (sort-by second >)
       (take 3)
       vec))

(defn get-distance-from-terminator [prices terminator]
  (abs (- (ffirst (get-levels-with-max-qty-sorted prices)) terminator)))

(defn order-book->quantities-indexed-by-price-level [order-book max-bid]
  (let [mid-price max-bid
        min-price (get-min-price mid-price)
        max-price (get-max-price mid-price)
        price-interval (- max-price min-price)
        bids (get-image-column min-price max-price price-interval (:bids order-book))
        asks (get-image-column min-price max-price price-interval (:asks order-book))]
    {:bids bids
     :asks asks
     :max-ask (second (first (get-levels-with-max-qty-sorted asks)))
     :max-bid (second (first (get-levels-with-max-qty-sorted bids)))
     :max-ask-distance (get-distance-from-terminator asks 10)
     :max-bid-distance (get-distance-from-terminator bids 9)}))

(defn pipeline [{:keys [on-update]}]
  (println "SYMBOL is: " SYMBOL)
  (let [inputs (atom clojure.lang.PersistentQueue/EMPTY)
        max-bids (atom clojure.lang.PersistentQueue/EMPTY)
        depth-cache (atom clojure.lang.PersistentQueue/EMPTY)
        order-book (atom nil)]
    (<!!
     (a/merge
      [(scheduler/start!
        60000
        (fn []
          (reset! order-book (-> (bi/depth! SYMBOL 5000)
                                 (update :bids #(into {} %))
                                 (update :asks #(into {} %))))
          (doseq [depth @depth-cache]
            (when (> (:lastUpdateId @order-book) (:u depth))
              (swap! order-book #(-> %
                                     (update :bids merge (into {} (:b depth)))
                                     (update :asks merge (into {} (:a depth)))))))))
       (scheduler/start!
        (* 1000 FETCH-INTERVAL-SECONDS)
        (fn []
          (when @order-book
            (let [order-book' (-> @order-book
                                  (update :bids #(->> (into [] %)
                                                      (sort-by (comp parse-double first) >)))
                                  (update :asks #(->> (into [] %)
                                                      (sort-by (comp parse-double first) <))))]
              (swap! max-bids #(as-> % $
                                 (conj $ (parse-double (ffirst (:bids order-book'))))
                                 (if (> (count $) INPUT-SIZE)
                                   (pop $)
                                   $)))
              (swap! inputs #(as-> % $
                               (conj $ (order-book->quantities-indexed-by-price-level order-book' (apply max @max-bids)))
                               (if (> (count $) INPUT-SIZE)
                                 (pop $)
                                 $))))
            (when (= (count @inputs) INPUT-SIZE)
              (db/put! {:xt/id (db/gen-id [SYMBOL FETCH-INTERVAL-SECONDS INPUT-SIZE])
                        :order-book/timestamp (System/currentTimeMillis)
                        :order-book/symbol SYMBOL
                        :order-book/bids (vec
                                          (map-indexed (fn [level volume]
                                                         {:price/level level
                                                          :price/quantity volume})
                                                       (-> @inputs last :bids)))
                        :order-book/asks (vec
                                          (map-indexed (fn [level volume]
                                                         {:price/level level
                                                          :price/quantity volume})
                                                       (-> @inputs last :asks)))})))))
       (thread
         (try
           (let [depth (str (str/lower-case SYMBOL) "@depth")]
             (bi/subscribe [depth]
                           (reify WebSocketMessageCallback
                             ^void (onMessage [_ event-str]

                                              (let [event (bi/jread event-str)
                                                    data (:data event)]
                                                (cond
                                                  (= (:stream event) depth)
                                                  (do
                                                    (swap! depth-cache #(as-> % $
                                                                          (conj $ data)
                                                                          (if (> (count $) 50)
                                                                            (pop $)
                                                                            $)))
                                                    (when @order-book
                                                      (doseq [depth @depth-cache]
                                                        (when (> (:lastUpdateId @order-book) (:u depth))
                                                          (swap! order-book #(-> %
                                                                                 (update :bids merge (into {} (:b depth)))
                                                                                 (update :asks merge (into {} (:a depth)))))))))))))))
           (catch Exception e (prn e))))]))))
