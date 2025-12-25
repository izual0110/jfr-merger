(ns jfr.service
  (:import (java.util UUID)
           (one.convert JfrToHeatmap JfrToFlame Arguments)
           (java.io ByteArrayOutputStream)
           (one.jfr JfrReader)
           (java.nio.charset StandardCharsets))
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [jfr.storage :as storage]
            [jfr.utils :as utils]
            [jfr.environ :as env]
            [jfr.detector.detector :as detector]
            [jfr.detector.worker :as detector-worker]))

(defn- get-temp-dir [] (env/temp-dir))

(defn- convert-with
  "Runs the provided converter constructor against a JFR input and returns the produced bytes."
  [converter-fn input profile-flag]
  (let [args (Arguments. (into-array String
                                     (cond-> []
                                       profile-flag (conj profile-flag)
                                       :always (conj input))))
        baos (ByteArrayOutputStream.)]
    (with-open [jfr (JfrReader. input)]
      (let [converter (converter-fn jfr args)]
        (.convert converter)
        (.dump converter baos)))
    (.toByteArray baos)))

(defn convert-heatmap
  "Converts a JFR input file to a heatmap (optionally cpu/alloc scoped)."
  [input profile-flag]
  (convert-with #(JfrToHeatmap. %1 %2) input profile-flag))

(defn convert-flamegraph
  "Converts a JFR input file to a flamegraph HTML (optionally cpu/alloc scoped)."
  [input profile-flag]
  (convert-with #(JfrToFlame. %1 %2) input profile-flag))

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

(defn- detector-key [uuid]
  (str uuid "-detector"))

(defn- write-detector-result! [uuid data]
  (let [json-str (json/write-str data)
        bytes (.getBytes json-str StandardCharsets/UTF_8)]
    (storage/save-bytes (detector-key uuid) bytes)
    data))

(defn detector-result [uuid]
  (when-let [bytes (storage/load-bytes (detector-key uuid))]
    (json/read-str (String. bytes StandardCharsets/UTF_8) :key-fn keyword)))

(defn- schedule-detector! [uuid merged-path]
  (let [scheduled-at (System/currentTimeMillis)]
    (write-detector-result! uuid {:uuid uuid  :status "pending" :scheduled-at scheduled-at})
    (detector-worker/enqueue!
     (fn []
       (let [started-at (System/currentTimeMillis)]
         (try
           (let [hits (detector/detect-patterns {:jfr-path merged-path
                                                      :alloc-only? false})
                 summary (detector/summarize hits {:top-stacks 10})
                 finished-at (System/currentTimeMillis)
                 result {:uuid uuid
                         :status "done"
                         :scheduled-at scheduled-at
                         :started-at started-at
                         :finished-at finished-at
                         :hit-count (count hits)
                         :summary summary}]
             (write-detector-result! uuid result)
             (println "Detector job completed for" uuid "\n\tat" finished-at "\n\tduration:" (- finished-at started-at) "ms"))
           (catch Throwable t
             (let [finished-at (System/currentTimeMillis)
                   result {:uuid uuid
                           :status "failed"
                           :scheduled-at scheduled-at
                           :started-at started-at
                           :finished-at finished-at
                           :error (.getMessage t)}]
               (println "Detector job failed for" uuid ":" (.getMessage t))
               (.printStackTrace t)
               (write-detector-result! uuid result)))))))
    (println "Scheduled detector job for" uuid "\n\tat" scheduled-at)))

(defn generate-artifacts [{:keys [params]}]
  (let [uuid (str (UUID/randomUUID))
        temp-dir (get-temp-dir)
        merged-path (str temp-dir "/" uuid ".jfr")
        files (->> (get params "files")
                   utils/normalize-vector
                   (filter #(and (map? %) (contains? % :tempfile))))
        add-flame? (= "true" (get params "addFlamegraph"))]
    (io/make-parents merged-path)
    (println "UUID:" uuid (if add-flame? "with flamegraph" "without flamegraph") "\n\t\tFiles to merge:" files)
    (with-open [out (io/output-stream merged-path)]
      (doseq [file files]
        (with-open [in (io/input-stream (:tempfile file))]
          (io/copy in out))))
    (doseq [{:keys [suffix flag]} [{:suffix "" :flag nil}
                                   {:suffix "-cpu" :flag "--cpu"}
                                   {:suffix "-alloc" :flag "--alloc"}]]
      (->> (convert-heatmap merged-path flag)
           (storage/save-bytes (str uuid suffix)))
      (when add-flame?
        (->> (convert-flamegraph merged-path flag)
             (storage/save-bytes (str uuid "-flame" suffix)))))
    [uuid (jfr-stats merged-path) (schedule-detector! uuid merged-path) add-flame?]))
