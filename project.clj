(defproject hft "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]
                 [org.clojure/tools.cli "1.0.219"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                ; [org.imgscalr/imgscalr-lib "4.2"]
                 [nrepl/nrepl "1.0.0"]
                 [metosin/jsonista "0.3.8"]
                 [alekcz/fire "0.5.1-SNAPSHOT"]
                 [com.github.clj-easy/graal-build-time "1.0.5"]
                 ;[com.phronemophobic/membrane "0.14.4-beta"]
                 ]
  :main hft.core
  :uberjar-name "hft.jar"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dtech.v3.datatype.graal-native=true"
                                  "-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.spec.skip-macros=true"]}})