(ns hft.gcloud
  (:require [fire.auth :as auth]
            [fire.storage :as storage]))

(def token (atom nil))

(defn init! []
  (reset! token (auth/create-token "GOOGLE_APPLICATION_CREDENTIALS")))

(defn upload-file! [f]
  (when-not @token (init!))
  (try
    (storage/upload! (str "2708_0953/" (.getName f)) (.getAbsolutePath f) "text/plain" @token)
    (catch Exception _
      (init!)
      (upload-file! f))))