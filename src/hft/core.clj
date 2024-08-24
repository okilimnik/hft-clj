(ns hft.core
  (:gen-class)
  (:require [hft.dataset :refer [pipeline-v1]]))

(defn -main [& _args]
  (pipeline-v1))