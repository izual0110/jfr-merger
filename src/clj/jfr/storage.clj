(ns jfr.storage
  (:import (org.rocksdb RocksDB Options CompressionType)
           (java.nio.charset StandardCharsets))
  (:require [jfr.environ :as env]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(RocksDB/loadLibrary)

(defn- get-db-path [] (env/get-jfr-data-path))
(defonce db-atom (atom nil))

(defn open-db 
  "Opens RocksDB with settings if not already open"
  [] (when (nil? @db-atom)
    (let [options (doto (Options.)
                    (.setCreateIfMissing true)
                    (.setEnableBlobFiles true)
                    (.setMinBlobSize (long (* 512 1024)))
                    (.setBlobCompressionType CompressionType/ZSTD_COMPRESSION))
          db-path (get-db-path)]
      (log/info (str "Opening RocksDB at " db-path))
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
  (let [options (Options.)
        db-path (get-db-path)]
    (RocksDB/destroyDB db-path options)
    (log/info (str "Database " db-path " was deleted"))))

(defn stats []
  (when-let [db @db-atom]
    (let [props ["rocksdb.estimate-num-keys"
                 "rocksdb.estimate-live-data-size"
                 "rocksdb.total-sst-files-size"
                 "rocksdb.num-files-at-level0"
                 "rocksdb.size-all-mem-tables"]] 
           (into {} (map (fn [p] [p (.getProperty db p)]) props)))))

(defn starts-with-bytes? [^bytes value ^bytes prefix]
  (and (<= (alength prefix) (alength value))
       (loop [i 0]
         (cond
           (= i (alength prefix)) true
           (not= (aget value i) (aget prefix i)) false
           :else (recur (inc i))))))

(defn get-all-keys
  ([]
   (get-all-keys nil))

  ([prefix]
   (when-let [db @db-atom]
     (let [it (.newIterator db)
           prefix-bytes (when prefix
                          (.getBytes ^String prefix StandardCharsets/UTF_8))]
       (try
         (if prefix-bytes
           (.seek it prefix-bytes)
           (.seekToFirst it))

         (loop [keys []]
           (if (.isValid it)
             (let [key-bytes (.key it)]
               (if (or (nil? prefix-bytes)
                       (starts-with-bytes? key-bytes prefix-bytes))
                 (let [key (String. key-bytes StandardCharsets/UTF_8)]
                   (.next it)
                   (recur (conj keys key)))

                 keys))
             keys))

         (finally
           (.close it)))))))

(defn init []
  (open-db))
(defn destroy []
  (close-db))
