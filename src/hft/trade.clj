(ns hft.trade
  (:require [clojure.core.async :refer [<!! chan sliding-buffer thread timeout go-loop <!]]
            [clojure.java.io :as io]
            [hft.dataset :as dataset]
            [hft.train :as train]
            [hft.api :as api]
            [mikera.image.core :as i]
            [taoensso.timbre :as log])
  (:import [ai.djl.modality.cv ImageFactory]
           [ai.djl.modality.cv.transform ToTensor]
           [ai.djl.modality.cv.translator ImageClassificationTranslator]
           [ai.djl.ndarray NDManager]
           [java.nio.file Paths]
           [java.util Arrays]))

(def SYMBOL dataset/SYMBOL)
(def root "./trade")
(def predictor (atom nil))
(def consuming-running? (atom false))
(def input-chan (chan (sliding-buffer 1)))
(def THRESHOLD 0.96)
(def TRADE-AMOUNT-USD 500)
(def TRADE-AMOUNT-BTC 0.01)
(def PROFIT-USD 40)
(def LOSS-USD 40)

;; we want our order not to match any existent order
;; so it would be a `maker` order (less exchange fee + more profit by default)
(def ^:private MAKER-ORDER-SHIFT 1)

(defn stop-consuming []
  (reset! consuming-running? false))

(defn- get-maker-price [price side]
  ((case side :buy - +) price MAKER-ORDER-SHIFT))

(defn- get-best-price [side]
  (let [best-prices (api/best-price! SYMBOL)]
    (-> (case side :buy :askPrice :bidPrice)
        best-prices
        parse-double)))

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

(defn open-order! [side]
  (let [opened-orders (api/opened-orders! SYMBOL)]
    (when (empty? opened-orders)
      (let [maker-price (-> (get-best-price side)
                            (get-maker-price side))]
        (api/open-order! (create-buy-params {:price maker-price}))
        (loop []
          (<!! (timeout 3000))
          (let [opened-orders (api/opened-orders! SYMBOL)]
            (cond
              ;; buy limit order was triggered
              (empty? opened-orders)
              (do (api/open-order! (create-take-profit-params {:price maker-price}))
                  (api/open-order! (create-stop-loss-params {:price maker-price}))
                  (recur))

              ;; stop loss or take-profit was triggered and only the counter-order left, so we close it
              (and (= (count opened-orders) 1)
                   (= (get-in opened-orders [0 "side"]) "SELL"))
              (api/cancel-order! SYMBOL (get-in opened-orders [0 "orderId"]))

              ;; otherwise we're waiting for stop profit or stop loss to be triggered
              :else
              (recur))))))))

(defn trade! [category probability]
  (when (= category "00000001") ;; buy
    (when (> probability THRESHOLD)
      (open-order! :buy))))

(defn load-model []
  (let [_memory-manager (NDManager/newBaseManager)
        model (train/load-model)
        classes (Arrays/asList (into-array ["10000000" "00000001"]))
        translator (.build
                    (doto (ImageClassificationTranslator/builder)
                      (.addTransform (ToTensor.))
                      (.optSynset classes)
                      (.optApplySoftmax true)))]
    (reset! predictor (.newPredictor model translator))))

(defn start-consumer! []
  (reset! consuming-running? true)
  (thread
    (while @consuming-running?
      (let [snapshot (<!! input-chan)
            image (dataset/create-input-image (drop dataset/PREDICTION-HEAD snapshot))
            dir (io/file root)
            filename "input.png"
            filepath (str root "/" filename)]
        (when-not (.exists dir)
          (.mkdirs dir))
        (i/save image filepath)
        (let [prediction (.best (.predict @predictor (.fromFile (ImageFactory/getInstance) (Paths/get (.toURI (io/file filepath))))))
              category (.getClassName prediction)
              probability (.getProbability prediction)]
          (log/debug category ": " probability)
          (trade! category probability))))))

;(stop-consuming)

(defn start! []
  (api/init)
  (load-model)
  (start-consumer!)
  ;; we want to continue dataset creation during trading
  ;(dataset/init-image-counter)
  ;(dataset/start-consumer!)
  (dataset/start-producer! [;dataset/input-chan 
                            input-chan]))