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
