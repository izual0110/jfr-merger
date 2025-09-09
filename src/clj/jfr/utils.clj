(ns  jfr.utils
  (:import (java.time Instant ZonedDateTime ZoneOffset)))

(defn if-nil [f & ns]
  (let [v (f)]
    (cond
      (not (nil? v)) v
      (some? ns) (apply if-nil ns)
      :else v)))

(defn normalize-vector [v]
  (if (vector? v) v [v]))

(defn ns-to-utc
  "Converts nanoseconds since epoch to UTC ISO string."
  [ns]
  (-> (Instant/ofEpochMilli (long (/ ns 1000000)))
      (ZonedDateTime/ofInstant ZoneOffset/UTC)
      (.toString)))