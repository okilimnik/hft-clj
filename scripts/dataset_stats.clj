(ns dataset-stats
  (:require [clojure.java.io :as io]))

(def categories #{"buy"
                  "sell"
                  "wait"})

(doseq [cat categories]
  (let [folder (str "./dataset/" cat)]
    (println cat " " (count (file-seq (io/file folder))))))

(comment "
 00010000   8204
 00001000   8005
 00000100   2370
 01000000   683
 10000000   324
 00100000   2615
 00000001   330
 00000010   587
         
         ")

;; clj scripts/dataset_stats.clj