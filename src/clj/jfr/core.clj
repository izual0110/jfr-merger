(ns jfr.core
  (:import (java.util UUID)
           (one.convert JfrToHeatmap Arguments)
           (java.io ByteArrayOutputStream)
           (one.jfr JfrReader))
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

(defn convert-to-bytes
  "Converts JFR input file to heatmap bytes using JfrToHeatmap, returns byte array."
  [input type]
  (let [args (Arguments. (into-array String ["--output" "heatmap" type input]))
        baos (ByteArrayOutputStream.)]
    (with-open [jfr (JfrReader. input)]
      (let [converter (JfrToHeatmap. jfr args)]
        (.convert converter)
        (.dump converter baos)))
    (.toByteArray baos)))

(defn normalize-vector [v]
  (if (vector? v) v [v]))

(defn generate-heatmap [{:keys [params]}]
  (let [uuid (str (UUID/randomUUID))
        merged-path (str temp-dir "/" uuid ".jfr")
        files (->> (get params "files")
                   normalize-vector
                   (filter #(and (map? %) (contains? % :tempfile))))]
    (io/make-parents merged-path)
    (println "UUID:" uuid "\n\t\tFiles to merge:" files )
    (with-open [out (io/output-stream merged-path)]
      (doseq [file files]
        (with-open [in (io/input-stream (:tempfile file))]
          (io/copy in out))))
    (let [all-bytes (convert-to-bytes merged-path "")
          cpu-bytes (convert-to-bytes merged-path "--cpu")
          alloc-bytes (convert-to-bytes merged-path "--alloc")]
      (storage/save-bytes uuid all-bytes)
      (storage/save-bytes (str uuid "-cpu") cpu-bytes)
      (storage/save-bytes (str uuid "-alloc") alloc-bytes)
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
  (reset! server (run-server #'app {:port 8080 :max-body (* 2 1024 1024 1024)})))


;; (-main)
;; (stop-server)
