(ns hft.ui
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [thread]]
            [membrane.ui :as ui]
            [membrane.java2d :as backend]
            [hft.dataset :as dataset]))

(def state-atm
  (atom
   {:src "file" :label "None"}))

(defn root [{:keys [label src]}]
  (if (.exists (io/file src))
    (ui/vertical-layout
     (ui/label label)
     (ui/image src))
    (ui/label "Warming...")))

(defn start! []
  (thread
    (dataset/pipeline-v1 {:on-update #(reset! state-atm %)
                          :ui? true}))
  (backend/run #(root @state-atm)))
  