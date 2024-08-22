(defproject hft "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [metosin/jsonista "0.3.8"]
                 [io.github.binance/binance-connector-java "3.2.0"]]
  :main hft.core
  :uberjar-name "hft.jar"
  :profiles {:uberjar {:aot :all}})