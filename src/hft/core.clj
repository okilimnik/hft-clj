(ns hft.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [hft.dataset :as dataset]
            [hft.trade :as trade]))

(defn -main [& args]
  (dataset/start!))