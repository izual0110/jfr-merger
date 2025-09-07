(ns jfr.storage
  (:import (org.rocksdb RocksDB Options CompressionType))
  (:require [jfr.environ :as env]
            [clojure.java.io :as io]))

(RocksDB/loadLibrary)

(def db-path (env/get-jfr-data-path))
(defonce db-atom (atom nil))

(defn open-db 
  "Opens RocksDB with settings if not already open"
  [] (when (nil? @db-atom)
    (let [options (doto (Options.)
                    (.setCreateIfMissing true)
                    (.setEnableBlobFiles true)
                    (.setMinBlobSize (long (* 512 1024)))
                    (.setBlobCompressionType CompressionType/ZSTD_COMPRESSION))]
      (io/make-parents db-path)
      (reset! db-atom (RocksDB/open options db-path)))))

(defn close-db 
  "Closes the global RocksDB instance"
  [] (when-let [db @db-atom]
    (.close db)
    (reset! db-atom nil)))

(defn save-bytes [key bytes]
  (when-let [db @db-atom]
    (locking db
      (.put db (.getBytes key) bytes))))

(defn load-bytes [key]
  (when-let [db @db-atom]
    (.get db (.getBytes key))))

(defn delete [key]
  (when-let [db @db-atom]
    (locking db
      (.delete db (.getBytes key)))))

(defn delete-db []
  (let [options (Options.)]
    (RocksDB/destroyDB db-path options)
    (println "Database" db-path "was deleted")))

(defn stats []
  (when-let [db @db-atom]
    (let [props ["rocksdb.estimate-num-keys"
                 "rocksdb.estimate-live-data-size"
                 "rocksdb.total-sst-files-size"
                 "rocksdb.num-files-at-level0"
                 "rocksdb.size-all-mem-tables"]] 
           (into {} (map (fn [p] [p (.getProperty db p)]) props)))))

(defn get-all-keys []
  (when-let [db @db-atom]
    (let [it (.newIterator db)]
      (try
        (.seekToFirst it)
        (loop [keys []]
          (if (.isValid it)
            (let [key (String. (.key it))]
              (.next it)
              (recur (conj keys key)))
            keys))
        (finally
          (.close it))))))

(defn init []
  (open-db))
(defn destroy []
  (close-db))
