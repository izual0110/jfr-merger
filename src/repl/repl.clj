(ns repl
  (:require
   [jfr.core :as core]
   [jfr.detector.detector :as detector]
   [jfr.detector.report :as report]
   [hiccup2.core :as h]
   [clojure.tools.logging :as log])
  (:import [java.lang NullPointerException]))


(core/stop-server)
(core/-main)

(log/error "Simulated NullPointerException" (NullPointerException.))

(defn test-detect []
  (let [;; jfr "test/BadPatternsDemo.jfr"
        jfr "test/SmallBadPatternsDemo.jfr"
        hits (detector/detect-patterns {:jfr-path jfr :alloc-only? false})
        _ (log/infof "Detected hits: %d" (count hits))
        summary (detector/summarize hits {:top-stacks 30})]
    (log/infof "== Quick-fix patterns in %s" jfr)
    (doseq [{:keys [id title count alloc-bytes top-stacks advice]} summary]
      (log/info (format "%n-- %s : %s" id title))
      (log/infof "   matches: %d%s"
                 count
                 (if (some? alloc-bytes) (str "  alloc-bytes≈" alloc-bytes) ""))
      (log/infof "   advice: %s" advice)
      (log/info (str " " top-stacks)))))

(test-detect)

(defn test-report []
  (let [;; jfr "test/BadPatternsDemo.jfr"
        jfr "test/SmallBadPatternsDemo.jfr"
        hits (detector/detect-patterns {:jfr-path jfr :alloc-only? false})
        _ (log/infof "Detected hits: %d" (count hits))
        summary (detector/summarize hits {:top-stacks 30})]
    (report/report-div {:uuid "test-uuid-1234"
                        :status "completed"
                        :scheduled-at 0
                        :started-at 0
                        :finished-at 0
                        :hit-count (count hits)
                        :summary summary})))


(def report
  (test-report))

report

(str (h/html report))

(report/report-div
 {:uuid "1234-5678"
  :status "completed"
  :scheduled-at 1620000000000
  :started-at 1620000001000
  :finished-at 1620000005000
  :hit-count 42
  :summary [{:id 1
             :title "Example Issue"
             :advice "Do something about it."
             :count 10
             :alloc-bytes 20480
             :top-stacks ["com.example.Foo.bar(Foo.java:42)"
                          "com.example.Baz.qux(Baz.java:99)"]
            ;;  :top-stacks ["123"]
             }]})

count

(str (count (seq ["123"])))
