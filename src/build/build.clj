(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'app/jfr-merger)
(def version "0.1.1")
(def class-dir "target/classes") 
(def basis (b/create-basis {:project "deps.edn"}))  
(def uber-file (format "target/%s-%s.jar"
                       (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))    

(defn uber [_]
  (clean nil)                                               
  (b/copy-dir {:src-dirs ["src/clj" "resources"]                  
               :target-dir class-dir})
  (b/compile-clj {:basis basis             
                  :class-dir class-dir
                  :java-opts ["--enable-native-access=ALL-UNNAMED"]
                  :ns-compile ['jfr.core]})
  (b/uber {:class-dir class-dir                               
           :uber-file uber-file
           :basis basis
           :main 'jfr.core}))     