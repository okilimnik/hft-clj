(ns hft.gcloud
  (:require [clojure.java.io :as io])
  (:import [com.google.cloud.storage BlobId BlobInfo StorageOptions Storage$BlobWriteOption]))

(def storage (atom nil))
(def bucket-name "neusa-datasets")
(def folder (atom nil))

(defn init! []
  (reset! storage (.getService (StorageOptions/getDefaultInstance)))
  (reset! folder (str (System/currentTimeMillis))))

(defn upload-file! [filename filepath]
  (try
    (when-not @storage (init!))
    (let [blob-id (BlobId/of bucket-name (str "order_book2/" @folder "/" filename))
          blob-info (.build (BlobInfo/newBuilder blob-id))]
      (.createFrom @storage blob-info (io/input-stream (io/file filepath)) (into-array Storage$BlobWriteOption [])))
    (catch Exception e (prn e))))