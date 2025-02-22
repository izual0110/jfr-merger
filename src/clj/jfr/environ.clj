(ns jfr.environ
  (:require [clojure.edn :as edn]))

(def config (atom nil))

(defn- check-file [file]
  (.exists (java.io.File. file)))

(defn- load-config []
  (let [config-file "config.edn"]
    (if (check-file config-file)
       (edn/read-string (slurp config-file))
      [])))

(defn get-property 
  ([key] (when (nil? @config) (reset! config (load-config))) (get @config key))
  ([key & xs] (when (nil? @config) (reset! config (load-config))) 
   (get-in @config (concat [key] xs))))

(defn get-jfr-data-path [] (.getAbsolutePath (java.io.File. (get-property :get-jfr-data-path))))

