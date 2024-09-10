(ns hft.model.lightgbm
  (:require [clojure.java.shell :refer [sh]]))

(defn train! []
  (prn (:out (sh "lightgbm" "task=train" "config=lgbm.conf" "data=lgbm.train" "valid=lgbm.test" "output_model=model.txt"))))

(defn predict! []
  (prn (:out (sh "lightgbm" "task=predict" "data=lgbm.predict" "input_model=model.txt" "output_result=prediction.txt"))))