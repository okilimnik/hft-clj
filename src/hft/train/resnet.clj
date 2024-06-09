(ns hft.train.resnet.train
  (:require [clojure.java.io :as io]
            [hft.dataset :as dataset]
            [hft.gcloud :refer [upload-model!]])
  (:import (ai.djl Model)
           (ai.djl.basicdataset.cv.classification ImageFolder)
           (ai.djl.basicmodelzoo.cv.classification ResNetV1)
           (ai.djl.metric Metrics)
           (ai.djl.modality.cv.transform ToTensor)
           (ai.djl.ndarray NDManager)
           (ai.djl.ndarray.types Shape)
           (ai.djl.repository Repository)
           (ai.djl.training DefaultTrainingConfig EasyTrain Trainer)
           (ai.djl.training.evaluator Accuracy)
           (ai.djl.training.listener EvaluatorTrainingListener TrainingListener TrainingListener$BatchData TrainingListener$Defaults)
           (ai.djl.training.loss Loss)
           (ai.djl.training.optimizer Optimizer)
           (ai.djl.training.tracker Tracker)
           [java.nio.file Paths]
           (java.util Arrays)
           (org.apache.commons.lang3 ArrayUtils)))

(def lr 0.05)
(def epochs 100)
(def batch-size 100)
(def IMAGE-SIZE dataset/INPUT-SIZE)
(def IMAGE-NUM-CHAN 3)
(def NUM-CATEGORIES 3)
(def MODEL-NAME "cnn")
(def MODEL-FOLDER "./model")
(def MODEL-OPTIONS {:img-opts {:width IMAGE-SIZE
                               :height IMAGE-SIZE
                               :num-chan IMAGE-NUM-CHAN}
                    :num-categories NUM-CATEGORIES})

(defn create-resnet-block [{:keys [num-categories img-opts]}]
  (.build
   (doto (ResNetV1/builder)
     (.setImageShape (Shape. (Arrays/asList (into-array [(:num-chan img-opts) (:width img-opts) (:height img-opts)]))))
     (.setNumLayers 50) ;;101
     (.setOutSize num-categories))))

(defn get-model [options]
  (doto (Model/newInstance MODEL-NAME)
    (.setBlock (create-resnet-block options))))

(defn get-dataset [^String path]
  (let [repo (Repository/newInstance "folder" path)
        dataset (.build
                 (doto (ImageFolder/builder)
                   (.setRepository repo)
                   (.addTransform (ToTensor.))
                   (.setSampling batch-size true)))]
    (.prepare dataset)
    dataset))

(defn load-model []
  (doto (get-model MODEL-OPTIONS)
    (.load (Paths/get (.toURI (io/file MODEL-FOLDER))))))

(def callback
  (let [epoch (atom 0)]
    (reify TrainingListener
      (^void onEpoch [_this ^Trainer trainer]
        (swap! epoch inc)
        (let [metrics (.getMetrics trainer)
              evaluators (.getEvaluators trainer)]
          (doall
           (for [evaluator evaluators
                 :let [metric-name (EvaluatorTrainingListener/metricName evaluator EvaluatorTrainingListener/VALIDATE_EPOCH)]
                 :when (.hasMetric metrics metric-name)
                 :let [value (.floatValue (.getValue (.latestMetric metrics metric-name)))]
                 :when (and (= metric-name "validate_epoch_Accuracy")
                            (>= value 0.72))
                 :let [model (.getModel trainer)]]
             (do (.setProperty model "Epoch" (str @epoch))
                 (.save model (Paths/get (.toURI (io/file MODEL-FOLDER))) MODEL-NAME))))))
      (^void onTrainingBegin [_this ^Trainer trainer])
      (^void onTrainingEnd [_this ^Trainer trainer])
      (^void onTrainingBatch [_this ^Trainer trainer ^TrainingListener$BatchData batchData])
      (^void onValidationBatch [_this ^Trainer trainer ^TrainingListener$BatchData batchData]))))

(defn start! []
  (let [_memory-manager (NDManager/newBaseManager)
        ;model (get-model MODEL-OPTIONS)
        model (load-model)
        loss (Loss/softmaxCrossEntropyLoss)
        lrt (Tracker/fixed lr)
        sgd (-> (Optimizer/sgd)
                (.setLearningRateTracker lrt)
                (.build))
        config (-> (DefaultTrainingConfig. loss)
                   (.optOptimizer sgd)
                   (.addEvaluator (Accuracy.))
                   (.addTrainingListeners
                     (ArrayUtils/add (TrainingListener$Defaults/logging) ^TrainingListener callback)))
        inputShape (Shape. (Arrays/asList (into-array [1 IMAGE-NUM-CHAN IMAGE-SIZE IMAGE-SIZE])))
        trainer (doto (.newTrainer model config)
                  (.setMetrics (Metrics.))
                  (.initialize (into-array [inputShape])))
        train-set (get-dataset "./dataset/train")
        test-set (get-dataset "./dataset/test")]
    (EasyTrain/fit trainer epochs train-set test-set)
    ;(prn (.getTrainingResult trainer))
    (.setProperty model "Epoch" (str epochs))
    (.save model (Paths/get (.toURI (io/file MODEL-FOLDER))) MODEL-NAME)
    (.close model)
    (let [file (->> (file-seq (io/file MODEL-FOLDER))
                    (remove #(.isDirectory %))
                    (sort-by #(.lastModified %))
                    last)]
      (upload-model! (.getName file) (.getAbsolutePath file)))))

