(ns jfr.detector-test
  (:require [clojure.test :refer :all]
            [jfr.detector.detector :as detector]))

(defn- sample
  ([bucket stack]
   (sample bucket stack 1))
  ([bucket stack weight]
   {:timestamp-ms (* bucket 1000)
    :frames stack
    :weight weight
    :event-type "jdk.ExecutionSample"}))

(deftest analyze-problem-samples-detects-peak-ranges
  (let [stack ["app.Work.hot()" "app.Work.run()"]
        samples (concat (map #(sample % stack) [0 1 2 3 4 5])
                        [(sample 6 stack 20)
                         (sample 10 ["app.Other.idle()"] 1)])
        problems (detector/analyze-problem-samples samples {:bucket-ms 1000 :min-score 5})
        peak (first (filter #(= :peak (:type %)) problems))]
    (is (some? peak))
    (is (= stack (:stack peak)))
    (is (= [{:start-ms 6000
             :end-ms 7000
             :start "1970-01-01T00:00:06Z"
             :end "1970-01-01T00:00:07Z"}]
           (:ranges peak)))))

(deftest analyze-problem-samples-detects-periodic-ranges
  (let [stack ["app.Poller.poll()" "app.Poller.run()"]
        samples (map #(sample % stack 2) [0 3 6 9])
        problems (detector/analyze-problem-samples samples {:bucket-ms 1000 :min-score 5})
        periodic (first (filter #(= :periodic (:type %)) problems))]
    (is (some? periodic))
    (is (= 4 (count (:ranges periodic))))
    (is (= "The same stack appears in repeated bursts with a stable interval between bursts."
           (:reason periodic)))))

(deftest analyze-problem-samples-detects-constant-ranges
  (let [stack ["app.Looper.loop()" "app.Looper.run()"]
        samples (map #(sample % stack 1) (range 10))
        problems (detector/analyze-problem-samples samples {:bucket-ms 1000 :min-score 5})
        constant (first (filter #(= :constant (:type %)) problems))]
    (is (some? constant))
    (is (= [{:start-ms 0
             :end-ms 10000
             :start "1970-01-01T00:00:00Z"
             :end "1970-01-01T00:00:10Z"}]
           (:ranges constant)))))
