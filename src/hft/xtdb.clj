(ns hft.xtdb
  (:require [xtdb.api :as xt]))

(def node (atom nil))

(defn init []
  (reset! node (xt/start-node {:xtdb/document-store {:xtdb/module 'xtdb.google.cloud-storage/->document-store
                                                     :project-id "neusa-a919b",
                                                     :bucket "neusa-datasets",
                                                     :prefix "xtdb/"}
                               :xtdb/index-store {:kv-store {:checkpointer {:store {:xtdb/module 'xtdb.google.cloud-storage/->checkpoint-store
                                                                                    :project-id "neusa-a919b",
                                                                                    :bucket "neusa-datasets",
                                                                                    :prefix "xtdb-indices/"}}}}})))

(defn put! [& docs]
  (when-not @node (init))
  (xt/submit-tx @node (for [doc docs]
                        [:xtdb.api/put doc]))
  (xt/sync @node))