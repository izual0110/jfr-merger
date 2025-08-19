(ns jfr.environ
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def config (atom nil))

(defn- load-config []
  (let [config-file (some #(when (.exists (io/file %)) %) ["resources/config.edn" "./config.edn"])]
    (if (some? config-file)
      (edn/read-string (slurp config-file))
      [])))

(defn get-property
  ([key] (when (nil? @config) (reset! config (load-config))) (get @config key))
  ([key & xs] (when (nil? @config) (reset! config (load-config)))
   (get-in @config (concat [key] xs))))

(defn get-jfr-data-path [] (.getAbsolutePath (java.io.File. (get-property :jfr-data-path))))
(defn temp-dir [] (get-property :temp-dir))

