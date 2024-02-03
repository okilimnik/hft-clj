(ns hft.data
  (:require [clojure.java.io :as io]
            [mikera.image.core :as i]
            [hft.gcloud :refer [upload-file!]])
  (:import [java.awt Color]))

(def image-counter (atom nil))
(def IMAGE-COUNTER-FILE "./image-counter.txt")

(defn- get-image-number! []
  (let [new-val (swap! image-counter inc)]
    (spit IMAGE-COUNTER-FILE (str new-val))
    new-val))

(defn- init-image-counter []
  (when-not @image-counter
    (let [init-val (parse-long
                    (try
                      (slurp IMAGE-COUNTER-FILE)
                      (catch Exception _
                        "0")))]
      (reset! image-counter init-val))))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [{:keys [data max-value min-value] :or {min-value 0}}]
  (let [width (count data)
        height (count (first data))
        image (i/new-image width height)
        pixels (i/get-pixels image)
        scale (/ 255.0 (- max-value min-value))]
    (dotimes [idx (* width height)]
      (let [width-idx (/ idx width)
            height-idx (mod idx width)
            color (* scale (get-in data [width-idx height-idx]))]
        (aset pixels idx
              (.getRGB (Color.
                        (long color)
                        (long color)
                        (long color))))))
    (i/set-pixels image pixels)
    image))

(defn save-image [{:keys [image dir filename local?] :or {local? false}}]
  (init-image-counter)
  (let [dir (io/file dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [indexed-filename (str filename "_" (get-image-number!) ".png")
          filepath (str dir "/" indexed-filename)]
      (i/save image filepath)
      (when-not local?
        (upload-file! indexed-filename filepath)
        (io/delete-file filepath)))))