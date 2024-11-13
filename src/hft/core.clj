(ns hft.core
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [hft.dataset :refer [range-market-pipeline trend-market-pipeline]]
            [hft.model.lightgbm :as lightgbm]))

(def cli-options
  ;; An option with a required argument
  [["-p" "--port PORT" "Port number"
    :default 80
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-t" nil "Train"
    :id :train]
   ["-i" nil "UI"
    :id :ui]
   ;; A non-idempotent option (:default is applied first)
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :update-fn inc] ; Prior to 0.4.1, you would have to use:
                   ;; :assoc-fn (fn [m k _] (update-in m [k] inc))
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        market-state (or (System/getenv "MARKET_STATE") "range")]

    (cond
      (:train options) (do (lightgbm/train!)
                           (System/exit 0))
      :else
      (do
        (println "market state is: " market-state)
        (case (keyword market-state)
          :range (range-market-pipeline {})
          :trend (trend-market-pipeline)
          (prn (str "Handling " market-state " market is not implemented")))))))

