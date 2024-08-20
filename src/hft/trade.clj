(ns hft.trade)

(def opened-order (atom nil))

(defn trade! [analysis]
  (cond
    @opened-order '()
    (> (last (:max-bid-distance analysis)) 2) '()
    (> (last (:max-ask-distance analysis)) 2) '()))