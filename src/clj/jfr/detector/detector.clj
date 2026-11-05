(ns jfr.detector.detector
  (:require [clojure.string :as str])
  (:import
   [jdk.jfr.consumer RecordingFile RecordedEvent RecordedFrame RecordedStackTrace]
   [java.nio.file Paths]
   [java.time Instant]))

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
            _ (subs desc (inc args-end))
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
                                           (->> frames (take 20) (vec))))
                                    (frequencies)
                                    (sort-by val >)
                                    (take top-stacks))]
                  {:id pid
                   :title title
                   :advice advice
                   :count cnt
                   :alloc-bytes (if (pos? allocsum) allocsum "unknown")
                   :top-stacks stacks})))
         (sort-by :count >))))

;; ----------------------------
;; 4) Temporal problem detector
;; ----------------------------

(defn- event-timestamp-ms [^RecordedEvent e]
  (.toEpochMilli (.getStartTime e)))

(defn- event-duration-ms [^RecordedEvent e]
  (let [duration (.getDuration e)]
    (when (and duration (pos? (.toNanos duration)))
      (max 1 (.toMillis duration)))))

(defn- event-weight [^RecordedEvent e]
  (or (event-duration-ms e)
      (alloc-size e)
      1))

(defn- instant-string [timestamp-ms]
  (str (Instant/ofEpochMilli (long timestamp-ms))))

(defn- clamp [min-value max-value value]
  (-> value (max min-value) (min max-value)))

(defn- choose-bucket-ms [samples]
  (let [timestamps (map :timestamp-ms samples)
        start (apply min timestamps)
        finish (apply max timestamps)
        duration (max 1 (- finish start))]
    (long (clamp 250 5000 (Math/ceil (/ duration 60.0))))))

(defn- median [values]
  (let [sorted-values (vec (sort values))
        n (count sorted-values)]
    (cond
      (zero? n) 0
      (odd? n) (nth sorted-values (quot n 2))
      :else (/ (+ (nth sorted-values (dec (quot n 2)))
                  (nth sorted-values (quot n 2)))
               2.0))))

(defn- stack-key [frames]
  (vec (take 12 frames)))

(defn- segment-buckets [bucket-indexes]
  (let [sorted-indexes (sort bucket-indexes)]
    (reduce (fn [segments idx]
              (if-let [[start end] (peek segments)]
                (if (<= (- idx end) 1)
                  (conj (pop segments) [start idx])
                  (conj segments [idx idx]))
                [[idx idx]]))
            []
            sorted-indexes)))

(defn- segment-range [recording-start-ms bucket-ms [start-bucket end-bucket]]
  (let [start-ms (+ recording-start-ms (* start-bucket bucket-ms))
        end-ms (+ recording-start-ms (* (inc end-bucket) bucket-ms))]
    {:start-ms start-ms
     :end-ms end-ms
     :start (instant-string start-ms)
     :end (instant-string end-ms)}))

