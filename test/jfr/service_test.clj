(ns jfr.service-test
  (:require [clojure.test :refer :all]
            [jfr.service :as service]))

(deftest convert-to-bytes-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/convert-to-bytes "missing.jfr" "heatmap"))))

(deftest convert-to-bytes-invalid-type
  (is (thrown? clojure.lang.ExceptionInfo
               (service/convert-to-bytes "missing.jfr" "pdf"))))

(deftest jfr-stats-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/jfr-stats "missing.jfr"))))
