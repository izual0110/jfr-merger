(ns jfr.heapdump-test
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [jfr.heapdump :as heapdump]
   [jfr.storage :as storage])
  (:import
   (com.sun.management HotSpotDiagnosticMXBean)
   (java.lang.management ManagementFactory)
   (java.nio.charset StandardCharsets)
   (java.nio.file Files)))

(defn- ^HotSpotDiagnosticMXBean hotspot-diagnostic []
  (ManagementFactory/newPlatformMXBeanProxy
   (ManagementFactory/getPlatformMBeanServer)
   "com.sun.management:type=HotSpotDiagnostic"
   HotSpotDiagnosticMXBean))

(deftest heapdump-stats-text-from-dump
  (let [temp-dir (Files/createTempDirectory "jfr-heapdump-test"
                                            (make-array java.nio.file.attribute.FileAttribute 0))
        path (-> (.resolve temp-dir "dump.hprof")
                 (.toFile)
                 (.getAbsolutePath))]
    (try
      (.dumpHeap (hotspot-diagnostic) path true)
      (let [output (heapdump/heapdump-stats-text path)]
        (is (string? output))
        (is (.contains output "Heap Dump:"))
        (is (.contains output "=== Class Histogram")))
      (finally
        (io/delete-file path true)
        (io/delete-file (.toFile temp-dir) true)))))

(deftest heapdump-history-roundtrip
  (let [db (atom {})]
    (with-redefs [storage/get-all-keys (fn
                                         ([] (storage/get-all-keys nil))
                                         ([prefix]
                                          (filter #(clojure.string/starts-with? % prefix)
                                                  (keys @db))))
                  storage/load-bytes (fn [k] (get @db k))
                  storage/save-bytes (fn [k v] (swap! db assoc k v))]
      (let [entry-1 {:name "a.hprof" :created-at 1700000000000 :stats "stats-a"}
            entry-2 {:name "b.hprof" :created-at 1700000001000 :stats "stats-b"}
            bytes-1 (.getBytes (json/write-str entry-1) StandardCharsets/UTF_8)
            bytes-2 (.getBytes (json/write-str entry-2) StandardCharsets/UTF_8)]
        (storage/save-bytes "meta-heapdump-history/1700000000000|a.hprof" bytes-1)
        (storage/save-bytes "meta-heapdump-history/1700000001000|b.hprof" bytes-2)
        (let [history (heapdump/load-heapdump-history)]
          (is (= 2 (count history)))
          (is (= "b.hprof" (:name (first history))))
          (is (= "stats-a" (:stats (second history)))))))))
