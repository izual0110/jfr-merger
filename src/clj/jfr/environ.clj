(ns jfr.environ
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def config (atom nil))

(defn slurp-safe [x]
  (try
    (when x [(slurp x) (str x)])             
    (catch Exception _ nil)))                    

(defn file-readable?
  [path]
  (let [f (io/file path)]         
    (and (.exists f)                
         (.isFile f)          
         (.canRead f))))


(defn- load-config []
  (let [[config path] (or
                     (when-let [name "./config.edn"] (file-readable? name) (slurp-safe (io/file name)))
                     (when-let [name "./resources/config.edn"] (file-readable? name) (slurp-safe (io/file name)))
                     (when-let [u (io/resource "config.edn")] (slurp-safe u)))]
    (println "Loading config from" path)
    (if (some? config)
      (edn/read-string config)
      [])))

(defn- get-property
  ([key] (when (nil? @config) (reset! config (load-config))) (get @config key))
  ([key & xs] (when (nil? @config) (reset! config (load-config)))
   (get-in @config (concat [key] xs))))

(defn get-jfr-data-path [] (.getAbsolutePath (java.io.File. (get-property :jfr-data-path))))
(defn temp-dir [] (get-property :temp-dir))

