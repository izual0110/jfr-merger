(ns jfr.repl
  (:require
   [jfr.core :as core]
   [jfr.detector.detector :as detector]))


(core/stop-server)
(core/-main)

(defn test-detect []
  (let [;; jfr "test/BadPatternsDemo.jfr"
        jfr "test/SmallBadPatternsDemo.jfr"
        hits (detector/detect-patterns {:jfr-path jfr :alloc-only? false})
        _ (println "Detected hits:" (count hits))
        summary (detector/summarize hits {:top-stacks 30})]
    (println "== Quick-fix patterns in" jfr)
    (doseq [{:keys [id title count alloc-bytes top-stacks advice]} summary]
      (println "\n--" id ":" title)
      (println "   matches:" count (if (some? alloc-bytes) (str "  alloc-bytes≈" alloc-bytes) ""))
      (println "   advice: " advice)
      (println " " top-stacks))))

(test-detect)