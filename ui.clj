(ns ^{:skip-aot true} hft.ui
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [thread]]
            [io.github.humbleui.ui :as ui]
            [hft.ui.state :as state]
            [hft.dataset :as dataset]))


(def root
  (ui/default-theme
   {}
   (ui/dynamic _ctx [chart (:chart @state/*app)]
               (ui/center
                (if (and chart (.exists (io/file chart)))
                  (ui/column
                   (ui/image chart))
                  (ui/label "Warming..."))))))


(defn -main [& args]
  (thread
    (dataset/range-market-pipeline {:on-update state/update-chart}))
  (ui/start-app!
   (reset! state/*window
           (ui/window
            {:title "Humble 🐝 UI"
             :width 600
             :height 800}
            #'root)))
  (state/redraw!))


;; clj -M -m hft.ui