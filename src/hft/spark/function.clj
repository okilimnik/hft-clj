(ns hft.spark.function
  (:import
   [scala Tuple2]))
  
(gen-class
 :name hft.spark.function.PairFunction
 :implements [org.apache.spark.api.java.function.PairFunction]
 :init init
 :state state
 :constructors {[clojure.lang.IFn] []}
 :prefix "pair-")

(defn pair-init [f]
  [[] f])

(defn pair-call [this v]
  (let [[v1 v2] ((.state this) v)]
    (Tuple2. v1 v2)))

(gen-class
 :name hft.spark.function.DoubleFunction
 :implements [org.apache.spark.api.java.function.DoubleFunction]
 :init init
 :state state
 :constructors {[clojure.lang.IFn] []}
 :prefix "double-")

(defn double-init [f]
  [[] f])

(defn double-call [this v]
  ((.state this) [(._1 v) (._2 v)]))