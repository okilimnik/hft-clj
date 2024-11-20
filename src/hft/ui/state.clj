(ns ^{:clojure.tools.namespace.repl/load false :skip-aot true} hft.ui.state
  (:require [io.github.humbleui.window :as window]))


(def initial-app-state
  {:chart nil})


(def *window
  "State of the main window. Gets set on app startup."
  (atom nil))


(defonce *app
  (atom initial-app-state))


(defn redraw!
  "Redraws the window with the current app state."
  []
  ;; we redraw only when window state has been set.
  ;; this lets us call the function on ns eval and will only
  ;; redraw if the window has already been created in either
  ;; user/-main or the app -main
  (some-> *window deref window/request-frame))


(defn update-chart [{:keys [chart]}]
  (swap! *app assoc :chart chart)
  (redraw!))