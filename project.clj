(defproject hft "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://hft-heroku.herokuapp.com"
  :license {:name "FIXME: choose"
            :url "http://example.com/FIXME"}
  :dependencies [[org.clojure/clojure "1.11.0"]
                 [org.clojure/core.async "1.6.673"]
                 [org.clojure/tools.cli "1.0.219"]
                 [io.github.binance/binance-connector-java "3.0.0rc3"]
                 [cheshire/cheshire "5.11.0"]
                 [org.apache.spark/spark-mllib_2.13 "3.4.1"]
                 [clojurewerkz/quartzite "2.1.0"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [com.google.firebase/firebase-admin "9.2.0"]
                 #_[com.intel.analytics.zoo/analytics-zoo-bigdl_0.13.0-spark_3.0.0 "0.11.2"]]
  :main hft.core
  :target-path "target/%s"
  :uberjar-name "app.jar"
  :jvm-opts ["--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
