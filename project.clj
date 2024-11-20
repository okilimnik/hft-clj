(defproject hft "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :repositories {"space-maven" {:url "https://packages.jetbrains.team/maven/p/skija/maven"}}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/core.async "1.6.681"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [org.imgscalr/imgscalr-lib "4.2"]
                 [metosin/jsonista "0.3.8"]
                 [io.github.binance/binance-connector-java "3.2.0"]
                 [org.ta4j/ta4j-core "0.16"]
                 [com.google.cloud/google-cloud-storage "2.43.1"]
                 [jfree/jfreechart "1.0.13"]
                 [com.xtdb/xtdb-google-cloud-storage "1.24.3"]]
  :main hft.core
  :uberjar-name "hft.jar"
  :profiles {:uberjar {:aot :all}})