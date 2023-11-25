(ns hft.trade-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [hft.trade :as sut]))

(def non-trade-inputs [;"./dataset/00000001"
                       "./dataset/01000000"
                       "./dataset/00100000"
                       "./dataset/00010000"
                       "./dataset/00001000"
                       "./dataset/00000100"
                       ])

(deftest get-prediction-test
  (testing "These inputs shouldn't trigger trade"
    (sut/load-model)
    (doseq [folder non-trade-inputs]
      (doseq [f (file-seq (io/file folder))]
        (when-not (.isDirectory f)
          (let [[buy wait] (sut/get-prediction! (.getAbsolutePath f))]
            (is (= false (sut/trade? [buy wait])))))))))