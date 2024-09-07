(ns hft.model.lightgbm
  (:require [clojure.java.shell :refer [sh]]))

(defn train! []
  (prn (:out (sh "lightgbm" "config=lgbm.train"))))

(defn predict! []
  (prn (:out (sh "lightgbm" "config=lgbm.predict"))))