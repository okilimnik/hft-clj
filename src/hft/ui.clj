(ns hft.ui
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [thread]]
            [membrane.ui :as ui]
            [membrane.java2d :as backend]
            [membrane.component :as component
             :refer [defui]]
            [hft.dataset :as dataset]))

(def state-atm
  (atom
   {:src nil :label "None"}))

(comment


  (defui root [{:keys [label src]}]
    (ui/center
     (if (.exists (io/file src))
       (ui/vertical-layout
        (ui/label label)
        (ui/image src))
       (ui/label "Warming..."))))



  (defn -main [& args]
    (thread
      (dataset/pipeline-v1 {:on-update #(reset! state-atm %)
                            :ui? true}))
    #_(backend/run #(ui/label "Hello World!"))
    (backend/run (component/make-app #'root {:x false :y true :z false})))
  )