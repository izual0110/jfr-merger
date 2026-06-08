(ns jfr.heapdump-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [jfr.heapdump :as heapdump])
  (:import
   (com.sun.management HotSpotDiagnosticMXBean)
   (java.lang.management ManagementFactory)
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
      (let [output (heapdump/heapdump-stats-text path)
            path-output (heapdump/handle-heapdump-upload {:params {"path" path}})]
        (is (string? output))
        (is (.contains output "Heap Dump:"))
        (is (.contains output "=== Class Histogram"))
        (is (string? path-output))
        (is (.contains path-output "Heap Dump:"))
        (is (.contains path-output "=== Class Histogram")))
      (finally
        (io/delete-file path true)
        (io/delete-file (.toFile temp-dir) true)))))


(deftest handle-heapdump-path-requires-readable-file
  (is (thrown-with-msg? IllegalArgumentException
                        #"readable file"
                        (heapdump/handle-heapdump-upload {:params {"path" "/tmp/definitely-missing-heapdump.hprof"}}))))
