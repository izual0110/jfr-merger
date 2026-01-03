(ns jfr.detector.worker
  (:require [clojure.tools.logging :as log])
  (:import (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(defonce ^LinkedBlockingQueue queue (LinkedBlockingQueue.))
(defonce worker-thread (atom nil))

(defn- run-task [task]
  (try
    (task)
    (catch InterruptedException _
      (.interrupt (Thread/currentThread)))
    (catch Throwable t
      (log/error (str "Detector task failed: " (.getMessage t)) t))))

(defn- worker-loop []
  (try
    (loop []
      (when-let [task (.poll queue 5000 TimeUnit/MILLISECONDS)]
        (run-task task))
      (when (some? @worker-thread)
        (recur)))
    (catch InterruptedException e
      (log/warn "Detector worker loop interrupted" e))))

(defn start! []
  (when (nil? @worker-thread)
    (let [thread (Thread. worker-loop)]
      (.setName thread "jfr-detector-worker")
      (.setDaemon thread true)
      (.start thread)
      (reset! worker-thread thread))))

(defn stop! []
  (when-let [thread @worker-thread]
    (reset! worker-thread nil)
    (.interrupt thread)))

(defn enqueue! [task]
  (start!)
  (.put queue task))
