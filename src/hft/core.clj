(ns hft.core
  (:gen-class)
  (:require [hft.dataset :as dataset]))

(defn -main [& args]
  (dataset/start!))