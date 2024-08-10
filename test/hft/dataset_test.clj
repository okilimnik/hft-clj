(ns hft.dataset-test
  (:require [clojure.test :refer [deftest is]]
            [hft.dataset :as sut]))

(deftest sort-levels-by-qty-test
  (is (= [6 7 8] (sut/get-levels-with-max-qty-sorted [0 0 0 0 0 0 0.5 0.4 0.4 0.3]))))