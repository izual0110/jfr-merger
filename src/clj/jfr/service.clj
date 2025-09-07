(ns jfr.service
   (:import (java.util UUID)
           (one.convert JfrToHeatmap Arguments)
           (java.io ByteArrayOutputStream)
           (one.jfr JfrReader))
  (:require [clojure.java.io :as io]
            [jfr.storage :as storage]
            [jfr.utils :as utils]
            [jfr.environ :as env]))

(def temp-dir (env/temp-dir))

(defn convert-to-bytes
  "Converts JFR input file to heatmap bytes using JfrToHeatmap, returns byte array."
  [input type]
  (let [args (Arguments. (into-array String ["--output" "heatmap" type input]))
        baos (ByteArrayOutputStream.)]
    (with-open [jfr (JfrReader. input)]
      (let [converter (JfrToHeatmap. jfr args)]
        (.convert converter)
        (.dump converter baos)))
    (.toByteArray baos)))

(defn jfr-stats
  [jfr-path]
  (with-open [jfr (JfrReader. jfr-path)]
    (let [events (.readAllEvents jfr)
          count (count events)
          grouped (frequencies (map #(-> % .getClass .getSimpleName) events))]
      {:event-count count
       :start-time (utils/ns-to-utc (.startNanos jfr))
       :end-time  (utils/ns-to-utc (.endNanos jfr))
       :duration (int (/ (.durationNanos jfr) 1000000))
       :event-types grouped})))

(defn generate-heatmap [{:keys [params]}]
  (let [uuid (str (UUID/randomUUID))
        merged-path (str temp-dir "/" uuid ".jfr")
        files (->> (get params "files")
                   utils/normalize-vector
                   (filter #(and (map? %) (contains? % :tempfile))))]
    (io/make-parents merged-path)
    (println "UUID:" uuid "\n\t\tFiles to merge:" files)
    (with-open [out (io/output-stream merged-path)]
      (doseq [file files]
        (with-open [in (io/input-stream (:tempfile file))]
          (io/copy in out))))
    (let [all-bytes (convert-to-bytes merged-path "")
          cpu-bytes (convert-to-bytes merged-path "--cpu")
          alloc-bytes (convert-to-bytes merged-path "--alloc")
          stats (jfr-stats merged-path)]
      (storage/save-bytes uuid all-bytes)
      (storage/save-bytes (str uuid "-cpu") cpu-bytes)
      (storage/save-bytes (str uuid "-alloc") alloc-bytes)
      [uuid stats])))

