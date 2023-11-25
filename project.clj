(defproject hft "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://hft-heroku.herokuapp.com"
  :license {:name "FIXME: choose"
            :url "http://example.com/FIXME"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]
                 [org.clojure/tools.cli "1.0.219"]
                 [metosin/jsonista "0.3.8"]
                 [com.taoensso/timbre "6.3.1"]
                 [com.fzakaria/slf4j-timbre "0.4.0"]
                 [io.github.binance/binance-connector-java "3.0.0rc3"]
                 [com.google.cloud/google-cloud-storage "2.25.0"]
                 [net.mikera/imagez "0.12.0"]
                 [ai.djl/api "0.25.0"]
                 [ai.djl.pytorch/pytorch-engine "0.25.0" :scope "runtime"]
                 [ai.djl/basicdataset "0.25.0"]
                 [ai.djl/model-zoo "0.25.0"]]
  :main hft.core
  :target-path "target/%s"
  :uberjar-name "app.jar"
  :jvm-opts ["--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
