(ns jfr.detector.detector
  (:require [clojure.string :as str])
  (:import
   [jdk.jfr.consumer RecordingFile RecordedEvent RecordedFrame RecordedStackTrace]
   [java.nio.file Paths]))

;; ----------------------------
;; 0) Helper utilities
;; ----------------------------

(defn- jfr-events ^java.util.List [^String jfr-path]
  (RecordingFile/readAllEvents (Paths/get jfr-path (make-array String 0))))

(defn- jvm-descriptor->pretty [^String desc]
  ;; A very small JVM descriptor parser for readability
  ;; (D)V  → "(double)"
  ;; (Ljava/lang/String;)V → "(java.lang.String)"
  ;; (I)Ljava/lang/String; → "(int):java.lang.String"
  (let [type-map {"I" "int"
                  "J" "long"
                  "D" "double"
                  "F" "float"
                  "Z" "boolean"
                  "B" "byte"
                  "C" "char"
                  "S" "short"
                  "V" "void"}]
    (letfn [(parse-type [^String s]
              (cond
                (empty? s) ["" ""]
                (= (subs s 0 1) "[") ;; array
                (let [[t rest] (parse-type (subs s 1))]
                  [(str t "[]") rest])
                (= (subs s 0 1) "L") ;; object
                (let [semi (.indexOf s ";")
                      fqcn (subs s 1 semi)]
                  [(.replace fqcn "/" ".") (subs s (inc semi))])
                :else [(get type-map (subs s 0 1) (subs s 0 1))
                       (subs s 1)]))]
      (let [args-start (.indexOf desc "(")
            args-end (.indexOf desc ")")
            args-str (subs desc (inc args-start) args-end)
            ret-str (subs desc (inc args-end))
            ;; parse arguments
            parse-args (fn parse-args [s acc]
                         (if (empty? s)
                           acc
                           (let [[t rest] (parse-type s)]
                             (recur rest (conj acc t)))))]
        (str "(" (clojure.string/join "," (parse-args args-str [])) ")")))))
                                                          

(defn- frame->fq-method ^String [^RecordedFrame f]
  (let [m (.getMethod f)
        type-name (.getName (.getType m))
        method-name (.getName m)
        descriptor (.getDescriptor m)] ;; descriptor in JVM format "(D)V"
    (str type-name "." method-name (jvm-descriptor->pretty descriptor))))

(defn- event->frames ^java.util.List [^RecordedEvent e]
  (let [^RecordedStackTrace st (.getStackTrace e)]
    (if (nil? st) (java.util.Collections/emptyList) (.getFrames st))))

(defn- event-name ^String [^RecordedEvent e]
  (.getName (.getEventType e)))                       ; e.g. "jdk.ObjectAllocationInNewTLAB"

(defn- alloc-size ^Long [^RecordedEvent e]
  (when (.hasField e "allocationSize") (.getLong e "allocationSize")))

(defn- object-class ^String [^RecordedEvent e]
  (when (.hasField e "objectClass") (str (.getValue e "objectClass"))))

(defn- stack->strings [^RecordedEvent e]
  (map frame->fq-method (event->frames e)))

;; ----------------------------
;; 1) Quick-fix patterns
;;    — extend as needed
;; ----------------------------

