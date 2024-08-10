(ns hft.gcloud
  (:require [clojure.string :as str]
            [fire.auth :as auth]
            [fire.storage :as storage]))

(def token (atom nil))

(defn init! []
  (reset! token (auth/create-token "GOOGLE_APPLICATION_CREDENTIALS")))

(defn get-mime-type [filename]
  (cond
    (str/ends-with? ".edn" filename) "text/plain"
    (str/ends-with? ".png" filename) "image/png"))

(defn upload-file! [filepath folder filename]
  (when-not @token (init!))
  (storage/upload! (str "order_book_08_08_2024/" folder "/" filename) filepath (get-mime-type filename) @token))