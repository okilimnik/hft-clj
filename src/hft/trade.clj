(ns hft.trade
  (:require [clojure.core.async :refer [thread <!!]]
            [clojure.java.io :as io]
            [mikera.image.core :as i]
            [hft.dataset :as dataset]
            [hft.train :as train]) 
  (:import [ai.djl.ndarray NDManager]))

(def model (atom nil))

(defn load-model []
  (let [_memory-manager (NDManager/newBaseManager)]
    (reset! model (train/load-model))))

(defn start-consumer! []
  (thread
    (let [snapshot (<!! dataset/input-chan)
          image (dataset/create-input-image (drop dataset/PREDICTION-HEAD snapshot))
          dir (io/file "./trade")
          filename "input.png"
          filepath (str "./trade/" filename)]
      (when-not (.exists dir)
        (.mkdirs dir))
      (i/save image filepath))))

(defn start! []
  (load-model)
  (start-consumer!)
  (dataset/start-producer!))