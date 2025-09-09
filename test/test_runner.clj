(ns test-runner
  (:require [clojure.test :as t]
            [clojure.tools.namespace.find :as ns-find]
            [clojure.java.io :as io]))

(defn -main [& _]
  (let [dirs [(io/file "test")]
        nss (ns-find/find-namespaces-in-dir (first dirs))]
    (println "Running tests in namespaces:" nss)
    (apply require nss)
    (let [{:keys [fail error]} (apply t/run-tests nss)]
      (shutdown-agents)
      (System/exit (if (pos? (+ (or fail 0) (or error 0))) 1 0)))))