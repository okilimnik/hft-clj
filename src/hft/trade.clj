(ns hft.trade
  (:require [clojure.core.async :refer [<! go-loop timeout]]
            [clojure.java.io :as io]
            [hft.data :as du]
            [hft.dataset :as dataset]
            [hft.market.binance :as bi]
            [hft.scheduler :as scheduler]
            [hft.train :as train]
            [mikera.image.core :as i]
            [taoensso.timbre :as log])
  (:import [ai.djl.modality.cv ImageFactory]
           [ai.djl.modality.cv.transform ToTensor]
           [ai.djl.modality.cv.translator ImageClassificationTranslator]
           [ai.djl.ndarray NDManager]
           [java.nio.file Paths]
           [java.util Arrays]))

(def SYMBOL dataset/SYMBOL)
(def predictor (atom nil))
(def TRADE-AMOUNT-BTC 0.005)
(def PROFIT-USD 40)
(def LOSS-USD 40)

(def keep-running? (atom true))
(def trading? (atom false))

(defn create-buy-params [{:keys [price]}]
  {"symbol" SYMBOL
   "side" "BUY"
   "type" "LIMIT"
   "timeInForce" "GTC"
   "quantity" TRADE-AMOUNT-BTC
   "price" price})

(defn create-take-profit-params [{:keys [price]}]
  {"symbol" SYMBOL
   "side" "SELL"
   "type" "LIMIT"
   "timeInForce" "GTC"
   "quantity" TRADE-AMOUNT-BTC
   "price" (+ price PROFIT-USD)})

(defn create-stop-loss-params [{:keys [price]}]
  {"symbol" SYMBOL
   "side" "SELL"
   "type" "LIMIT"
   "timeInForce" "GTC"
   "quantity" TRADE-AMOUNT-BTC
   "price" (- price LOSS-USD)})

(defn open-order! [asks]
  (log/debug "open-order! triggered")
  (let [opened-orders (bi/opened-orders! SYMBOL)]
    (when (empty? opened-orders)
      (log/debug "no open orders so we can trade")
      (reset! trading? true)
      (let [buy-price (dataset/find-trade-price asks)]
        (log/debug "trying to buy at " buy-price)
        (bi/open-order! (create-buy-params {:price buy-price}))
        (go-loop []
          (<! (timeout 3000))
          (let [opened-orders (bi/opened-orders! SYMBOL)]
            (cond
              (empty? opened-orders)
              (do
                (log/debug "we successfully bought, creating stop loss/profit orders")
                (bi/open-order! (create-take-profit-params {:price buy-price}))
                (bi/open-order! (create-stop-loss-params {:price buy-price}))
                (recur))

              (= (count opened-orders) 1)
              (do
                (log/debug "there is an open order, canceling it")
                (bi/cancel-order! SYMBOL (get-in opened-orders [0 "orderId"]))
                (reset! trading? false))

              :else
              (do
                (log/debug "we're waiting for stop profit or stop loss to be triggered")
                (recur)))))))))

(defn trade? [[buy sell wait]]
  (and (> (.getProbability buy) 2)
       (< (.getProbability sell) -1)
       (< (.getProbability wait) -1)
       (> (.getProbability wait) (.getProbability sell))))

(defn trade! [prediction asks]
  (when (trade? prediction)
    (open-order! asks)))

(defn load-model []
  (let [_memory-manager (NDManager/newBaseManager)
        model (train/load-model)
        classes (Arrays/asList (into-array ["buy" "sell" "wait"]))
        translator (.build
                    (doto (ImageClassificationTranslator/builder)
                      (.addTransform (ToTensor.))
                      (.optSynset classes)))]
    (reset! predictor (.newPredictor model translator))))

(defn get-prediction! [filepath]
  (.items (.predict @predictor (.fromFile (ImageFactory/getInstance) (Paths/get (.toURI (io/file filepath)))))))

(defn start-consumer! []
  (let [input (atom clojure.lang.PersistentQueue/EMPTY)
        input-filepath "./input.png"]
    (scheduler/start!
     3000
     (fn []
       (let [order-book (bi/depth! SYMBOL 1000)]
         (swap! input #(as-> % $
                         (conj $ (dataset/order-book->quantities-indexed-by-price-level dataset/PRICE-INTERVAL-FOR-INDEXING order-book))
                         (if (> (count $) dataset/INPUT-SIZE)
                           (pop $)
                           $)))
         (when (and (= (count @input) dataset/INPUT-SIZE)
                    (not @trading?))
           (let [image (du/->image {:data @input
                                    :max-value dataset/MAX-QUANTITY})]
             (i/save image input-filepath)
             (let [prediction (get-prediction! input-filepath)]
               (trade! prediction (:asks order-book)))))))
     keep-running?)))

(defn start! []
  (bi/init)
  (load-model)
  (start-consumer!))