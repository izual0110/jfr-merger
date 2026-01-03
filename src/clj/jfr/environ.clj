(ns jfr.environ
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defonce ^:private config (atom nil))

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
  (let [candidates (concat (for [path ["./config.edn" "./resources/config.edn"]
                                 :when (file-readable? path)]
                             (io/file path))
                           [(io/resource "config.edn")])
        [raw path] (some slurp-safe candidates)]
    (when path
      (log/info (str "Loading config from " path))
    (if raw
      (edn/read-string raw)
      {})))

(defn- get-property [key] 
  (get (or @config (reset! config (load-config))) key))

(defn get-jfr-data-path [] (.getAbsolutePath (java.io.File. (get-property :jfr-data-path))))
(defn temp-dir [] (get-property :temp-dir))
