(ns hft.image 
  (:require [clojure.java.io :as io]
            [clojure.string :as str]) 
  (:import [java.awt.image BufferedImage]
           [javax.imageio IIOImage ImageIO]))

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