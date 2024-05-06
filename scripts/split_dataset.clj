(ns split-dataset
  (:require [clojure.java.io :as io]))

(def categories #{"buy" "sell" "wait"})

(def folder "./dataset")

(doseq [cat categories]
  (let [files (-> (io/file (str folder "/" cat))
                  file-seq
                  shuffle)
        total (count files)
        train-quantity 2325
        test-quantity 775
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
          (io/copy file (io/file (str dest "/" (.getName file)))))))))

;; clj scripts/split_dataset.clj