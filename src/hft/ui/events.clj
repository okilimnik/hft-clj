(ns hft.ui.events 
  (:require
   [hft.ui.state :refer [*app redraw!]]
   [hft.xtdb :as d]))

(defn update-order-book [{:keys [symbol limit]}]
  (let [order-book (->> (d/q `{:find [(pull ?order-book [*
                                                         {:order-book/bids [*]
                                                          :order-book/asks [*]}])]
                               :in [?symbol]
                               :where [[?order-book :order-book/symbol ?symbol]
                                       [?order-book :order-book/timestamp ?timestamp]]
                               :order-by [[?timestamp :desc]]
                               :limit ~limit}
                             symbol)
                        (mapv first))]
    (swap! *app assoc :order-book order-book)
    (redraw!)))