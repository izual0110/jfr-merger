(ns jfr.service-test
  (:require [clojure.test :refer :all]
            [jfr.service :as service]))

(deftest convert-heatmap-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/convert-heatmap "missing.jfr" nil))))

(deftest convert-flamegraph-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/convert-flamegraph "missing.jfr" nil))))

(deftest jfr-stats-missing-file
  (is (thrown? java.nio.file.NoSuchFileException
               (service/jfr-stats "missing.jfr"))))

(deftest history-metadata-roundtrip
  (let [db (atom {})]
    (with-redefs [jfr.storage/get-all-keys (fn [] (keys @db))
                  jfr.storage/load-bytes (fn [k] (get @db k))
                  jfr.storage/save-bytes (fn [k v] (swap! db assoc k v))
                  jfr.storage/delete (fn [k] (swap! db dissoc k))]
      (service/save-history-item! {:uuid "u-1" :stats {:event-count 1} :flame true :detector false})
      (service/save-history-item! {:uuid "u-2" :stats {:event-count 2} :flame false :detector true :name "old"})

      (let [history (service/load-history)]
        (is (= 2 (count history)))
        (is (= #{"u-1" "u-2"} (set (map :uuid history)))))

      (service/save-history-name! "u-2" "renamed")
      (is (= "renamed" (:name (first (filter #(= "u-2" (:uuid %)) (service/load-history))))))

      (service/clear-history!)
      (is (empty? (service/load-history))))))
