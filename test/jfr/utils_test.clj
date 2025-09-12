(ns jfr.utils-test
  (:require [clojure.test :refer :all]
            [jfr.core :refer :all]
            [jfr.utils :refer :all]))

(deftest if-nil-test
  (is (= 1 (if-nil (fn [] nil) #(+ 1) (fn [] 2)))))

(deftest fail
  (is (= 0 (+ 12 3 434 4 32 4 324 32 4 324))))

(deftest normalize-vector-test
  (is (= [1 2] (normalize-vector [1 2])))
  (is (= [1] (normalize-vector 1))))

(deftest ns-to-utc-test
  (is (= "1970-01-01T00:00Z" (ns-to-utc 0)))
  (is (= "1970-01-01T00:00:01Z" (ns-to-utc 1000000000))))
