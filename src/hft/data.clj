(ns hft.data
  (:require [clojure.java.io :as io]
            [clojure.math :refer [ceil]]
            [mikera.image.core :as i]
            [hft.gcloud :refer [upload-file!]])
  (:import [java.awt Color]
           [org.imgscalr Scalr Scalr$Rotation]))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [{:keys [data max-value min-value] :or {min-value 0}}]
  (let [width (count data)
        height (count (:asks (first data)))
        image (i/new-image width height)
        pixels (i/get-pixels image)
        blues (flatten (map :asks data))
        greens (flatten (map :bids data))
        max-value (ceil (max max-value (apply max blues) (apply max greens)))
        scale (/ 255.0 (- max-value min-value))]
    (doseq [i (range (* width height))]
      (aset pixels i
            (.getRGB (Color.
                      (long 0)
                      (long (* scale (nth greens i)))
                      (long (* scale (nth blues i)))))))
    (i/set-pixels image pixels)
    (Scalr/rotate image Scalr$Rotation/CW_270 nil)))

(defn save-image [{:keys [image dir ui? folder metadata] :or {ui? false}}]
  (let [dir (io/file dir folder)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [timestamp (System/currentTimeMillis)
          image-filename (str timestamp ".png")
          image-filepath (str dir "/" folder "/"image-filename)
          metadata-filename (str timestamp ".edn")
          metadata-filepath (str dir "/" folder "/" metadata-filename)]
      (i/save image image-filepath :quality 1.0)
      (when metadata
        (spit metadata-filepath metadata))
      (when-not ui?
        (upload-file! (str folder "/" image-filename) image-filepath)
        (io/delete-file image-filepath)
        (upload-file! (str folder "/" metadata-filename) metadata-filepath)
        (io/delete-file metadata-filepath))
      image-filepath)))