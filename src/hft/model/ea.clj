(ns hft.model.ea
  (:require [hft.interop :refer [as-function]])
  (:import [io.jenetics BitChromosome Genotype]
           [io.jenetics.engine Engine EvolutionResult]))

(defn eval! [gt]
  (-> gt
      (.chromosome)
      (.as BitChromosome)
      (.bitCount)))

(defn start []
  (let [gtf (Genotype/of [(BitChromosome/of 10 0.5)])
        engine (.build (Engine/builder (as-function eval!) gtf))
        result (-> engine
                   (.stream)
                   (.limit 100)
                   (.collect (EvolutionResult/toBestGenotype)))]
    (println result)))

(defn -main [& args]
  (start))

;; clj -M -m hft.model.ea