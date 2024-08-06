(defproject hft "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :main hft.core
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-shell "0.5.0"]]}}
  :aliases
  {"native"
   ["shell"
    "native-image"
    "--report-unsupported-elements-at-runtime"
    "--initialize-at-run-time=hft.core"
    "--features=clj_easy.graal_build_time.InitClojureClasses"
    "--no-fallback" "--no-server"
    "-jar" "./target/${:name}.jar"
    "-H:Name=./target/${:name}"]})