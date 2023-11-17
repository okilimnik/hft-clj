(ns split-dataset 
  (:require [clojure.java.io :as io]))

(def categories #{"10000000"
                 ; "01000000"
                 ; "00100000"
                 ; "00010000"
                 ; "00001000"
                 ; "00000100"
                 ; "00000010"
                  "00000001"})

(def folder "./dataset")

(let [get-train-quantity (fn [total] (int (* 0.7 total)))
      get-test-quantity (fn [total train-quantity] (- total train-quantity))]
  (doseq [cat categories]
    (let [files (-> (io/file (str folder "/" cat))
                    file-seq
                    shuffle)
          total (count files)
          train-quantity (get-train-quantity total)
          test-quantity (get-test-quantity total train-quantity)
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