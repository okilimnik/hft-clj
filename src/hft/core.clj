(ns hft.core
  (:gen-class)
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [hft.api :as binance]
   [hft.dataset :as dataset]))

(def cli-options
  [["-d" "--dataset" "Prepare dataset"
    :id :dataset]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options _arguments errors _summary]} (parse-opts args cli-options)]
    (cond
      errors (println (error-msg errors))
      (:dataset options) (do (binance/init)
                             (dataset/prepare!))
      :else (println "No command provided, exiting..."))))