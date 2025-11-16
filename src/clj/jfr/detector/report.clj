(ns jfr.detector.report
  (:require
   [clojure.string :as str]))

(defn- format-bytes [b]
  (cond
    (nil? b) "—"
    (number? b)
    (let [kb (/ b 1024.0)
          mb (/ b (* 1024.0 1024.0))]
      (cond
        (>= mb 1) (format "%.1f MB" mb)
        (>= kb 1) (format "%.1f KB" kb)
        :else (str b " B")))
    :else
    (str b)))

(defn- format-duration-ms [started finished]
  (when (and started finished)
    (str (- finished started) " ms")))

(defn- issue-stack-item [[frames hits]]
  [:li.issue-stack-item
   (when hits
     [:div.issue-stack-hits (str hits " hits")])
   [:ol.issue-stack-frames
    (for [frame (or frames [])]
      [:li.issue-stack-frame [:code.issue-stack-frame-code frame]])]])

(defn- issue-block [{:keys [id title advice count alloc-bytes top-stacks]}]
  [:div.issue
   [:div.issue-header
    [:div.issue-title-row
     [:span.issue-title title]
     [:span.issue-id (str "#" id)]]
    [:div.issue-meta
     [:span.issue-meta-item (str "hits: " count)]
     [:span.issue-meta-item (str "alloc: " (format-bytes alloc-bytes))]]]
   [:div.issue-advice
    [:span.issue-advice-label "Advice: "]
    [:span.issue-advice-text advice]]
   (when (seq top-stacks)
     [:details.issue-stacks
      [:summary "Top stacks (" (clojure.core/count top-stacks) ")"]
      [:ul.issue-stack-list
       (for [stack top-stacks]
         (issue-stack-item stack))]])])

(defn report-div
  "It takes a report map (from JSON) and returns a Hiccup tree with a single root <div>"
  [{:keys [uuid status scheduled-at started-at finished-at hit-count summary] :as report}]
  [:div.profiler-report
   [:div.report-header
    [:div.report-title-row
     [:span.report-title "Profiler report"]
     (when uuid
       [:span.report-uuid (str "UUID: " uuid)])]
    [:div.report-meta
     [:span.report-meta-item (str "Status: " (or status "unknown"))]
     (when hit-count
       [:span.report-meta-item (str "Hits: " hit-count)])
     (when (and started-at finished-at)
       [:span.report-meta-item
        (str "Duration: " (format-duration-ms started-at finished-at))])
     [:span.report-meta-item (str "Findings: " (count summary) " pattern" (when (not= 1 (count summary)) "s"))]]]
   [:div.report-body
    [:div.report-issues
     (for [issue summary]
       (issue-block issue))]]])
