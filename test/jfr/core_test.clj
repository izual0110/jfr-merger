(ns jfr.core-test
  (:import [io.netty.handler.ssl.util SelfSignedCertificate]
           [java.io File]
           [java.security.cert X509Certificate]
           [java.util Arrays UUID]
           [javax.net.ssl SSLContext TrustManager X509TrustManager]
           [okhttp3 OkHttpClient OkHttpClient$Builder Protocol Request Request$Builder])
  (:require [clojure.test :refer :all]
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
          cert (SelfSignedCertificate. "localhost")
          trust-manager (reify X509TrustManager
                          (checkClientTrusted [_ _ _])
                          (checkServerTrusted [_ _ _])
                          (getAcceptedIssuers [_] (make-array X509Certificate 0)))
          client-ssl-context (doto (SSLContext/getInstance "TLS")
                               (.init nil (into-array TrustManager [trust-manager]) nil))
          client (-> (OkHttpClient$Builder.)
                     (.sslSocketFactory (.getSocketFactory client-ssl-context) trust-manager)
                     (.hostnameVerifier (reify javax.net.ssl.HostnameVerifier
                                          (verify [_ _ _] true)))
                     (.protocols (Arrays/asList (into-array Protocol [Protocol/HTTP_2 Protocol/HTTP_1_1])))
                     (.build))
          request (-> (Request$Builder.)
                      (.url (str "https://localhost:" port "/index.html"))
                      (.get)
                      (.build))]
      (try
        (core/start-server port {:http2? true
                                 :ssl-context {:certificate-chain (.certificate cert)
                                               :private-key (.privateKey cert)}})
        (is (some? @core/server))
        (loop [attempts 10]
          (let [response (try
                           (.execute (.newCall client request))
                           (catch Exception _ nil))]
            (cond
              (and response (= 200 (.code response)))
              (try
                (is (seq (.string (.body response))))
                (is (= Protocol/HTTP_2 (.protocol response)))
                (finally
                  (.close response)))

              (zero? attempts)
              (is false (str "Unexpected status: " (when response (.code response))))

              :else
              (do
                (when response
                  (.close response))
                (Thread/sleep 100)
                (recur (dec attempts))))))
        (catch Exception e
          (is false (str "Unexpected exception: " e)))
        (finally
          (.delete cert)
          (core/stop-server)
          (is (nil? @core/server))
          (reset! core/server original-server))))))
