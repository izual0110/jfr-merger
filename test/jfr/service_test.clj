(ns jfr.service-test
  (:require [clojure.test :refer :all]
            [jfr.service :as service]))

(deftest convert-heatmap-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/convert-heatmap "missing.jfr" nil))))

(deftest convert-flamegraph-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/convert-flamegraph "missing.jfr" nil))))

(deftest jfr-stats-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/jfr-stats "missing.jfr"))))


(deftest collect-jfr-inputs-supports-filesystem-paths
  (let [tmp (java.io.File/createTempFile "service-test" ".jfr")
        path (.getAbsolutePath tmp)]
    (.deleteOnExit tmp)
    (is (= [path]
           (#'jfr.service/collect-jfr-inputs {"filePaths" path})))
    (is (= [path path]
           (#'jfr.service/collect-jfr-inputs {"filePaths" (str path "\n" path)})))))

(deftest collect-jfr-inputs-requires-existing-readable-file
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"does not exist"
                        (#'jfr.service/collect-jfr-inputs {"filePaths" "/tmp/definitely-missing-file.jfr"}))))

(deftest collect-jfr-inputs-requires-at-least-one-input
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"At least one JFR file"
                        (#'jfr.service/collect-jfr-inputs {}))))
