(ns jfr.core
  (:require 
   [jfr.storage :as storage]
   [jfr.service :as service]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [compojure.route :refer [resources]]
   [compojure.core :refer [defroutes GET POST]]
   [org.httpkit.server :refer [run-server]]
   [hiccup2.core :as h]
   [clojure.data.json :as json])
  (:gen-class))

(defn index [_]
  {:status  302
   :headers {"Location" "/index.html"}
   :body    (str (h/html [:a {:href "/index.html"} "index"]))})

(defn get-heatmap [uuid]
  (let [data (storage/load-bytes uuid)]
    (if data
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body data}
      {:status 404
       :body "Heatmap not found"})))

(defroutes handlers
  (GET "/" [] index)
  (POST "/api/heatmap" req (let [[uuid stats] (service/generate-heatmap req)] 
                             {:status 200 
                              :headers {"Content-Type" "application/json"} 
                              :body (json/write-str {:uuid uuid :stats stats})}))
  (GET "/api/heatmap/:uuid" [uuid] (get-heatmap uuid))
  (resources "/"))

(defonce server (atom nil))

(defn stop-server []
  (storage/destroy)
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(def app
  (-> handlers
      wrap-multipart-params))

(defn -main
  "I don't do a whole lot ... yet."
  [& _]
  (println "Hello, World!")
  (println "http://localhost:8080/index.html")
  (storage/init)
  (reset! server (run-server #'app {:port 8080 :max-body (* 1 1024 1024 1024)})))


;; (-main)
;; (stop-server)
