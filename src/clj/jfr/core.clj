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
   [hiccup2.core :as h]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [jfr.environ :as env])
  (:gen-class))

(defn index [_]
  {:status  302
   :headers {"Location" "/index.html"}
   :body    (str (h/html [:a {:href "/index.html"} "index"]))})

(defn get-artifact [uuid content-type]
  (let [data (storage/load-bytes uuid)]
    (if data
      {:status 200
       :headers {"Content-Type" content-type}
       :body data}
      {:status 404
       :body "Artifact not found"})))

(defroutes handlers
  (GET "/" [] index)
  (GET "/api/convertor/:uuid" [uuid] (get-artifact uuid "text/html"))
  (POST "/api/convertor" req (let [[uuid stats add-flame? add-detector?] (service/generate-artifacts req)]
                               {:status 200
                                :headers {"Content-Type" "application/json"}
                                :body (json/write-str {:uuid uuid :stats stats :flame add-flame? :detector add-detector?})}))
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

(defn start-server
  ([] (start-server (env/get-server-port) {}))
  ([port] (start-server port {}))
  ([port {:keys [http2? ssl-context] :or {http2? false}}]
   (storage/init)
   (detector-worker/start!)
   (reset! server
           (http/start-server #'app
                              (cond-> {:port port
                                       :max-request-body-size Integer/MAX_VALUE}
                                http2? (assoc :http-versions [:http2]
                                              :use-h2c? true)
                                ssl-context (assoc :ssl-context ssl-context))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& _]
  (log/info "Hello, World!")
  (log/info "http://localhost:8080/index.html")
  start-server)


;; (-main)
;; (stop-server)
