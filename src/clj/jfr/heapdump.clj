(ns jfr.heapdump
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [jfr.environ :as env])
  (:import
   (java.io File PrintWriter StringWriter)
   (org.openjdk.jol.datamodel ModelVM)
   (org.openjdk.jol.heap HeapDumpReader)
   (org.openjdk.jol.layouters HotSpotLayouter)))

(def ^:private column-order [:instances :size :sum-size])

(def ^:private column-labels
  {:instances "INSTANCES"
   :size "SIZE"
   :sum-size "SUM SIZE"
   :class "CLASS"})

(defn- table-print-first []
  (or (some-> (System/getProperty "printFirst") Integer/parseInt)
      30))

(defn- format-row [^PrintWriter pw row]
  (doseq [key column-order]
    (.printf pw " %,15d" (long (get row key))))
  (.printf pw "    %s%n" (str (:class row))))

(defn- sum-columns [rows]
  (reduce (fn [acc row]
            (reduce (fn [inner key]
                      (update inner key + (get row key)))
                    acc
                    column-order))
          {:instances 0 :size 0 :sum-size 0}
          rows))

(defn- print-table
  [^PrintWriter pw rows sort-key]
  (let [sorted-rows (if (= sort-key :class)
                      (sort-by :class rows)
                      (sort-by sort-key #(compare %2 %1) rows))
        print-first (table-print-first)
        [head tail] (split-at print-first sorted-rows)
        tops (sum-columns head)
        sums (sum-columns sorted-rows)]
    (.println pw "=== Class Histogram")
    (.println pw)
    (.println pw (format "Table is sorted by \"%s\"."
                         (get column-labels sort-key)))
    (when (not= print-first Integer/MAX_VALUE)
      (.println pw (format "Printing first %d lines. Use -DprintFirst=# to override." print-first)))
    (.println pw)
    (doseq [key column-order]
      (.printf pw " %15s" (get column-labels key)))
    (.println pw "    CLASS")
    (.println pw "------------------------------------------------------------------------------------------------")
    (doseq [row head]
      (format-row pw row))
    (when (seq tail)
      (doseq [_ column-order]
        (.printf pw " %15s" "..."))
      (.printf pw "    ...%n")
      (doseq [key column-order]
        (.printf pw " %,15d" (long (- (get sums key) (get tops key)))))
      (.printf pw "    <other>%n"))
    (.println pw "------------------------------------------------------------------------------------------------")
    (doseq [key column-order]
      (.printf pw " %,15d" (long (get sums key))))
    (.printf pw "    <total>%n")
    (.println pw)))

(defn- get-vm-version []
  (try
    (Integer/parseInt (System/getProperty "java.specification.version"))
    (catch Exception _
      8)))

(defn class-name
  "Return a display-friendly class name from JOL ClassData."
  [class-data]
  (.name class-data))

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
                 {:class (class-name cd)
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

(defn handle-heapdump-upload [{:keys [params]}]
  (let [file-param (get params "file")
        tempfile (:tempfile file-param)
        filename (:filename file-param)]
    (if (and tempfile filename)
      (let [temp-dir (env/temp-dir)
            safe-name (safe-filename filename)
            path (str temp-dir "/" safe-name)]
        (io/make-parents path)
        (try
          (with-open [in (io/input-stream tempfile)
                      out (io/output-stream path)]
            (io/copy in out))
          (let [text (heapdump-stats-text path)]
            {:status 200
             :headers {"Content-Type" "text/plain; charset=utf-8"}
             :body text})
          (catch Exception e
            (log/error e "Failed to compute heap dump stats")
            {:status 500
             :headers {"Content-Type" "application/json"}
             :body (str "{\"error\":\"" (string/replace (or (.getMessage e) "Unknown error") #"\"" "\\\"") "\"}")})
          (finally
            (when path
              (.delete (File. path))))))
      {:status 400
       :headers {"Content-Type" "application/json"}
       :body "{\"error\":\"Missing heapdump file\"}"})))
