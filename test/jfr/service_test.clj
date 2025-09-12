(ns jfr.service-test
  (:require [clojure.test :refer :all]
            [jfr.service :as service]))

(deftest convert-to-bytes-missing-file
  (is (thrown? java.io.FileNotFoundException
               (service/convert-to-bytes "missing.jfr" ""))))

(deftest jfr-stats-missing-file
  (is (thrown? java.io.FileNotFoundException
               (service/jfr-stats "missing.jfr"))))
