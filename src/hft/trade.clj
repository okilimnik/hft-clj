(ns hft.trade
  (:require [clojure.java.io :as io]
            [hft.dataset :as dataset]
            [hft.train :as train]
            [juxt.dirwatch :refer [watch-dir]]) 
  (:import [ai.djl.ndarray NDManager]))

(def model (atom nil))

(defn load-model []
  (let [_memory-manager (NDManager/newBaseManager)]
    (reset! model (train/load-model))))

(defn trade! [data]
  (prn-str data))

(defn start! []
  (load-model)
  (watch-dir trade! (io/file "./trade"))
  (dataset/prepare!))