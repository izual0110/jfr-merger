(ns jfr.service
  (:import (java.util UUID)
           (one.convert JfrToHeatmap JfrToFlame Arguments)
           (java.io ByteArrayOutputStream)
           (one.jfr JfrReader)
           (java.nio.file Files Paths)
           (java.nio.charset StandardCharsets))
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [jfr.storage :as storage]
            [jfr.utils :as utils]
            [jfr.environ :as env]
            [jfr.detector.detector :as detector]
            [jfr.detector.worker :as detector-worker]
            [clojure.string :as string]))

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
             (log/info (str "Detector job completed for " uuid
                            " at " finished-at
                            " (duration: " (- finished-at started-at) " ms)")))
           (catch Throwable t
             (let [finished-at (System/currentTimeMillis)
                   result {:uuid uuid
                           :status "failed"
                           :scheduled-at scheduled-at
                           :started-at started-at
                           :finished-at finished-at
                           :error (.getMessage t)}]
               (log/error (str "Detector job failed for " uuid ": " (.getMessage t)) t)
               (write-detector-result! uuid result)))))))
    (log/info (str "Scheduled detector job for " uuid " at " scheduled-at))))

(defn- parse-filesystem-paths [raw-paths]
  (->> (utils/normalize-vector raw-paths)
       (mapcat #(string/split (str %) #"\r?\n"))
       (map string/trim)
       (remove string/blank?)
       vec))

(defn- ensure-readable-jfr! [path-str]
  (let [path (Paths/get path-str (make-array String 0))]
    (when-not (Files/exists path (make-array java.nio.file.LinkOption 0))
      (throw (ex-info "Filesystem JFR file does not exist"
                      {:path path-str
                       :reason :file-not-found})))
    (when-not (Files/isRegularFile path (make-array java.nio.file.LinkOption 0))
      (throw (ex-info "Filesystem JFR path must point to a file"
                      {:path path-str
                       :reason :not-a-file})))
    (when-not (Files/isReadable path)
      (throw (ex-info "Filesystem JFR file is not readable"
                      {:path path-str
                       :reason :not-readable})))
    path-str))

(defn- collect-jfr-inputs [{:strs [files filePaths]}]
  (let [uploaded-paths (->> (utils/normalize-vector files)
                            (filter #(and (map? %) (contains? % :tempfile)))
                            (mapv (comp str :tempfile)))
        fs-paths (->> (parse-filesystem-paths filePaths)
                      (mapv ensure-readable-jfr!))
        all-inputs (into uploaded-paths fs-paths)]
    (when (empty? all-inputs)
      (throw (ex-info "At least one JFR file must be provided"
                      {:reason :missing-inputs})))
    all-inputs))

(defn generate-artifacts [{:keys [params]}]
  (let [uuid (str (UUID/randomUUID))
        temp-dir (get-temp-dir)
        merged-path (str temp-dir "/" uuid ".jfr")
        inputs (collect-jfr-inputs params)
        add-flame? (= "true" (get params "addFlamegraph"))
        add-detector? (= "true" (get params "addDetector"))]
    (io/make-parents merged-path)
    (log/info (str "UUID: " uuid " "
                   (if add-flame? "with flamegraph" "without flamegraph")
                   "\n\t\tFiles to merge: " inputs))
    (with-open [out (io/output-stream merged-path)]
      (doseq [input inputs]
        (with-open [in (io/input-stream input)]
          (io/copy in out))))
    (doseq [{:keys [suffix flag]} [{:suffix "" :flag nil}
                                   {:suffix "-cpu" :flag "--cpu"}
                                   {:suffix "-alloc" :flag "--alloc"}]]
      (->> (convert-heatmap merged-path flag)
           (storage/save-bytes (str uuid suffix)))
      (when add-flame?
        (->> (convert-flamegraph merged-path flag)
             (storage/save-bytes (str uuid "-flame" suffix)))))
    (when add-detector? (schedule-detector! uuid merged-path))
    [uuid (jfr-stats merged-path) add-flame? add-detector?]))
