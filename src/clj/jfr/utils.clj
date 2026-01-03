(ns jfr.utils
  (:import (java.time Instant ZonedDateTime ZoneOffset)))

(defn if-nil
  "Invokes each thunk in order and returns the first non-nil result.
  Returns nil when every thunk yields nil."
  ([f]
   (f))
  ([f & fs]
   (loop [current f
          remaining fs]
     (let [value (current)]
       (if (nil? value)
         (if-let [next-fn (first remaining)]
           (recur next-fn (rest remaining))
           nil)
         value)))))

(defn normalize-vector [v]
  (if (vector? v) v [v]))

(defn ns-to-utc
  "Converts nanoseconds since epoch to UTC ISO string."
  [ns]
  (-> (Instant/ofEpochMilli (long (/ ns 1000000)))
      (ZonedDateTime/ofInstant ZoneOffset/UTC)
      (.toString)))
