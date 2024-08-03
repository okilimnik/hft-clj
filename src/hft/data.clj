(ns hft.data
  (:require [clojure.java.io :as io]
            [mikera.image.core :as i]
            [hft.gcloud :refer [upload-file!]])
  (:import [java.awt Color]))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [{:keys [data max-value min-value] :or {min-value 0}}]
  (let [width (count data)
        height (count (:asks (first data)))
        image (i/new-image width height)
        pixels (i/get-pixels image)
        scale (/ 255.0 (- max-value min-value))]
    (dotimes [idx (* width height)]
      (let [height-idx (int (/ idx width))
            width-idx (mod idx width)
            b (-> (nth data width-idx)
                  :asks
                  (nth height-idx)
                  (min max-value))
            g (-> (nth data width-idx)
                  :bids
                  (nth height-idx)
                  (min max-value))]
        (aset pixels idx
              (.getRGB (Color.
                        (long 0)
                        ;; the image is reflected around the X axis, thus we need to interchange green and blue
                        (long (* scale b))
                        (long (* scale g)))))))
    (i/set-pixels image pixels)
    image))

(defn save-image [{:keys [image dir ui? metadata] :or {ui? false}}]
  (let [dir (io/file dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [timestamp (System/currentTimeMillis)
          image-filename (str timestamp ".png")
          image-filepath (str dir "/" image-filename)
          metadata-filename (str timestamp ".edn")
          metadata-filepath (str dir "/" metadata-filename)]
      (i/save image image-filepath)
      (when metadata
        (spit (str dir "/") metadata))
      (when-not ui?
        (upload-file! image-filename image-filepath)
        (io/delete-file image-filepath)
        (upload-file! metadata-filename metadata-filepath)
        (io/delete-file metadata-filepath))
      image-filepath)))