(defn- coefficient-of-variation [values]
  (let [n (count values)
        avg (when (pos? n) (/ (reduce + values) n))]
    (if (and avg (pos? avg))
      (let [variance (/ (reduce + (map #(let [delta (- % avg)] (* delta delta)) values)) n)]
        (/ (Math/sqrt variance) avg))
      0)))

(defn- add-problem [problems problem-type confidence reason ranges stack score event-types]
  (conj problems {:type problem-type
                  :confidence confidence
                  :reason reason
                  :ranges ranges
                  :stack stack
                  :score score
                  :event-types event-types}))

(defn- stack-temporal-problems
  [{:keys [recording-start-ms bucket-ms total-buckets min-score]} [stack samples]]
  (let [bucketed (->> samples
                      (group-by #(quot (- (:timestamp-ms %) recording-start-ms) bucket-ms))
                      (map (fn [[idx xs]] [idx (reduce + (map :weight xs))]))
                      (into {}))
        bucket-values (vals bucketed)
        active-buckets (keys bucketed)
        total-score (reduce + bucket-values)
        event-types (->> samples (map :event-type) frequencies (sort-by val >) (take 5) (mapv first))
        baseline (max 1 (median bucket-values))
        max-score (if (seq bucket-values) (apply max bucket-values) 0)
        peak-threshold (max min-score (* 3 baseline))
        active-segments (segment-buckets active-buckets)
        coverage (/ (count active-buckets) (max 1 total-buckets))
        gaps (->> active-segments (map first) (partition 2 1) (map (fn [[a b]] (- b a))) vec)]
    (cond-> []
      (>= max-score peak-threshold)
      (add-problem :peak
                   (min 1.0 (/ max-score (max 1.0 (* 5.0 baseline))))
                   "A stack has a short bucket whose event weight is much higher than its normal bucket weight."
                   (->> bucketed
                        (filter (fn [[_ score]] (>= score peak-threshold)))
                        (map first)
                        segment-buckets
                        (mapv #(segment-range recording-start-ms bucket-ms %)))
                   stack
                   max-score
                   event-types)

      (and (>= total-score min-score)
           (>= coverage 0.7)
           (>= (count active-buckets) 3))
      (add-problem :constant
                   (min 1.0 coverage)
                   "The same stack is active in most time buckets, which can indicate a sustained hot path."
                   [(segment-range recording-start-ms bucket-ms [(apply min active-buckets) (apply max active-buckets)])]
                   stack
                   total-score
                   event-types)

      (and (>= total-score min-score)
           (>= (count active-segments) 3)
           (< coverage 0.7)
           (seq gaps)
           (<= (coefficient-of-variation gaps) 0.35))
      (add-problem :periodic
                   (max 0.1 (- 1.0 (coefficient-of-variation gaps)))
                   "The same stack appears in repeated bursts with a stable interval between bursts."
                   (mapv #(segment-range recording-start-ms bucket-ms %) active-segments)
                   stack
                   total-score
                   event-types))))

(defn analyze-problem-samples
  "Detect temporal problem shapes in already extracted event samples.

  Each sample must contain :timestamp-ms and should contain :frames. :weight and
  :event-type are optional. The detector classifies candidate stacks as :peak,
  :periodic, or :constant and returns time ranges plus representative stacks."
  [samples {:keys [bucket-ms top-problems min-score]
            :or {top-problems 20 min-score 5}}]
  (let [normalized (->> samples
                        (keep (fn [{:keys [timestamp-ms frames weight event-type]}]
                                (when (and timestamp-ms (seq frames))
                                  {:timestamp-ms timestamp-ms
                                   :frames (stack-key frames)
                                   :weight (or weight 1)
                                   :event-type (or event-type "unknown")}))))]
    (if (seq normalized)
      (let [recording-start-ms (apply min (map :timestamp-ms normalized))
            recording-end-ms (apply max (map :timestamp-ms normalized))
            bucket-ms (or bucket-ms (choose-bucket-ms normalized))
            total-buckets (inc (quot (- recording-end-ms recording-start-ms) bucket-ms))]
        (->> normalized
             (group-by :frames)
             (mapcat #(stack-temporal-problems {:recording-start-ms recording-start-ms
                                                :bucket-ms bucket-ms
                                                :total-buckets total-buckets
                                                :min-score min-score}
                                               %))
             (sort-by (juxt (comp - :confidence) (comp - :score)))
             (take top-problems)
             vec))
      [])))

(defn event-samples
  "Extract timestamped stack samples from a JFR file for temporal problem analysis."
  [jfr-path]
  (->> (jfr-events jfr-path)
       (keep (fn [^RecordedEvent e]
               (let [frames (vec (stack->strings e))]
                 (when (seq frames)
                   {:timestamp-ms (event-timestamp-ms e)
                    :event-type (event-name e)
                    :weight (event-weight e)
                    :frames frames}))))
       vec))

(defn detect-problem-ranges
  "Detect peak, periodic, and constant problem candidates in a JFR file.

  Returns maps with :type, :ranges, :stack, :score, :confidence, and :event-types."
  ([jfr-path]
   (detect-problem-ranges jfr-path {}))
  ([jfr-path options]
   (analyze-problem-samples (event-samples jfr-path) options)))
