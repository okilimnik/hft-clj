(ns hft.data
  (:require [clojure.java.io :as io]
            [mikera.image.core :as i]
            [hft.gcloud :refer [upload-file!]])
  (:import [java.awt Color]))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [{:keys [data max-value min-value] :or {min-value 0}}]
  (let [width (count data)
        height (count (:b (first data)))
        image (i/new-image width height)
        pixels (i/get-pixels image)
        scale (/ 255.0 (- max-value min-value))]
    (dotimes [idx (* width height)]
      (let [height-idx (int (/ idx width))
            width-idx (mod idx width)
            b (-> (nth data width-idx)
                  :b
                  (nth height-idx)
                  (min max-value))
            g (-> (nth data width-idx)
                  :g
                  (nth height-idx)
                  (min max-value))]
        (aset pixels idx
              (.getRGB (Color.
                        (long 0)
                        (long (* scale g))
                        (long (* scale b)))))))
    (i/set-pixels image pixels)
    image))

(defn save-image [{:keys [image dir filename ui?] :or {ui? false}}]
  (let [dir (io/file dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [indexed-filename (str (System/currentTimeMillis) ".png")
          filepath (str dir "/" indexed-filename)]
      (i/save image filepath)
      (when-not ui?
        (upload-file! indexed-filename filepath)
        (io/delete-file filepath))
      filepath)))