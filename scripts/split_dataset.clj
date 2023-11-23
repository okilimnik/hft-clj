(ns split-dataset 
  (:require [clojure.java.io :as io]))

(def target-category "00000001")
(def total-total (atom 0))

(def categories #{;"00000001"
                  "10000000"
                  "01000000"
                  "00100000"
                  "00010000"
                  "00001000"
                  "00000100"
                  ;"00000010"
                  })

(def folder "./dataset")

;; prepare wait category
(let [get-train-quantity (fn [total] (int (* 0.7 total)))
      get-test-quantity (fn [total train-quantity] (- total train-quantity))]
  (doseq [cat categories]
    (let [files (-> (io/file (str folder "/" cat))
                    file-seq
                    shuffle)
          total (count files)
          _ (swap! total-total + total)
          train-quantity (get-train-quantity total)
          test-quantity (get-test-quantity total train-quantity)
          train (take train-quantity files)
          test (->> files
                    (drop train-quantity)
                    (take test-quantity))
          updated-category "wait"]
      (doseq [file train]
        (when-not (.isDirectory file)
          (let [dest (str folder "/train/" updated-category)
                dest-folder (io/file dest)]
            (when-not (.exists dest-folder)
              (.mkdirs dest-folder))
            (io/copy file (io/file (str dest "/" (.getName file)))))))
      (doseq [file test]
        (when-not (.isDirectory file)
          (let [dest (str folder "/test/" updated-category)
                dest-folder (io/file dest)]
            (when-not (.exists dest-folder)
              (.mkdirs dest-folder))
            (io/copy file (io/file (str dest "/" (.getName file))))))))))

;; prepare buy category
(let [get-train-quantity (fn [total] (int (* 0.7 total)))
      get-test-quantity (fn [total train-quantity] (- total train-quantity))
      cat target-category
      files (-> (io/file (str folder "/" cat))
                file-seq
                shuffle)
      total (count files)]
  (dotimes [i (inc (/ @total-total total))]
    (let [train-quantity (get-train-quantity total)
          test-quantity (get-test-quantity total train-quantity)
          train (take train-quantity files)
          test (->> files
                    (drop train-quantity)
                    (take test-quantity))
          updated-category "buy"]
      (doseq [file train]
        (when-not (.isDirectory file)
          (let [dest (str folder "/train/" updated-category)
                dest-folder (io/file dest)]
            (when-not (.exists dest-folder)
              (.mkdirs dest-folder))
            (io/copy file (io/file (str dest "/" i "__" (.getName file)))))))
      (doseq [file test]
        (when-not (.isDirectory file)
          (let [dest (str folder "/test/" updated-category)
                dest-folder (io/file dest)]
            (when-not (.exists dest-folder)
              (.mkdirs dest-folder))
            (io/copy file (io/file (str dest "/" i "__" (.getName file))))))))))



;; clj scripts/split_dataset.clj