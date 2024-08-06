(ns hft.image 
  (:require [clojure.java.io :as io]
            [clojure.string :as str]) 
  (:import [java.awt.image BufferedImage]
           [javax.imageio IIOImage ImageIO]))

(defn new-image
  "Creates a new BufferedImage with the specified width and height.
   Uses BufferedImage/TYPE_INT_ARGB format by default,
   but also supports BufferedImage/TYPE_INT_RGB when alpha channel is not needed."
  (^java.awt.image.BufferedImage [width height]
   (new-image width height true))
  (^java.awt.image.BufferedImage [width height alpha?]
   (if alpha?
     (BufferedImage. (int width) (int height) BufferedImage/TYPE_INT_ARGB)
     (BufferedImage. (int width) (int height) BufferedImage/TYPE_INT_RGB))))

(defn get-pixels
  "Gets the pixels in a BufferedImage as a primitive int[] array.
   This is often an efficient format for manipulating an image."
  (^ints [^BufferedImage image]
   (.getDataElements (.getRaster image) 0 0 (.getWidth image) (.getHeight image) nil)))

(defn set-pixels
  "Sets the pixels in a BufferedImage using a primitive int[] array.
   This is often an efficient format for manipulating an image."
  ([^java.awt.image.BufferedImage image ^ints pixels]
   (.setDataElements (.getRaster image) 0 0 (.getWidth image) (.getHeight image) pixels)))


(defn write
  "Writes an image externally.

  `out` will be coerced to a `java.io.OutputStream` as per `clojure.java.io/output-stream`.
  `format-name` determines the format of the written file. See
  [ImageIO/getImageWritersByFormatName](https://docs.oracle.com/javase/7/docs/api/javax/imageio/ImageIO.html#getImageWritersByFormatName(java.lang.String))

  Accepts optional keyword arguments.

  `:quality` - decimal, between 0.0 and 1.0. Defaults to 0.8.

  `:progressive` - boolean, `true` turns progressive encoding on, `false`
  turns it off. Defaults to the default value in the ImageIO API -
  `ImageWriteParam/MODE_COPY_FROM_METADATA`. See
  [Java docs](http://docs.oracle.com/javase/7/docs/api/javax/imageio/ImageWriteParam.html).

  Examples:

    (write image (clojure.java.io/resource \"my/image.png\") \"png\" :quality 1.0)
    (write image my-output-stream \"jpg\" :progressive false)
    (write image \"/path/to/new/image/jpg\" \"jpg\" :quality 0.7 :progressive true)

  "
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
  "Stores an image to disk.

  See the documentation of `mikera.image.core/write` for optional arguments.

  Examples:

    (save image \"/path/to/new/image.jpg\" :quality 1.0)
    (save image \"/path/to/new/image/jpg\" :progressive false)
    (save image \"/path/to/new/image/jpg\" :quality 0.7 :progressive true)

  Returns the path to the saved image when saved successfully."
  [^java.awt.image.BufferedImage image path & {:keys [quality progressive]
                                               :or {quality 0.8
                                                    progressive nil}
                                               :as opts}]
  (let [outfile (io/file path)
        ext (-> path (str/split #"\.") last str/lower-case)]
    (apply write image outfile ext opts)
    path))