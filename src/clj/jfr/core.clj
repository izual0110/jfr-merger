(ns jfr.core 
  (:import (java.util UUID)
           (java.nio.file Files Paths)
           (one.convert JfrToHeatmap Arguments))
  (:require [clojure.java.io :as io]
            [jfr.storage :as storage]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [jfr.environ :as env]
            [compojure.route :refer [resources]]
            [compojure.core :refer [defroutes GET POST]]
            [org.httpkit.server :refer [run-server]]
            [hiccup2.core :as h])
  (:gen-class))

(def temp-dir (env/temp-dir))

(defn index [_]
  {:status  302
   :headers {"Location" "/index.html"}
   :body    (str (h/html [:a {:href "/index.html"} "index"]))})

(defn convert [in out]
  (let[args (Arguments. (into-array String ["--output" "heatmap" in out]))]
   (JfrToHeatmap/convert in out args)))

(defn generate-heatmap [{:keys [params]}]
  (let [uuid (str (UUID/randomUUID))
        merged-path (str temp-dir "/" uuid ".jfr")
        heatmap-path (str temp-dir "/new_" uuid ".html")
        files (get params "files")
        files (->> files
                   (filter #(and (map? %) (contains? % :tempfile))))]
    (io/make-parents merged-path)
    (with-open [out (io/output-stream merged-path)]
      (doseq [file files]
        (with-open [in (io/input-stream (:tempfile file))]
          (io/copy in out))))
    (convert merged-path heatmap-path)
    (let [bytes (Files/readAllBytes (Paths/get heatmap-path (make-array String 0)))]
      (storage/save-bytes uuid bytes)
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body uuid})))

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
  (POST "/api/heatmap" req (generate-heatmap req))
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
  (reset! server (run-server #'app {:port 8080})))


;; (-main)
;; (stop-server)
