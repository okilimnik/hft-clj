(ns hft.image
  (:require [clojure.java.io :as io]
            [clojure.math :refer [ceil]]
            [clojure.string :as str])
  (:import [java.awt Color]
           [java.awt.image BufferedImage]
           [javax.imageio IIOImage ImageIO]
           [org.imgscalr Scalr Scalr$Rotation]))

(defn new-image
  (^java.awt.image.BufferedImage [width height]
   (new-image width height true))
  (^java.awt.image.BufferedImage [width height alpha?]
   (if alpha?
     (BufferedImage. (int width) (int height) BufferedImage/TYPE_INT_ARGB)
     (BufferedImage. (int width) (int height) BufferedImage/TYPE_INT_RGB))))

(defn get-pixels
  (^ints [^BufferedImage image]
   (.getDataElements (.getRaster image) 0 0 (.getWidth image) (.getHeight image) nil)))

(defn set-pixels
  ([^java.awt.image.BufferedImage image ^ints pixels]
   (.setDataElements (.getRaster image) 0 0 (.getWidth image) (.getHeight image) pixels)))

(defn write
  [^java.awt.image.BufferedImage image out format-name]
  (let [^javax.imageio.ImageWriter writer (.next (ImageIO/getImageWritersByFormatName format-name))
        ^javax.imageio.ImageWriteParam write-param (.getDefaultWriteParam writer)
        iioimage (IIOImage. image nil nil)]
    (with-open [io-stream (io/output-stream out)
                outstream (ImageIO/createImageOutputStream io-stream)]
      (doto writer
        (.setOutput outstream)
        (.write nil iioimage write-param)
        (.dispose)))))

(defn save
  [^java.awt.image.BufferedImage image path]
  (let [outfile (io/file path)
        ext (-> path (str/split #"\.") last str/lower-case)]
    (write image outfile ext)
    path))

(defn ->image
  "int[] pixels - 1-dimensional Java array, which length is width * height"
  [{:keys [data max-value min-value] :or {min-value 0}}]
  (let [width (count data)
        height (count (:asks (first data)))
        image (new-image width height)
        pixels (get-pixels image)
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
    (set-pixels image pixels)
    (Scalr/rotate image Scalr$Rotation/CW_270 nil)))

(defn save-image [{:keys [image dataset-dir folder]}]
  (let [dir (io/file dataset-dir folder)]
    (when-not (.exists dir)
      (.mkdirs dir))
    (let [timestamp (System/currentTimeMillis)
          image-filename (str timestamp ".png")
          image-filepath (str dir "/" image-filename)]
      (save image image-filepath)
      image-filepath)))