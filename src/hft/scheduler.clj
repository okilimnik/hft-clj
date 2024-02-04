(ns hft.scheduler 
  (:require [clojure.core.async :refer [thread]]))

(defn start! [interval f]
  (loop [scheduled-t (System/currentTimeMillis)]
    (let [current-t (System/currentTimeMillis)]
      (if (>= current-t scheduled-t)
        (do (f)
            (recur (+ scheduled-t interval)))
        (do (Thread/sleep (- scheduled-t current-t))
            (recur scheduled-t))))))