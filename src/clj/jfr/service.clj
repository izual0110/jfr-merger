(ns jfr.service
  (:import (java.util UUID)
           (one.convert JfrToHeatmap Arguments)
           (java.io ByteArrayOutputStream)
           (one.jfr JfrReader))
  (:require [clojure.java.io :as io]
            [jfr.storage :as storage]
            [jfr.utils :as utils]
            [jfr.environ :as env]
            [clojure.string :as str]))

(defn- get-temp-dir [] (env/temp-dir))

(def ^:private valid-output-types #{"heatmap" "html"})

(defn convert-to-bytes
  "Converts a JFR input file to the requested representation (heatmap or flamegraph HTML).

  `output-type` must be either \"heatmap\" or \"html\". Optional `profile-flag` values
  (e.g. `\"--cpu\"`, `\"--alloc\"`) are passed through to the converter.
  Returns a byte array with the rendered artifact."
  ([input output-type]
   (convert-to-bytes input output-type nil))
  ([input output-type profile-flag]
   (when-not (valid-output-types output-type)
     (throw (ex-info (str "Unsupported output type: " output-type)
                     {:supported valid-output-types
                      :output-type output-type})))
   (let [args (Arguments. (into-array String
                                      (cond-> ["--output" output-type]
                                        (and profile-flag (not (str/blank? profile-flag)))
                                        (conj profile-flag)
                                        true
                                        (conj input))))
         baos (ByteArrayOutputStream.)]
     (with-open [jfr (JfrReader. input)]
       (let [converter (JfrToHeatmap. jfr args)]
         (.convert converter)
         (.dump converter baos)))
     (.toByteArray baos))))

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
        temp-dir (get-temp-dir)
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
    (doseq [output-type ["heatmap" "html"]
            {:keys [suffix flag]} [{:suffix "" :flag nil}
                                   {:suffix "-cpu" :flag "--cpu"}
                                   {:suffix "-alloc" :flag "--alloc"}]]
      (->> (convert-to-bytes merged-path output-type flag)
           (storage/save-bytes (str uuid suffix (when (= output-type "html") "-html")))))
    [uuid  (jfr-stats merged-path)]))
