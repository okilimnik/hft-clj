(ns hft.firebase
  (:require
   [clojure.java.io :as io])
  (:import
    [com.google.auth.oauth2 GoogleCredentials]
    [com.google.cloud.storage Bucket$BlobWriteOption]
    [com.google.firebase FirebaseApp FirebaseOptions$Builder]
    [com.google.firebase.cloud StorageClient]))

(def bucket (atom nil))

(defn init! []
  (let [options (.build
                 (doto (FirebaseOptions$Builder.)
                   (.setCredentials (GoogleCredentials/fromStream (io/input-stream (io/file "firebase.json"))))
                   (.setStorageBucket "neusa-a919b.appspot.com")))]
    (FirebaseApp/initializeApp options)
    (reset! bucket (.bucket (StorageClient/getInstance)))))

(defn upload-file! [filename filepath]
  (try
    (when-not @bucket (init!))
    (.create @bucket filename (io/input-stream (io/file filepath)) (into-array Bucket$BlobWriteOption []))
    (catch Exception e (prn e))))