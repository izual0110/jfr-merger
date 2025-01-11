(ns jfr-merger.core
  (:use [compojure.route :only [files not-found resources]]
        [compojure.core :only [defroutes GET POST DELETE ANY context]]
        [org.httpkit.server :refer [run-server]])
  (:gen-class))

(defn index [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello from Compojure!"})

(defroutes app
  (GET "/" [] index)
  (resources "/"))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (println "http://localhost:8080")
  (reset! server (run-server #'app {:port 8080})))


(-main)
(stop-server)
