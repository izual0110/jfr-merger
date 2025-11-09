(ns jfr.core-test
  (:require [clojure.test :refer :all]
            [jfr.core :as core]))

(deftest index-html-served
  (let [response (core/app {:request-method :get
                            :uri "/index.html"})
        body (:body response)
        text (if (string? body)
               body
               (slurp body))]
    (is (= 200 (:status response)))
    (is (seq text))))
