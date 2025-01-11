(defproject jfr-merger "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/clojurescript "1.11.132"]
                 [http-kit "2.8.0"]
                 [compojure "1.7.1"]]
  :main ^:skip-aot jfr-merger.core
  :target-path "target/%s"
  :profiles {
             :uberjar {:aot :all :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:plugins [[lein-cljsbuild "1.1.8"]
                             [lein-figwheel "0.5.20"]]
                   :dependencies [[reloaded.repl "0.2.4"]]
                   :source-paths ["dev", "backend"]
                   :cljsbuild {:builds [{:source-paths ["frontend" "dev"]
                                         :figwheel true
                                         :compiler {:output-to "target/classes/public/app.js"
                                                    :output-dir "target/classes/public/out"
                                                    :optimizations :none
                                                    :recompile-dependents true
                                                    :source-map true}}]}}})
