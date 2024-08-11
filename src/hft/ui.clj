(ns hft.ui
  #_(:require [clojure.java.io :as io]
            [clojure.core.async :refer [thread]]
            [membrane.ui :as ui]
            [membrane.java2d :as backend]
            [hft.dataset :as dataset]))

#_(def state-atm
  (atom
   {:src "file" :label "None"}))

#_(defn root [{:keys [label src]}]
  (if (.exists (io/file src))
    (ui/vertical-layout
     (ui/label label)
     (ui/image src))
    (ui/label "Warming...")))

(defn start! []
  #_(thread
    (dataset/pipeline-v1 {:on-update #(reset! state-atm %)
                          :ui? true}))
  #_(backend/run #(root @state-atm)))
  