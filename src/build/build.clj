(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'app/app)          
(def version "0.1.0")
(def class-dir "target/classes") 
(def basis (b/create-basis {:project "deps.edn"}))  
(def uber-file (format "target/%s-%s-standalone.jar"     
                       (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))    

(defn uber [_]
  (clean nil)                                               
  (b/copy-dir {:src-dirs ["src/clj" "resources"]                  
               :target-dir class-dir})
  (b/compile-clj {:basis basis                                 ; AOT-компиляция ns с -main
                  :class-dir class-dir
                  :ns-compile ['jfr.core]})
  (b/uber {:class-dir class-dir                               
           :uber-file uber-file
           :basis basis
           :main 'jfr.core}))     