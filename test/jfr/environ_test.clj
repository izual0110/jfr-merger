(ns jfr.environ-test
  (:require [clojure.test :refer :all]
            [jfr.environ :as env]))

(deftest slurp-safe-nonexistent
  (is (nil? (env/slurp-safe "no-such-file.edn"))))

(deftest file-readable?-nonexistent
  (is (false? (env/file-readable? "no-such-file.edn"))))

(deftest get-jfr-data-path-missing
  (with-redefs [env/config (atom {})]
    (is (thrown? NullPointerException (env/get-jfr-data-path)))))
