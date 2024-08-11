(ns hft.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [hft.dataset :as dataset]
            [hft.ui :as ui]))

(set! *warn-on-reflection* true)

(def cli-options
  [["-d" "--dataset" "Prepare dataset"
    :id :dataset]
   ["-i" "--ui" "Run UI"
    :id :ui]
   ["-n" "--network" "Train network"
    :id :network]
   ["-t" "--trade" "Trade"
    :id :trade]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options _arguments errors _summary]} (parse-opts args cli-options)]
    (cond
      errors (println (error-msg errors))
      (:dataset options) (dataset/prepare! :binance)
      (:ui options) (ui/start!)
      :else (println "No command provided, exiting..."))))