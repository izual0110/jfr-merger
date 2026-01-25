(ns jfr.core-test
  (:import [io.netty.handler.ssl.util SelfSignedCertificate]
           [java.io File]
           [java.net URI]
           [java.net.http HttpClient HttpClient$Version HttpRequest HttpResponse$BodyHandlers]
           [java.security.cert X509Certificate]
           [java.util UUID]
           [javax.net.ssl SSLContext SSLParameters TrustManager X509TrustManager])
  (:require [aleph.netty :as netty]
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
          cert (SelfSignedCertificate. "localhost")
          ssl-context (netty/ssl-server-context {:certificate-chain (.certificate cert)
                                                 :private-key (.privateKey cert)})
          trust-manager (reify X509TrustManager
                          (checkClientTrusted [_ _ _])
                          (checkServerTrusted [_ _ _])
                          (getAcceptedIssuers [_] (make-array X509Certificate 0)))
          client-ssl-context (doto (SSLContext/getInstance "TLS")
                               (.init nil (into-array TrustManager [trust-manager]) nil))
          ssl-params (doto (SSLParameters.)
                       (.setEndpointIdentificationAlgorithm nil))
          client (-> (HttpClient/newBuilder)
                     (.sslContext client-ssl-context)
                     (.sslParameters ssl-params)
                     (.version HttpClient$Version/HTTP_2)
                     (.build))
          request (-> (HttpRequest/newBuilder (URI. (str "https://localhost:" port "/index.html")))
                      (.GET)
                      (.build))]
      (try
        (core/start-server port {:http2? true :ssl-context ssl-context})
        (is (some? @core/server))
        (loop [attempts 10]
          (let [response (try
                           (.send client request (HttpResponse$BodyHandlers/ofString))
                           (catch Exception _ nil))]
            (cond
              (and response (= 200 (.statusCode response)))
              (do
                (is (seq (.body response)))
                (is (= HttpClient$Version/HTTP_2 (.version response))))

              (zero? attempts)
              (is false (str "Unexpected status: " (when response (.statusCode response))))

              :else
              (do
                (Thread/sleep 100)
                (recur (dec attempts))))))
        (catch Exception e
          (is false (str "Unexpected exception: " e)))
        (finally
          (.delete cert)
          (core/stop-server)
          (is (nil? @core/server))
          (reset! core/server original-server))))))
