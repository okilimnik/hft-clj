(ns hft.algo
  (:require
   [hft.spark.function])
  (:import
   [hft.spark.function PairFunction DoubleFunction]
   [org.apache.spark SparkConf]
   [org.apache.spark.api.java JavaSparkContext]
   [org.apache.spark.mllib.tree RandomForest]
   [org.apache.spark.mllib.tree.model RandomForestModel]
   [org.apache.spark.mllib.util MLUtils]))

(def ctx (atom nil))

(defn init []
  (reset! ctx (JavaSparkContext. (-> (SparkConf.)
                                     (.setAppName "RandomForestRegression")
                                     (.setMaster "local")
                                     (.setJars (into-array ["target/uberjar/app.jar"]))))))

(defn load-model! [id]
  (when-not @ctx (init))
  (RandomForestModel/load (.sc @ctx) (str "models/" id)))

(defn start! []
  (when-not @ctx (init))
  (let [datapath "order-book.txt"
        data (-> (MLUtils/loadLibSVMFile (.sc @ctx) datapath) (.toJavaRDD))

        splits (.randomSplit data (double-array [0.8 0.2]))
        training-data (aget splits 0)
        test-data (aget splits 1)

        categorical-features-info {}
        num-trees 16
        feature-subset-strategy "auto"
        impurity "variance"
        max-depth 4
        max-bins 8
        seed 12345
        model (RandomForest/trainRegressor training-data categorical-features-info num-trees feature-subset-strategy impurity max-depth max-bins seed)
        labelsAndPredictions (.mapToPair test-data (PairFunction. (fn [p] [(.predict model (.features p)) (.label p)])))
        testMSE (.mean (.mapToDouble labelsAndPredictions (DoubleFunction. (fn [[p v]] (Math/pow (- p v) 2)))))]
    (println "MSE: " testMSE)
    (.save model (.sc @ctx) (str "models/" (System/currentTimeMillis)))))