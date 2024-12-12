(ns hft.model.ea
  (:import [io.jenetics BitChromosome Genotype]
           [io.jenetics.engine Engine EvolutionResult]
           [java.util.function Function]))

(defn eval! [gt]
  (-> gt
      (.chromosome)
      (.as BitChromosome)
      (.bitCount)))

(defn as-function [f]
  (reify Function
    (apply [_this arg]
      (f arg))))

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