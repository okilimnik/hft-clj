(ns transform-dataset 
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def categories #{"buy"
                  "sell"
                  "wait"})

(def folder (io/file "./dataset/order_book30"))
(def files (file-seq folder))

(defn move-file [source-file dest-path]
  (io/copy source-file (io/file dest-path))
  #_(io/delete-file source-file))

(doseq [file files]
  (let [filename (.getName file)
        category (-> (str/split filename #"_")
                     second
                     (str/split #"\.")
                     first)
        dest-folder (io/file (str "./dataset/" category))]
    (when (contains? categories category)
      (when-not (.exists dest-folder)
        (.mkdirs dest-folder))
      (move-file file (str "./dataset/" category "/" filename))
      #_(io/delete-file file))))

;; clj scripts/transform_dataset.clj