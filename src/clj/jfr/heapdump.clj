(ns jfr.heapdump
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [jfr.environ :as env]
   [org.httpkit.server :refer [with-channel on-close on-receive send!]])
  (:import
   (java.io BufferedOutputStream File FileOutputStream PrintWriter StringWriter)
   (java.util UUID)
   (org.openjdk.jol.datamodel ModelVM)
   (org.openjdk.jol.heap HeapDumpReader)
   (org.openjdk.jol.layouters HotSpotLayouter)))

(def ^:private table-columns
  [{:key :instances :label "INSTANCES"}
   {:key :size :label "SIZE"}
   {:key :sum-size :label "SUM SIZE"}
   {:key :class :label "CLASS"}])
(def ^:private table-labels
  (into {} (map (juxt :key :label) table-columns)))

(defn- table-print-first []
  (or (some-> (System/getProperty "printFirst") Integer/parseInt)
      30))

(defn- format-table-row [^PrintWriter pw row]
  (doseq [{:keys [key]} (butlast table-columns)]
    (.printf pw " %,15d" (long (get row key))))
  (.printf pw "    %s%n" (str (:class row))))

(defn- print-table
  [^PrintWriter pw rows sort-key]
  (let [sorted-rows (if (= sort-key :class)
                      (sort-by :class rows)
                      (sort-by sort-key #(compare %2 %1) rows))
        print-first (table-print-first)
        total-count (count sorted-rows)
        [head tail] (split-at print-first sorted-rows)
        tops (reduce (fn [acc row]
                       (reduce (fn [inner {:keys [key]}]
                                 (if (= key :class)
                                   inner
                                   (update inner key + (get row key))))
                               acc
                               (butlast table-columns)))
                     {:instances 0 :size 0 :sum-size 0}
                     head)
        sums (reduce (fn [acc row]
                       (reduce (fn [inner {:keys [key]}]
                                 (if (= key :class)
                                   inner
                                   (update inner key + (get row key))))
                               acc
                               (butlast table-columns)))
                     {:instances 0 :size 0 :sum-size 0}
                     sorted-rows)]
    (.println pw "=== Class Histogram")
    (.println pw)
    (if (= sort-key :class)
      (.println pw "Table is sorted by \"CLASS\".")
      (.println pw (format "Table is sorted by \"%s\"."
                           (get table-labels sort-key))))
    (when (not= print-first Integer/MAX_VALUE)
      (.println pw (format "Printing first %d lines. Use -DprintFirst=# to override." print-first)))
    (.println pw)
    (doseq [{:keys [label]} (butlast table-columns)]
      (.printf pw " %15s" label))
    (.println pw "    CLASS")
    (.println pw "------------------------------------------------------------------------------------------------")
    (doseq [row head]
      (format-table-row pw row))
    (when (< print-first total-count)
      (doseq [{:keys [label]} (butlast table-columns)]
        (.printf pw " %15s" "..."))
      (.printf pw "    ...%n")
      (doseq [{:keys [key]} (butlast table-columns)]
        (.printf pw " %,15d" (long (- (get sums key) (get tops key)))))
      (.printf pw "    <other>%n"))
    (.println pw "------------------------------------------------------------------------------------------------")
    (doseq [{:keys [key]} (butlast table-columns)]
      (.printf pw " %,15d" (long (get sums key))))
    (.printf pw "    <total>%n")
    (.println pw)))

(defn- get-vm-version []
  (try
    (Integer/parseInt (System/getProperty "java.specification.version"))
    (catch Exception _
      8)))

(defn heapdump-stats-text
  "Return the jol-cli heapdump-stats output for the provided heap dump."
  [path]
  (let [layouter (HotSpotLayouter. (ModelVM.) (get-vm-version))
        writer (StringWriter.)
        pw (PrintWriter. writer)]
    (.println pw (str "Heap Dump: " path))
    (let [reader (HeapDumpReader. (io/file path))
          data (.parse reader)
          rows (for [cd (.keys data)
                     :let [cnt (.count data cd)]
                     :when (pos? cnt)
                     :let [instance-size (-> layouter (.layout cd) (.instanceSize))]]
                 {:class (.prettyName cd)
                  :instances (long cnt)
                  :size (long instance-size)
                  :sum-size (long (* cnt instance-size))})]
      (.println pw)
      (.println pw (.toString layouter))
      (.println pw)
      (print-table pw rows :instances)
      (print-table pw rows :size)
      (print-table pw rows :sum-size))
    (.flush pw)
    (.toString writer)))

(defn- safe-filename [filename]
  (-> (or filename "heapdump.hprof")
      (string/replace #"[^A-Za-z0-9._-]" "_")))

(defn- send-json! [channel payload]
  (send! channel (json/write-str payload)))

(defn handle-heapdump-ws [req]
  (with-channel req channel
    (let [state (atom {:stream nil
                       :path nil
                       :uuid nil
                       :bytes 0
                       :last-progress 0})]
      (on-receive
       channel
       (fn [data]
         (cond
           (string? data)
           (let [{:keys [type filename]} (json/read-str data :key-fn keyword)]
             (case type
               "start"
               (let [uuid (str (UUID/randomUUID))
                     temp-dir (env/temp-dir)
                     file-name (safe-filename filename)
                     path (str temp-dir "/" uuid "-" file-name)]
                 (io/make-parents path)
                 (when-let [stream (:stream @state)]
                   (.close stream))
                 (reset! state {:stream (BufferedOutputStream. (FileOutputStream. path))
                                :path path
                                :uuid uuid
                                :bytes 0
                                :last-progress 0})
                 (send-json! channel {:type "ready" :uuid uuid}))

               "finish"
               (let [{:keys [stream path uuid bytes]} @state]
                 (when stream
                   (.flush stream)
                   (.close stream))
                 (send-json! channel {:type "processing" :bytes bytes})
                 (future
                   (try
                     (let [text (heapdump-stats-text path)]
                       (send-json! channel {:type "stats" :uuid uuid :text text}))
                     (catch Exception e
                       (log/error e "Failed to compute heap dump stats")
                       (send-json! channel {:type "error" :message (.getMessage e)}))
                     (finally
                       (when path
                         (.delete (File. path)))))))

               (send-json! channel {:type "error" :message "Unknown command"})))

           (instance? (Class/forName "[B") data)
           (let [{:keys [stream bytes last-progress]} @state]
             (if stream
               (let [new-bytes (+ bytes (alength ^bytes data))]
                 (.write stream ^bytes data)
                 (swap! state assoc :bytes new-bytes)
                 (when (>= (- new-bytes last-progress) (* 5 1024 1024))
                   (swap! state assoc :last-progress new-bytes)
                   (send-json! channel {:type "progress" :bytes new-bytes})))
               (send-json! channel {:type "error" :message "Upload not initialized"})))

           :else
           (send-json! channel {:type "error" :message "Unsupported frame"}))))
      (on-close
       channel
       (fn [status]
         (when-let [stream (:stream @state)]
           (try
             (.close stream)
             (catch Exception _)))
         (log/info (str "Heapdump websocket closed: " status)))))))
