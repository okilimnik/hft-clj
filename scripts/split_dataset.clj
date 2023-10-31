(ns split-dataset 
  (:require [clojure.java.io :as io]))

(def categories
  {"00010000"   8204
   "00001000"   8005
   "00000100"   2370
   "01000000"   683
   "10000000"   324
   "00100000"   2615
   "00000001"   330
   "00000010"   587})

(def folder "./dataset")

(let [quantity (apply min (vals categories))
      train-quantity (int (* 0.7 quantity))
      test-quantity (- quantity train-quantity)]
  (doseq [cat (keys categories)]
    (let [files (-> (io/file (str folder "/" cat))
                    file-seq
                    shuffle)
          train (take train-quantity files)
          test (->> files
                    (drop train-quantity)
                    (take test-quantity))]
      (doseq [file train]
        (when-not (.isDirectory file)
          (let [dest (str folder "/train/" cat)
                dest-folder (io/file dest)]
            (when-not (.exists dest-folder)
              (.mkdirs dest-folder))
            (io/copy file (io/file (str dest "/" (.getName file)))))))
      (doseq [file test]
        (when-not (.isDirectory file)
          (let [dest (str folder "/test/" cat)
                dest-folder (io/file dest)]
            (when-not (.exists dest-folder)
              (.mkdirs dest-folder))
            (io/copy file (io/file (str dest "/" (.getName file))))))))))

;; clj scripts/split_dataset.clj