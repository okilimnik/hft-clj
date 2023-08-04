(ns hft.core
  (:gen-class)
  (:require
   [hft.api]
   [hft.algo :as algo]
   [hft.dataset :as dataset]))

(defn -main [& args]
  (dataset/-main))