(ns jfr.core
  (:require
   [jfr.storage :as storage]
   [jfr.service :as service]
   [jfr.detector.worker :as detector-worker]
   [jfr.detector.report :as report]
   [jfr.heapdump :as heapdump]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [compojure.route :refer [resources]]
   [compojure.core :refer [defroutes GET POST]]
   [aleph.http :as http]
   [aleph.netty :as netty]
   [hiccup2.core :as h]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [jfr.environ :as env])
  (:gen-class))

(defonce process-start-nanos (System/nanoTime))

(defn elapsed-ms []
  (quot (- (System/nanoTime) process-start-nanos) 1000000))

(defn index [_]
  {:status  302
   :headers {"Location" "/index.html"}
   :body    (str (h/html [:a {:href "/index.html"} "index"]))})

(defn get-artifact [uuid]
  (let [data (storage/load-bytes uuid)]
    (if data
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body data}
      {:status 404
       :body "Artifact not found"})))

(defn- parse-json-body [req]
  (when-let [body (:body req)]
    (json/read-str (slurp body) :key-fn keyword)))

(defn- history-name-response [uuid req]
  (let [{:keys [name]} (parse-json-body req)
        updated (service/save-history-name! uuid (or name ""))]
    (if updated
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str updated)}
      {:status 404
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:error "History item not found"})})))


(defn- json-response
  ([status body]
   {:status status
    :headers {"Content-Type" "application/json"}
    :body (json/write-str body)}))

(defn- bad-request-response [message]
  (json-response 400 {:error message}))

(defn- server-error-response [message]
  (json-response 500 {:error message}))

(defn- infer-filesystem-type [path]
  (let [lower-path (string/lower-case (or path ""))]
    (cond
      (string/ends-with? lower-path ".jfr") "jfr"
      (or (string/ends-with? lower-path ".hprof")
          (string/ends-with? lower-path ".hprof.gz")
          (string/ends-with? lower-path ".gz")) "heapdump")))

(defn- filesystem-process-response [req]
  (try
    (let [{:keys [path type addFlamegraph addDetector]} (parse-json-body req)
          normalized-type (or (some-> type string/lower-case)
                              (infer-filesystem-type path))]
      (case normalized-type
        "jfr"
        (let [uuid (service/generate-artifacts-from-path path {:add-flame? (true? addFlamegraph)
                                                               :add-detector? (true? addDetector)})]
          (json-response 201 {:type "jfr"
                              :uuid uuid
                              :links {:heatmap (str "/api/convertor/" uuid)
                                      :cpu (str "/api/convertor/" uuid "-cpu")
                                      :alloc (str "/api/convertor/" uuid "-alloc")}}))

        "heapdump"
        (let [stats (heapdump/handle-heapdump-path path)]
          (json-response 200 {:type "heapdump"
                              :stats stats}))

        (bad-request-response "Unsupported file type. Use a .jfr, .hprof, or .hprof.gz path, or pass type jfr/heapdump.")))
    (catch IllegalArgumentException e
      (log/error e "Failed to process filesystem path")
      (bad-request-response (.getMessage e)))
    (catch Exception e
      (log/error e "Failed to process filesystem path")
      (server-error-response (or (.getMessage e) "Unknown error")))))

(defroutes handlers
  (GET "/" [] index)
  (GET "/api/convertor/:uuid" [uuid] (get-artifact uuid))
  (POST "/api/convertor" req (let [body (service/generate-artifacts req)] {:status 201 :body body}))
  (POST "/api/filesystem/process" req (filesystem-process-response req))
  (POST "/api/heapdump" req (let [response (heapdump/handle-heapdump-upload req)]
                              (try
                                {:status 200
                                 :headers {"Content-Type" "text/plain; charset=utf-8"}
                                 :body response}
                                (catch IllegalArgumentException e
                                  (log/error e "Failed to compute heap dump stats")
                                  {:status 400
                                   :headers {"Content-Type" "application/json"}
                                   :body "{\"error\":\"Missing heapdump file\"}"})
                                (catch Exception e
                                  (log/error e "Failed to compute heap dump stats")
                                  {:status 500
                                   :headers {"Content-Type" "application/json"}
                                   :body (str "{\"error\":\"" (string/replace (or (.getMessage e) "Unknown error") #"\"" "\\\"") "\"}")}))))

  (GET "/api/detector/:uuid" [uuid]
    (if-let [result (service/detector-result uuid)]
      {:status 200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (str (h/html (report/report-div result)))}
      {:status 404
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:error "Detector result not found"})}))

  (GET "/api/storage/stats" [] {:status 200
                                :headers {"Content-Type" "application/json"}
                                :body (json/write-str (storage/stats))})
  (GET "/api/storage/keys" [] {:status 200
                               :headers {"Content-Type" "application/json"}
                               :body (json/write-str (storage/get-all-keys))})
  (GET "/api/history" [] {:status 200
                          :headers {"Content-Type" "application/json"}
                          :body (json/write-str (service/load-history))})
  (GET "/api/history-heapdump-stats" [] {:status 200
                                         :headers {"Content-Type" "application/json"}
                                         :body (json/write-str (heapdump/load-heapdump-history))})
  (POST "/api/history/:uuid/name" [uuid :as req] (history-name-response uuid req))
  (POST "/api/clear" [] (do (service/clear!) {:status 201}))
  (resources "/"))

(defonce server (atom nil))

(defn stop-server []
  (detector-worker/stop!)
  (storage/destroy)
  (when-let [active-server @server]
    (.close active-server)
    (reset! server nil)))

(def app
  (-> handlers
      wrap-multipart-params))

(def ssl
  (netty/self-signed-ssl-context "localhost"
                                 {:application-protocol-config
                                  (netty/application-protocol-config [:http2])}))

(defn start-server
  ([] (start-server (env/get-server-port) (env/get-server-http2?)))
  ([port http2?]
   (storage/init)
   (detector-worker/start!)
   (reset! server
           (http/start-server #'app {:port port
                                     ;;:max-request-body-size Integer/MAX_VALUE
                                     :http-versions (if http2? [:http2] [:http1])
                                     :force-h2c? http2?
                                     :ssl-context (if http2? ssl nil)}))))

(defn -main
  "I don't do a whole lot ... yet."
  [& _]
  (log/info "Hello, World!")
  (log/info (str "http" (if (env/get-server-http2?) "s" "") "://localhost:8080/index.html"))
  (start-server)
  (println "Application ready in" (elapsed-ms) "ms"))

;; (-main)
;; (stop-server)
