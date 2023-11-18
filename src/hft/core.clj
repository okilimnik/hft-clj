(ns hft.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [hft.api :as binance]
            [hft.dataset :as dataset]
            [hft.train :as train]
            [hft.trade :as trade]))

(def cli-options
  [["-d" "--dataset" "Prepare dataset"
    :id :dataset]
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
      (:dataset options) (dataset/prepare!)
      (:network options) (train/start!)
      (:trade options) (trade/start!)
      :else (println "No command provided, exiting..."))))