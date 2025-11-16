(ns jfr.detector.report-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [hiccup2.core :as h]
            [jfr.detector.report :as report]))

(def sample-report
  {:uuid "test-uuid-1234"
   :status "completed"
   :scheduled-at 0
   :started-at 0
   :finished-at 0
   :hit-count 3832
   :summary [{:id 4
              :title "new SimpleDateFormat repeatedly"
              :advice "Switch to immutable java.time.DateTimeFormatter (thread-safe)."
              :count 1593
              :alloc-bytes 122072
              :top-stacks [[
                            [
                             "java.util.Arrays.copyOf(java.lang.Object[],int)"
                             "java.text.DateFormatSymbols.copyMembers(java.text.DateFormatSymbols,java.text.DateFormatSymbols)"
                             "java.text.DateFormatSymbols.initializeData(java.util.Locale)"
                             "java.text.DateFormatSymbols.<init>(java.util.Locale)"
                             "sun.util.locale.provider.DateFormatSymbolsProviderImpl.getInstance(java.util.Locale)"
                             "java.text.DateFormatSymbols.getProviderInstance(java.util.Locale)"]
                            283]]}]})

(deftest report-div-renders-top-stacks
  (let [hiccup-tree (report/report-div sample-report)
        rendered-html (str (h/html hiccup-tree))]
    (testing "top stack summary is visible"
      (is (str/includes? rendered-html "Top stacks (1)")))
    (testing "each frame is rendered as a code line"
      (is (str/includes? rendered-html "<code class=\"issue-stack-frame-code\">java.util.Arrays.copyOf")))
    (testing "hit counts are displayed next to the stack"
      (is (str/includes? rendered-html "283 hits")))))
