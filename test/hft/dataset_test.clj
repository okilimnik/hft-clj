(ns hft.dataset-test
  (:require [clojure.test :refer [deftest is]]
            [hft.dataset :as sut]))

(deftest event->readable-event-test
  (is (=

       {:lastUpdateId 160,
        :bids {"0.0024" {:qty 10.0}},
        :asks {"0.0026" {:qty 100.0}}}

       (sut/event->readable-event
        {:e "depthUpdate",
         :E 123456789,
         :s "BNBBTC",
         :U 157,
         :u 160,
         :b [["0.0024", "10"]],
         :a [["0.0026", "100"]]}))))

(deftest add-to-states-test 
  (is (=
       
       [{:lastUpdateId 1, 
         :bids {"0.0021" {:qty 9.0}
                "0.0024" {:qty 10.0}}, 
         :asks {"0.0026" {:qty 100.0}
                "0.0027" {:qty 90.0}}} 
        {:lastUpdateId 2, 
         :bids {"0.0021" {:qty 9.0}}, 
         :asks {"0.0026" {:qty 20.0}}}]

       (sut/add-to-states
        [{:lastUpdateId 1,
          :bids {"0.0021" {:qty 9.0}
                 "0.0024" {:qty 10.0}},
          :asks {"0.0026" {:qty 100.0}
                 "0.0027" {:qty 90.0}}}]
        {:lastUpdateId 2,
         :bids {"0.0024" {:qty 0.0}},
         :asks {"0.0026" {:qty 20.0}
                "0.0027" {:qty 0.0}}}))))

(deftest get-price-extremums-test
  (is (=

       ["0.002100" "0.002701"]
       
       (map sut/format-float
            (sut/get-price-extremums
             [{:lastUpdateId 1,
               :bids {"0.0021" {:qty 9.0}
                      "0.0024" {:qty 10.0}},
               :asks {"0.0026" {:qty 100.0}
                      "0.0027" {:qty 90.0}}}
              {:lastUpdateId 2,
               :bids {"0.0021" {:qty 9.0}},
               :asks {"0.0026" {:qty 20.0}}}])))))

(deftest remove-low-qty-test
  (is (=

       {"0.0030" {:qty 0.1
                  :price-level 2}
        "0.0031" {:qty 0.2
                  :price-level 2}}

       (sut/remove-low-qty
        {"0.0021" {:qty 0.1
                   :price-level 1}
         "0.0022" {:qty 0.1
                   :price-level 1}
         "0.0030" {:qty 0.1
                   :price-level 2}
         "0.0031" {:qty 0.2
                   :price-level 2}}
        {1 0.2
         2 0.3}
        0.3))))

(deftest calc-quantities-by-price-level-test
  (let [res (sut/calc-quantities-by-price-level
             {"0.0021" {:qty 0.1
                        :price-level 1}
              "0.0022" {:qty 0.1
                        :price-level 1}
              "0.0030" {:qty 0.1
                        :price-level 2}
              "0.0031" {:qty 0.2
                        :price-level 2}})
        formatted-res (reduce #(update %1 %2 sut/format-float) res (keys res))]

    (is (= {1 "0.200000" 
            2 "0.300000"} formatted-res))))

(deftest add-price-level-test 
  (is (=

       {"0.0017" {:qty 0.05, :price-level 0}, 
        "0.0021" {:qty 0.1, :price-level 13}, 
        "0.0022" {:qty 0.1, :price-level 16}, 
        "0.0030" {:qty 0.1, :price-level 43}, 
        "0.0031" {:qty 0.2, :price-level 46}, 
        "0.0035" {:qty 0.1, :price-level 59}}

       (let [min-price-in-map 0.0017
             max-price-in-map 0.0035]
         (sut/add-price-level
          {"0.0017" {:qty 0.05}
           "0.0021" {:qty 0.1}
           "0.0022" {:qty 0.1}
           "0.0030" {:qty 0.1}
           "0.0031" {:qty 0.2}
           "0.0035" {:qty 0.1}}
          min-price-in-map
          (/ (- (+ max-price-in-map sut/MAX-PRICE-INTERVAL-ADDITION)
                min-price-in-map) 
             sut/INPUT-SIZE))))))

(deftest calc-change-level-test 
  (with-redefs [sut/LEVEL-PRICE-CHANGE-PERCENT 0.04]
    (is (= 0 (sut/calc-change-level 28342.06 28335.01)))
    (is (= 1 (sut/calc-change-level 28335.01 28350.06)))
    (is (= 4 (sut/calc-change-level 28335.01 28385.06)))))

(deftest )