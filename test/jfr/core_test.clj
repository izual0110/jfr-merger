(ns jfr.core-test
  (:import [java.io File]
           [java.util UUID])
  (:require [aleph.http :as http]
            [clojure.test :refer :all]
            [jfr.environ :as env]
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

(deftest start-stop-server
  (with-redefs [env/get-jfr-data-path (fn [] (.getAbsolutePath (File. (str "target/jfr-test-db-" (UUID/randomUUID)))))]
    (let [original-server @core/server
          port 8181
          url (str "https://localhost:" port "/index.html")
          pool (http/connection-pool {:connection-options {:http-versions [:http2] :force-h2c? true :insecure? true}})
          request-opts {:pool pool :throw-exceptions false}]
      (try
        (core/start-server port true)
        (is (some? @core/server))
        (loop [attempts 10]
          (let [response (try
                           @(http/get url request-opts)
                           (catch Exception _ nil))]
            (cond
              (and response (= 200 (:status response))) (let [body (:body response)
                                                              text (if (string? body) body (slurp body))] 
                                                          (is (seq text)))
              (zero? attempts) (is false (str "Unexpected status: " (when response (:status response))))

              :else (do
                      (Thread/sleep 100)
                      (recur (dec attempts))))))
        (catch Exception e
          (is false (str "Unexpected exception: " e)))
        (finally
          (core/stop-server)
          (is (nil? @core/server))
          (reset! core/server original-server))))))


(deftest history-endpoints
  (with-redefs [jfr.service/load-history (fn [] [{:uuid "abc" :name "demo"}])
                jfr.service/save-history-name! (fn [uuid name] {:uuid uuid :name name})
                jfr.service/clear-history! (fn [] nil)]
    (let [get-response (core/app {:request-method :get
                                  :uri "/api/history"})
          post-response (core/app {:request-method :post
                                   :uri "/api/history/abc/name"
                                   :body (java.io.ByteArrayInputStream. (.getBytes "{\"name\":\"new\"}"))})
          clear-response (core/app {:request-method :post
                                    :uri "/api/history/clear"})]
      (is (= 200 (:status get-response)))
      (is (= 200 (:status post-response)))
      (is (= 200 (:status clear-response))))))
