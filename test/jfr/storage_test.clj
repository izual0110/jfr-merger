(ns jfr.storage-test
  (:import [java.util UUID]
           [java.io File])
  (:require [clojure.test :refer :all]
            [jfr.storage :as storage]))

(deftest test-storage
  (testing "RocksDB storage operations"
    (let [test-db-path (str "target/jfr-test-db-" (UUID/randomUUID))]
      (with-redefs [storage/get-db-path (fn [] test-db-path)]
        (let [key "test-key"
              value (.getBytes "test-value")]
          (storage/save-bytes key value)
          (is (nil? (storage/load-bytes key)) "Value should be nil because DB is not opened")
          (storage/delete key)
          (is (nil? (storage/load-bytes key)) "Value should be nil because DB is not opened"))

        (storage/open-db)
        (is (some? @storage/db-atom) "Database should be opened")

        (let [key "test-key"
              value (.getBytes "test-value")]
          (storage/save-bytes key value)
          (is (= (String. value) (String. (storage/load-bytes key))) "Value should be retrievable after saving")

          (storage/delete key)
          (is (nil? (storage/load-bytes key)) "Value should be nil after deletion"))

        (let [stats-map (storage/stats)]
          (is (map? stats-map) "Stats should return a map")
          (is (contains? stats-map "rocksdb.estimate-num-keys") "Stats should contain estimate-num-keys"))

        (storage/close-db)
        (is (nil? @storage/db-atom) "Database should be closed")
        (storage/delete-db)
        (is (false? (.exists (File. test-db-path))) "Database directory should be deleted")))))


