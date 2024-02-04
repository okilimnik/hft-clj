(ns hft.trade
  (:require [clojure.core.async :refer [<!! chan sliding-buffer thread timeout]]
            [clojure.java.io :as io]
            [hft.dataset :as dataset]
            [hft.train :as train]
            [hft.binance :as api]
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
(def TRADE-AMOUNT-BTC 0.005)
(def PROFIT-USD 48)
(def LOSS-USD 48)

;; we want our order not to match any existent order
;; so it would be a `maker` order (less exchange fee + more profit by default)
#_(def ^:private MAKER-ORDER-SHIFT 1)
#_(defn- get-maker-price [price side]
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
  (log/debug "open-order! triggered")
  (let [opened-orders (api/opened-orders! SYMBOL)]
    (when (empty? opened-orders)
      (log/debug "no open orders so we can trade")
      (let [maker-price (-> (get-best-price side)
                            #_(get-maker-price side))]
        (log/debug "trying to buy at maker-price")
        (api/open-order! (create-buy-params {:price maker-price}))
        (loop []
          (<!! (timeout 3000))
          (let [opened-orders (api/opened-orders! SYMBOL)]
            (cond
              ;; buy limit order was triggered
              (empty? opened-orders)
              (do
                (log/debug "we successfully bought, creating stop loss/profit orders")
                (api/open-order! (create-take-profit-params {:price maker-price}))
                (api/open-order! (create-stop-loss-params {:price maker-price}))
                (recur))

              ;; stop loss or take-profit was triggered and only the counter-order left, so we close it
              (and (= (count opened-orders) 1)
                   (= (get-in opened-orders [0 "side"]) "SELL"))
              (do
                (log/debug "stop loss or take-profit was triggered and only the counter-order left, so we close it, and can analyse inputs again")
                (api/cancel-order! SYMBOL (get-in opened-orders [0 "orderId"])))

              ;; otherwise we're waiting for stop profit or stop loss to be triggered
              :else
              (do
                (log/debug "we're waiting for stop profit or stop loss to be triggered, we don't analyse inputs in this state, only 1 trade at a time")
                (recur)))))))))

(defn trade? [[buy wait]]
  (and (> (.getProbability buy) 4)
       (< (.getProbability wait) -3)))

(defn trade! [prediction]
  (when (trade? prediction)
    (open-order! :buy)))

(defn load-model []
  (let [_memory-manager (NDManager/newBaseManager)
        model (train/load-model)
        classes (Arrays/asList (into-array ["buy" "wait"]))
        translator (.build
                    (doto (ImageClassificationTranslator/builder)
                      (.addTransform (ToTensor.))
                      (.optSynset classes)))]
    (reset! predictor (.newPredictor model translator))))

(defn get-prediction! [filepath]
  (.items (.predict @predictor (.fromFile (ImageFactory/getInstance) (Paths/get (.toURI (io/file filepath)))))))

(defn start-consumer! []
  (reset! consuming-running? true)
  (thread
    (while @consuming-running?
      (let [snapshot (<!! input-chan)
            image nil #_(dataset/create-input-image (drop dataset/PREDICTION-HEAD snapshot))
            dir (io/file root)
            filename "input.png"
            filepath (str root "/" filename)]
        (when-not (.exists dir)
          (.mkdirs dir))
        (i/save image filepath)
        (let [prediction (get-prediction! filepath)]
          (log/debug prediction ": " prediction)
          (trade! prediction))))))

(defn start! []
  (api/init)
  (load-model)
  (start-consumer!))