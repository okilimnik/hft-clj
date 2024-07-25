(ns hft.data
  (:require [clojure.java.io :as io]
            [mikera.image.core :as i]
            [hft.gcloud :refer [upload-file!]]
            [clojure.string :as str])
  (:import [java.awt Color]))

(def image-counter (atom nil))
(def IMAGE-COUNTER-FILE "./image-counter.txt")

(defn- get-image-number! []
  (let [new-val (swap! image-counter inc)
        max-l 10]
    (spit IMAGE-COUNTER-FILE (str new-val))
    (let [s (str (str/join (repeat max-l "0")) new-val)
          l (count s)]
      (subs s (- l max-l) l))))

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

(defn save-image1 [{:keys [image dir filename ui?] :or {ui? false}}]
  (init-image-counter)
  (let [dir (io/file dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [indexed-filename (str (get-image-number!) "_" filename ".png")
          filepath (str dir "/" indexed-filename)]
      (i/save image filepath)
      (when-not ui?
        (upload-file! indexed-filename filepath)
        (io/delete-file filepath))
      filepath)))

(defn save-image [{:keys [image dir filename ui?] :or {ui? false}}]
  (let [dir (io/file dir)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [indexed-filename (str (System/currentTimeMillis) ".png")
          filepath (str dir "/" indexed-filename)]
      (i/save image filepath)
      (upload-file! indexed-filename filepath)
      (io/delete-file filepath)
      filepath)))