(def default-patterns
  [;; enum.values() -> Enum.clone(): enum array allocation on each call
    {:id 1
     :title "Enum.values clone array"
     :where :stack
     :match [#"\.values\(\)$"]
    :advice "Move MyEnum.values() out of tight loops into a local cache, or use static final T[] CACHE."}

   ;; String.split(regex) inside a loop: Pattern compilation + array allocation
    {:id 2
     :title "String.split(regex) in hot path"
     :where :stack
     :match [#"^java\.lang\.String\.split\(java\.lang\.String\)$"]
    :advice "Switch to indexOf/substrings or cache the Pattern in a static final field."}

   ;; String.format: heavyweight Formatter + plenty of temporaries
    {:id 3
     :title "String.format in hot path"
     :where :stack
     :match [#"^java\.lang\.String\.format\(java\.lang\.String,java\.lang\.Object\[\]\)$" #"^java\.util\.Formatter\."]
    :advice "Replace with concatenation or StringBuilder."}

   ;; SimpleDateFormat created/used frequently
    {:id 4
     :title "new SimpleDateFormat repeatedly"
     :where :stack
     :match [#"^java\.text\.SimpleDateFormat\.<init>\(java\.lang\.String\)$"]
    :advice "Switch to immutable java.time.DateTimeFormatter (thread-safe)."}

   ;; Autoboxing in collections/streams
    {:id 5
     :title "Autoboxing on hot path"
     :where :stack
     :match [#"^java\.lang\.(Integer|Long|Double)\.valueOf\(int\)$"]
    :advice "Prefer primitives/IntStream/fastutil/trove and avoid boxing inside loops."}

   ;; Random created in each method
    {:id 6
     :title "new Random() frequently"
     :where :stack
     :match [#"^java\.util\.Random\.<init>\(.*\)$"]
    :advice "Use ThreadLocalRandom.current() / SplittableRandom instead."}

   ;; Optional.of(...) in a tight loop
    {:id 7
     :title "Optional allocations in hot path"
     :where :stack
     :match [#"^java\.util\.Optional\.of\(java\.lang\.Object\)?$"]
    :advice "Remove unnecessary Optional allocations in loops; a simple condition is faster."}

   ;; Stream/forEach on short collections
    {:id 8
     :title "Stream pipeline in tight loop"
     :where :stack
     :match [#"^java\.util\.stream\."]
    :advice "A plain for-loop is faster and avoids extra allocations."}

   ;; map.keySet().contains(x)
    {:id 9
     :title "keySet().contains instead of containsKey"
     :where :stack
     :match [#"\.keySet$" #"\.contains\(java\.lang\.Object\)$"]
    :advice "Call Map.containsKey(x) instead."}

   ;; System.currentTimeMillis inside a loop
    {:id 10
     :title "System.currentTimeMillis in loop"
     :where :stack
     :match [#"^java\.lang\.System\.currentTimeMillis\(\)$"]
    :advice "Store the value in a variable outside the loop."}

   ;; BigDecimal(double)
    {:id 11
     :title "new BigDecimal(double)"
     :where :stack
     :match [#"^java\.math\.BigDecimal\.<init>\(double\)$"]
    :advice "BigDecimal.valueOf(double) is more precise and cheaper."}

   ;; toCharArray()
    {:id 12
     :title "String.toCharArray in hot path"
     :where :stack
     :match [#"^java\.lang\.String\.toCharArray\(\)$"]
    :advice "Use charAt/regionMatches instead of allocating an array."}
   ])

;(test-detect)

;; ----------------------------
;; 2) Matching by stack/object type
;; ----------------------------

(defn- match-stack? [frames re-list]
  (let [pred (fn [^String f] (some #(re-find % f) re-list))]
    (boolean (some pred frames))))

(defn- match-object? [^String obj-class re-list]
  (when obj-class
    (boolean (some #(re-find % obj-class) re-list))))

(defn detect-patterns
  [{:keys [jfr-path patterns alloc-only?] :or {patterns default-patterns alloc-only? false}}]
  (for [^RecordedEvent e (jfr-events jfr-path)
        :when (or (not alloc-only?) 
                  (= (event-name e) "jdk.ObjectAllocationInNewTLAB"))
        :let [frames (vec (stack->strings e))
              obj    (object-class e)
              sz     (alloc-size e)]
        p patterns
        :let [w (:where p)
              res (case w
                    :stack (match-stack? frames (:match p))
                    :object (match-object? obj (:match p))
                    ;; can be combined when :where = [:stack :object]
                    (match-stack? frames (:match p)))]
        :when res]
    {:pattern (:id p) :title (:title p) :advice (:advice p)
     :event e :frames frames :obj obj :size sz :ename (event-name e) :top (first frames)}))

;; ----------------------------
;; 3) Result aggregation
;; ----------------------------

(defn summarize
  "Aggregate hits: total count, allocation size (if present), and top stacks."
  [hits {:keys [top-stacks] :or {top-stacks 5}}]
  (let [grouped (group-by :pattern hits)]
    (->> grouped
         (map (fn [[pid xs]]
                (let [title    (:title (first xs))
                      advice   (:advice (first xs))
                      cnt      (count xs)
                      allocsum (reduce (fn [a {:keys [size]}] (if size (+ a size) a)) 0 xs)
                      stacks   (->> xs
                                    (map (fn [{:keys [frames]}]
                                           (->> frames (take 6) (str/join " ← "))))
                                    (frequencies)
                                    (sort-by val >)
                                    (map (fn [[k v]] (str "[" v "] " k)))
                                    (take top-stacks))]
                  {:id pid
                   :title title
                   :advice advice
                   :count cnt
                   :alloc-bytes (if (pos? allocsum) allocsum "unknown")
                   :top-stacks stacks})))
         (sort-by :count >))))

(defn test-detect []
  (let [
        ;; jfr "test/BadPatternsDemo.jfr"
        jfr "test/SmallBadPatternsDemo.jfr"
        hits (detect-patterns {:jfr-path jfr :alloc-only? false})
        _ (println "Detected hits:" (count hits))
        summary (summarize hits {:top-stacks 3})]
    (println "== Quick-fix patterns in" jfr)
    (doseq [{:keys [pattern title count alloc-bytes top-stacks advice]} summary]
      (println "\n--" (name pattern) ":" title)
      (println "   matches:" count (if (some? alloc-bytes) (str "  alloc-bytes≈" alloc-bytes) ""))
      (println "   advice: " advice)
      (println " " top-stacks))))

;; (test-detect)


