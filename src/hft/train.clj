(ns hft.train
  (:import (ai.djl Model)
           (ai.djl.basicdataset.cv.classification ImageFolder)
           (ai.djl.basicmodelzoo.cv.classification ResNetV1)
           (ai.djl.metric Metrics)
           (ai.djl.modality.cv.transform ToTensor)
           (ai.djl.ndarray.types Shape)
           (ai.djl.repository Repository)
           (ai.djl.training DefaultTrainingConfig EasyTrain)
           (ai.djl.training.evaluator Accuracy)
           (ai.djl.training.listener TrainingListener$Defaults)
           (ai.djl.training.loss Loss)
           (ai.djl.training.optimizer Optimizer)
           (ai.djl.training.tracker Tracker)
           (java.nio.file Paths)
           [java.util Arrays]))

(def lr 0.05)
(def epochs 10)
(def batch-size 100)
(def IMAGE-SIZE 60)
(def IMAGE-NUM-CHAN 3)

(defn get-model [{:keys [num-categories img-opts]}]
  (let [model (Model/newInstance "cnn")
        resNet50 (.build
                  (doto (ResNetV1/builder)
                    (.setImageShape (Shape. (Arrays/asList (into-array [(:num-chan img-opts) (:width img-opts) (:height img-opts)]))))
                    (.setNumLayers 50)
                    (.setOutSize num-categories)))]
    (.setBlock model resNet50)
    model))

(defn get-dataset [path]
  (let [repo (Repository/newInstance "folder" path)
        dataset (.build
                 (doto (ImageFolder/builder)
                   (.setRepository repo)
                   (.addTransform (ToTensor.))
                   (.setSampling batch-size true)))]
    (.prepare dataset)
    dataset))

(defn run []
  (let [model (get-model {:img-opts {:width IMAGE-SIZE
                                     :height IMAGE-SIZE
                                     :num-chan IMAGE-NUM-CHAN}
                          :num-categories 8})
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
    (prn (.getTrainingResult trainer))))