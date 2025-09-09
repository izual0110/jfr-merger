(ns jfr.utils-test
  (:require [clojure.test :refer :all]
            [jfr.core :refer :all]
            [jfr.utils :refer :all]))

(deftest if-nil-test
  (is (= 1 (if-nil (fn [] nil) #(+ 1) (fn [] 2)))))