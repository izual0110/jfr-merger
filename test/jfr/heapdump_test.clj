(ns jfr.heapdump-test
  (:require
   [clojure.test :refer [deftest is]]
   [jfr.heapdump :as heapdump])
  (:import
   (org.openjdk.jol.info ClassData)))

(deftest class-name-uses-jol-name
  (is (= "com.example.Foo"
         (heapdump/class-name (ClassData. "com.example.Foo")))))
