(ns hft.scheduler
  (:require
   [clojurewerkz.quartzite.scheduler :as qs]
   [clojurewerkz.quartzite.triggers :as t]
   [clojurewerkz.quartzite.jobs :refer [defjob] :as j]
   [clojurewerkz.quartzite.schedule.cron :refer [schedule cron-schedule]]))
(comment
  (def scheduler-instance (atom nil))
  

  (defn init []
    (reset! scheduler-instance (-> (qs/initialize) qs/start)))
  

  (defprotocol Stateful
    (start [this] "Method to start")
    (stop [this] "Method to stop"))
  

  (defrecord Job [id job trigger]
    Stateful
    (start [this] (qs/schedule @scheduler-instance (:job this) (:trigger this)))
    (stop [this] (qs/pause-job @scheduler-instance (str "triggers." (:id this)))))
  

  (defn make-job [f cron-expr]
    (when-not @scheduler-instance (init))
    (let [id (random-uuid)
          job (j/build
               (j/of-type f)
               (j/with-identity (j/key (str "jobs." id))))
          trigger-key (str "triggers." id)
          trigger (t/build
                   (t/with-identity (t/key trigger-key))
                   (t/start-now)
                   (t/with-schedule (schedule
                                     (cron-schedule cron-expr))))]
      (doto (Job. id job trigger)
        (start))))
  )