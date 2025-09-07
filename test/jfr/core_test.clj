(ns jfr.core-test
  (:require [clojure.test :refer :all]
            [jfr.core :refer :all]
            [jfr.utils :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

 (assert (= 1 (if-nil (fn [] nil)
                       #(+ 1)
                       (fn [] 2))))