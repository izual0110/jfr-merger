(ns jfr.core-test
  (:import [java.io ByteArrayInputStream File]
           [java.nio.charset StandardCharsets]
           [java.util UUID])
  (:require [aleph.http :as http]
            [clojure.data.json :as json]
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
  (with-redefs [jfr.service/load-history (fn [] [{:uuid "abc" :name "demo"}])]
    (let [get-response (core/app {:request-method :get
                                  :uri "/api/history"})
          clear-response (core/app {:request-method :post
                                    :uri "/api/clear"})]
      (is (= 200 (:status get-response)))
      (is (= 201 (:status clear-response))))))

(deftest heapdump-history-endpoint
  (with-redefs [jfr.heapdump/load-heapdump-history (fn [] [{:name "heap.hprof" :created-at 1700000000000 :stats "ok"}])]
    (let [response (core/app {:request-method :get
                              :uri "/api/history-heapdump-stats"})]
      (is (= 200 (:status response)))
      (is (.contains (str (:body response)) "heap.hprof")))))

(defn- json-request [body]
  {:request-method :post
   :uri "/api/filesystem/process"
   :headers {"content-type" "application/json"}
   :body (ByteArrayInputStream. (.getBytes (json/write-str body) StandardCharsets/UTF_8))})

(deftest filesystem-jfr-endpoint
  (with-redefs [jfr.service/generate-artifacts-from-path (fn [path options]
                                                           (is (= "/tmp/profile.jfr" path))
                                                           (is (= {:add-flame? true :add-detector? false} options))
                                                           "uuid-1")]
    (let [response (core/app (json-request {:path "/tmp/profile.jfr"}))
          body (json/read-str (str (:body response)) :key-fn keyword)]
      (is (= 201 (:status response)))
      (is (= "jfr" (:type body)))
      (is (= "uuid-1" (:uuid body)))
      (is (= "/api/convertor/uuid-1" (get-in body [:links :heatmap]))))))

(deftest filesystem-heapdump-endpoint
  (with-redefs [jfr.heapdump/handle-heapdump-path (fn [path]
                                                    (is (= "/tmp/dump.hprof" path))
                                                    "heap stats")]
    (let [response (core/app (json-request {:path "/tmp/dump.hprof"
                                            :type "heapdump"}))
          body (json/read-str (str (:body response)) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "heapdump" (:type body)))
      (is (= "heap stats" (:stats body))))))

(deftest filesystem-endpoint-rejects-unknown-type
  (let [response (core/app (json-request {:path "/tmp/file.bin"
                                          :type "unknown"}))
        body (json/read-str (str (:body response)) :key-fn keyword)]
    (is (= 400 (:status response)))
    (is (.contains (:error body) "Unsupported file type"))))
