(ns jfr.heapdump
  (:require
   [clojure.java.io :as io]
   [jfr.environ :as env])
  (:import
   (java.util UUID)
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

(defn- format-row [^PrintWriter pw row]
  (doseq [key column-order]
    (.print pw (format " %,15d" (long (get row key)))))
  (.print pw (format "    %s%n" (str (:class row)))))

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
        print-first (env/get-heapdump-print-first)
        [head tail] (split-at print-first sorted-rows)
        tops (sum-columns head)
        sums (sum-columns sorted-rows)]
    (.println pw (str "=== Class Histogram. Printing first " print-first " lines."))
    (.println pw)
    (.println pw (format "Table is sorted by \"%s\"."
                         (get column-labels sort-key)))
    (.println pw)
    (doseq [key column-order]
      (.print pw (format " %15s" (get column-labels key))))
    (.println pw "    CLASS")
    (.println pw "------------------------------------------------------------------------------------------------")
    (doseq [row head]
      (format-row pw row))
    (when (seq tail)
      (doseq [_ column-order]
        (.print pw (format " %15s" "...")))
      (.print pw "    ...\n")
      (doseq [key column-order]
        (.print pw (format " %,15d" (long (- (get sums key) (get tops key))))))
      (.print pw "    <other>\n"))
    (.println pw "------------------------------------------------------------------------------------------------")
    (doseq [key column-order]
      (.print pw (format " %,15d" (long (get sums key)))))
    (.print pw "    <total>\n")
    (.println pw)))

(defn heapdump-stats-text
  "Return the jol-cli heapdump-stats output for the provided heap dump."
  [path]
  (let [layouter (HotSpotLayouter. (ModelVM.) (env/get-vm-version))
        writer (StringWriter.)
        pw (PrintWriter. writer)]
    (.println pw (str "Heap Dump: " path))
    (let [reader (HeapDumpReader. (io/file path))
          data (.parse reader)
          rows (for [cd (.keys data)
                     :let [cnt (.count data cd)]
                     :when (pos? cnt)
                     :let [instance-size (-> layouter (.layout cd) (.instanceSize))]]
                 {:class (.name cd)
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
  (str (UUID/randomUUID) (if (.endsWith filename ".gz") ".hprof.gz" ".hprof")))

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
          (heapdump-stats-text path)
          (finally
            (when path
              (.delete (File. path))))))
      (throw (IllegalArgumentException. "Missing heapdump file")))))

