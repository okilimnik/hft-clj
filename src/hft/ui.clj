(ns hft.ui
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [thread]]
            [io.github.humbleui.ui :as ui]
            [hft.ui.state :as state]
            [hft.dataset :as dataset]
            [hft.market.binance :as bi]))

(def root
  (ui/default-theme {}
                    (ui/dynamic _ctx [src (:input-image-src @state/*app)]
                                (ui/center
                                 (if (.exists (io/file src))
                                   (ui/image src)
                                   (ui/label "Warming..."))))))

(defn -main [& args]
  (bi/init)
  (thread
    (dataset/pipeline-v1 {:on-update state/update-input-image}))
  (ui/start-app!
   (reset! state/*window
           (ui/window
            {:title "Humble 🐝 UI"
             :width 300
             :height 300}
            #'root)))
  (state/redraw!))