(ns jfr.core-test
  (:import [java.util UUID]
           [java.io File])
  (:require [clojure.test :refer :all]
            [jfr.environ :as env]
            [jfr.core :as core]
            [org.httpkit.client :as http-client]))

(deftest index-html-served
  (let [response (core/app {:request-method :get
                            :uri "/index.html"})
        body (:body response)
        text (if (string? body)
               body
               (slurp body))]
    (is (= 200 (:status response)))
    (is (seq text))))

(deftest start-stop-server
  (with-redefs [env/get-jfr-data-path (fn [] (.getAbsolutePath (File. (str "target/jfr-test-db-" (UUID/randomUUID)))))]
    (let [original-server @core/server
          port 8181]
      (try
        (core/start-server port)
        (is (fn? @core/server))
        (loop [attempts 10]
          (let [response (try
                           @(http-client/get (str "http://localhost:" port "/index.html")
                                             {:timeout 2000})
                           (catch Exception _ nil))]
            (cond
              (= 200 (:status response))
              (do
                (is (seq (:body response)))
                (is true))

              (zero? attempts)
              (is false (str "Unexpected status: " (:status response)))

              :else
              (do
                (Thread/sleep 100)
                (recur (dec attempts))))))
        (catch Exception e
          (is false (str "Unexpected exception: " e)))
        (finally
          (core/stop-server)
          (is (nil? @core/server))
          (reset! core/server original-server))))))
