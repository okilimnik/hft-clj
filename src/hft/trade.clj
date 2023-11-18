(ns hft.trade
  (:require [clojure.core.async :refer [<!! thread chan sliding-buffer]]
            [clojure.java.io :as io]
            [hft.dataset :as dataset]
            [hft.train :as train]
            [mikera.image.core :as i])
  (:import [ai.djl.modality.cv ImageFactory]
           [ai.djl.modality.cv.transform ToTensor]
           [ai.djl.modality.cv.translator ImageClassificationTranslator]
           [ai.djl.ndarray NDManager]
           [java.nio.file Paths]
           [java.util Arrays]))

(def root "./trade")
(def predictor (atom nil))
(def consuming-running? (atom false))
(def input-chan (chan (sliding-buffer 1)))

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
        (let [prediction (.predict @predictor (.fromFile (ImageFactory/getInstance) (Paths/get (.toURI (io/file filepath)))))]
          (prn-str prediction))))))

(defn start! []
  (load-model)
  (start-consumer!)
  ;; we want to continue dataset creation during trading
  (dataset/init-image-counter)
  (dataset/start-consumer!)
  (dataset/start-producer! [dataset/input-chan input-chan]))