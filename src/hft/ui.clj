(ns ^{:skip-aot true} hft.ui
  (:require
   [clojure.core.async :refer [<!! thread timeout]]
   [clojure.java.io :as io]
   [hft.dataset :refer [FETCH-INTERVAL-SECONDS INPUT-SIZE]]
   [hft.strategy :refer [SYMBOL]]
   [hft.ui.events :refer [update-order-book]]
   [hft.ui.state :as state]
   [io.github.humbleui.ui :as ui]))


(def root
  (ui/default-theme
   {}
   (ui/dynamic _ctx [chart (:chart @state/*app)]
               (ui/center
                (if (and chart (.exists (io/file chart)))
                  (ui/column
                   (ui/image chart))
                  (ui/label "Warming..."))))))

(defn fetch-data! []
  (thread
    (loop []
      (update-order-book {:symbol SYMBOL
                          :limit INPUT-SIZE})
      (<!! (timeout (* FETCH-INTERVAL-SECONDS 1000)))
      (recur))))

(defn -main [& args]
  (fetch-data!)
  (ui/start-app!
   (reset! state/*window
           (ui/window
            {:title "Humble 🐝 UI"
             :width 600
             :height 800}
            #'root)))
  (state/redraw!))


;; clj -M -m hft.ui