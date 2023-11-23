(ns hft.train
  (:require [clojure.java.io :as io])
  (:import (ai.djl Model)
           (ai.djl.basicdataset.cv.classification ImageFolder)
           (ai.djl.basicmodelzoo.cv.classification ResNetV1)
           (ai.djl.metric Metrics)
           (ai.djl.modality.cv.transform ToTensor)
           (ai.djl.ndarray NDManager)
           (ai.djl.ndarray.types Shape)
           (ai.djl.repository Repository)
           (ai.djl.training DefaultTrainingConfig EasyTrain)
           (ai.djl.training.evaluator Accuracy)
           (ai.djl.training.listener TrainingListener$Defaults)
           (ai.djl.training.loss Loss)
           (ai.djl.training.optimizer Optimizer)
           (ai.djl.training.tracker Tracker)
           [java.nio.file Paths]
           (java.util Arrays)))

(def lr 0.005)
(def epochs 46)
(def batch-size 20)
(def IMAGE-SIZE 60)
(def IMAGE-NUM-CHAN 3)
(def NUM-CATEGORIES 2)
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
     (.setNumLayers 50)
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

(defn start! []
  (let [_memory-manager (NDManager/newBaseManager)
        model (get-model MODEL-OPTIONS)       
        ;model (load-model)
        loss (Loss/softmaxCrossEntropyLoss)
        lrt (Tracker/fixed lr)
        sgd (-> (Optimizer/sgd)
                (.setLearningRateTracker lrt)
                (.build))
        config (-> (DefaultTrainingConfig. loss)
                   (.optOptimizer sgd)
                   (.addEvaluator (Accuracy.))
                   (.addTrainingListeners (TrainingListener$Defaults/logging)))
        inputShape (Shape. (Arrays/asList (into-array [1 IMAGE-NUM-CHAN IMAGE-SIZE IMAGE-SIZE])))
        trainer (doto (.newTrainer model config)
                  (.setMetrics (Metrics.))
                  (.initialize (into-array [inputShape])))
        train-set (get-dataset "./dataset/train")
        test-set (get-dataset "./dataset/test")]
    (EasyTrain/fit trainer epochs train-set test-set)
    ;(prn (.getTrainingResult trainer))
    (.setProperty model "Epoch" (str epochs))
    (.save model (Paths/get (.toURI (io/file MODEL-FOLDER))) MODEL-NAME)))

