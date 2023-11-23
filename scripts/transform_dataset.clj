(ns transform-dataset 
  (:require [clojure.java.io :as io]))

(def categories #{"10000000"
                  "01000000"
                  "00100000"
                  "00010000"
                  "00001000"
                  "00000100"
                  "00000010"
                  "00000001"})

(def folder (io/file "./dataset/order_book10"))
(def files (file-seq folder))

(defn move-file [source-file dest-path]
  (io/copy source-file (io/file dest-path))
  #_(io/delete-file source-file))

(doseq [file files]
  (let [filename (.getName file)
        category (subs filename 0 8)
        dest-folder (io/file (str "./dataset/" category))]
    (when (contains? categories category)
      (when-not (.exists dest-folder)
        (.mkdirs dest-folder))
      (move-file file (str "./dataset/" category "/" filename))
      #_(io/delete-file file))))

;; clj scripts/transform_dataset.clj