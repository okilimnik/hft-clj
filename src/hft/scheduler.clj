(ns hft.scheduler)

(defn start! [interval f keep-running?]
  (loop [scheduled-t (System/currentTimeMillis)]
    (when @keep-running?
      (let [current-t (System/currentTimeMillis)]
        (if (>= current-t scheduled-t)
          (do (f)
              (recur (+ scheduled-t interval)))
          (do (Thread/sleep ^long (- scheduled-t current-t))
              (recur scheduled-t)))))))