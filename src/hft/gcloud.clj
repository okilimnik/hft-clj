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

(defn upload-file! [filename filepath]
  (when-not @token (init!))
  (storage/upload! filepath (str "order_book_06_08_2024/" filename) (get-mime-type filename) @token))