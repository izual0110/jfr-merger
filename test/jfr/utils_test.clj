(ns jfr.utils-test
  (:require [clojure.test :refer :all]
            [jfr.utils :refer :all]))

(deftest if-nil-test
  (is (= 1 (if-nil (constantly nil)
                   (constantly 1)
                   (constantly 2))))
  (is (= :value (if-nil (constantly :value))))
  (is (nil? (if-nil (constantly nil))))
  (is (false? (if-nil (constantly nil) (constantly false))))
  (let [calls (atom 0)]
    (is (= :ok (if-nil (constantly nil)
                         (fn [] (swap! calls inc) :ok)
                         (fn [] (swap! calls dec) :should-not-run))))
    (is (= 1 @calls))))

(deftest normalize-vector-test
  (is (= [1 2] (normalize-vector [1 2])))
  (is (= [1] (normalize-vector 1))))

(deftest ns-to-utc-test
  (is (= "1970-01-01T00:00Z" (ns-to-utc 0)))
  (is (= "1970-01-01T00:00:01Z" (ns-to-utc 1000000000))))
