(ns hft.model.lightgbm
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn ->csv [row]
  (->> (str/split row #" ")
       (str/join ",")))

(defn prepare! []
  (let [data (->> (file-seq (io/file "dataset"))
                  (remove #(.isDirectory %))
                  (mapcat (comp #(str/split % #"\n") slurp))
                  shuffle
                  vec)
        total (count data)
        vailid-count (int (* 0.75 total))
        valid-data (take vailid-count data)
        test-data (drop vailid-count data)]
    (doseq [row valid-data]
      (spit "lgbm.train" (str (->csv row) "\n") :append true))
    (doseq [row test-data]
      (spit "lgbm.test" (str (->csv row) "\n") :append true))))

(defn train! []
  (when-not (.exists (io/file "lgbm.train"))
    (prepare!))
  
  ;; lightgbm config=lgbm.conf data=lgbm.train valid=lgbm.test output_model=model.txt
  (prn (:out (sh "lightgbm" "config=lgbm.conf" "data=lgbm.train" "valid=lgbm.test" "output_model=model.txt"))))

(defn predict! []
  (prn (:out (sh "lightgbm" "task=predict" "data=lgbm.predict" "input_model=model.txt" "output_result=prediction.txt"))))