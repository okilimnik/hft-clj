(ns hft.model.ea
  (:require
   [hft.interop :refer [as-function]])
  (:import
   [io.jenetics Genotype LongChromosome LongGene]
   [io.jenetics.engine Engine EvolutionResult]))

(def GENERATIONS 200)

(defn eval! [gt]
  (reduce (fn [res idx]
            (let [v (-> (.get gt idx)
                        (.as LongChromosome)
                        (.toArray)
                        (#(reduce + 0 %)))]
              (+ res v)))
          (range (.length gt))))

(def timeframe-chromosome (LongGene/of 0 10))
(def price-change-chromosome (LongGene/of 0 50))

(defn start []
  (let [gtf (Genotype/of (->> [(LongChromosome/of [timeframe-chromosome])
                               (LongChromosome/of [price-change-chromosome])]
                              (repeat 5)
                              (mapcat identity)))
        engine (.build (Engine/builder (as-function eval!) gtf))
        result (-> engine
                   (.stream)
                   (.limit GENERATIONS)
                   (.collect (EvolutionResult/toBestGenotype)))]
    (println result)))

(defn -main [& args]
  (start))

;; clj -M -m hft.model.ea