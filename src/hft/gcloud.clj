(ns hft.gcloud
  (:require [fire.auth :as auth]
            [fire.storage :as storage]))

(def token (atom nil))
(def retries 1)

(defn init! []
  (reset! token (auth/create-token "GOOGLE_APPLICATION_CREDENTIALS")))

(defn upload-file! [f & {:keys [retry] :or {retry 0}}]
  (when-not @token (init!))
  (try
    (when (<= retry retries)
      (storage/upload! (str "0309_2249/" (.getName f)) (.getAbsolutePath f) "text/plain" @token))
    (catch Exception e
      (prn e)
      (init!)
      (upload-file! f :retry (inc retry)))))