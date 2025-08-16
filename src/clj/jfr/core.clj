(ns jfr.core
  (:use [compojure.route :only [files not-found resources]]
        [compojure.core :only [defroutes GET POST DELETE ANY context]]
        [org.httpkit.server :refer [run-server]]
        [hiccup.core :refer [html]]
        [jfr.storage :as storage])
  (:import [one.convert JfrToHeatmap])
  (:gen-class))

(defn index [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (html [:a {:href "/index.html"} "index"])})

;; (System/getProperty "user.dir") 

;(JfrToHeatmap/convert "input" "output" nil)

(defn generate-heatmap [body]
  {:status  200
  :headers {"Content-Type" "text/html"}
  :body (str (java.util.UUID/randomUUID))})

(defroutes app
  (GET "/" [] index)
  (POST "/api/heatmap" {body :body} (generate-heatmap body))
  (GET "/api/heatmap/:uuid" [uuid] uuid)
  (resources "/"))

(defonce server (atom nil))

(defn stop-server []
  (storage/destroy)
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (println "http://localhost:8080/index.html")
  (storage/init)
  (reset! server (run-server #'app {:port 8080})))


(-main)
;; (stop-server)
