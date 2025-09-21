(ns jfr.detector
  (:require [clojure.string :as str])
  (:import
   ;; JDK Flight Recorder API (доступно с JDK 11+)
   [jdk.jfr.consumer RecordingFile RecordedEvent RecordedFrame RecordedStackTrace]
   [java.nio.file Paths]))

;; ----------------------------
;; 0) Вспомогательные штуки
;; ----------------------------

(defn- jfr-events ^java.util.List [^String jfr-path]
  (RecordingFile/readAllEvents (Paths/get jfr-path (make-array String 0))))

(defn- frame->fq-method ^String [^RecordedFrame f]
  (let [m (.getMethod f)
        _ (if (.contains (.getName m) "values") (println 
                                                 "new:" (.toString (.getDescriptor m))
                                                 "\n\nresult:" (str (.getName (.getType m)) "." (.getName m))) nil)]
    (str (.getName (.getType m)) "." (.getName m))))

;(test-detect)

(defn- jvm-descriptor->pretty [^String desc]
  ;; очень упрощённый парсер JVM дескрипторов для читаемости
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
                (= (subs s 0 1) "[") ;; массив
                (let [[t rest] (parse-type (subs s 1))]
                  [(str t "[]") rest])
                (= (subs s 0 1) "L") ;; объект
                (let [semi (.indexOf s ";")
                      fqcn (subs s 1 semi)]
                  [(.replace fqcn "/" ".") (subs s (inc semi))])
                :else [(get type-map (subs s 0 1) (subs s 0 1))
                       (subs s 1)]))]
      (let [args-start (.indexOf desc "(")
            args-end (.indexOf desc ")")
            args-str (subs desc (inc args-start) args-end)
            ret-str (subs desc (inc args-end))
            ;; распарсим аргументы
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
        descriptor (.getDescriptor m)] ;; дескриптор в jvm-формате "(D)V"
    ;; Преобразуем "(D)V" в что-то более читаемое
    ;; (if (.contains method-name "values") (println (str type-name "." method-name (jvm-descriptor->pretty descriptor))))
    (str type-name "." method-name (jvm-descriptor->pretty descriptor))))                                                          

;(test-detect)

(defn- event->frames ^java.util.List [^RecordedEvent e]
  (let [^RecordedStackTrace st (.getStackTrace e)]
    (if (nil? st) (java.util.Collections/emptyList) (.getFrames st))))

(defn- event-name ^String [^RecordedEvent e]
  (.getName (.getEventType e)))                       ; e.g. "jdk.ObjectAllocationInNewTLAB"

(defn- alloc-size ^Long [^RecordedEvent e]
  (when (.hasField e "allocationSize") (.getLong e "allocationSize")))

(defn- object-class ^String [^RecordedEvent e]
  (when (.hasField e "objectClass") (str (.getValue e "objectClass"))))

;; (defn- is-alloc? [^RecordedEvent e]
;;   (str/starts-with? (event-name e) "jdk.ObjectAllocation"))

;; (defn- top-frame ^String [^RecordedEvent e]
;;   (let [frames (event->frames e)]
;;     (when (seq frames)
;;       (frame->fq-method (first frames)))))

(defn- stack->strings [^RecordedEvent e]
  (map frame->fq-method (event->frames e)))

;; ----------------------------
;; 1) Паттерны «быстрых фиксов»
;;    — можно дополнять под себя
;; ----------------------------

(def default-patterns
  [;; enum.values() -> Enum.clone(): аллокация массива enum при каждом вызове
   {:id :enum-values
    :title "1) Enum.values clone array"
    :where :stack
    :match [#"\.values\(\)$"]
    :advice "Вынеси MyEnum.values() из tight-loop в локальную/кэш, либо static final T[] CACHE."}

   ;; String.split(regex) в цикле: компиляция Pattern + массив
   {:id :string-split
    :title "2) String.split(regex) in hot path"
    :where :stack
    :match [#"^java\.lang\.String\.split\(java\.lang\.String\)$"]
    :advice "Переход на indexOf/substrings или кэшируй Pattern в static final."}

   ;; String.format: тяжёлый Formatter + куча временных
   {:id :string-format
    :title "3) String.format in hot path"
    :where :stack
    :match [#"^java\.lang\.String\.format\(java\.lang\.String,java\.lang\.Object\[\]\)$" #"^java\.util\.Formatter\."]
    :advice "Заменить на конкатенацию или StringBuilder."}

   ;; SimpleDateFormat создаётся/используется часто
   {:id :sdf-new
    :title "4) new SimpleDateFormat repeatedly"
    :where :stack
    :match [#"^java\.text\.SimpleDateFormat\.<init>\(java\.lang\.String\)$"]
    :advice "Перейти на иммутабельный java.time.DateTimeFormatter (thread-safe)."}

   ;; Автобоксинг в коллекциях/стримах
   {:id :boxing
    :title "5) Autoboxing on hot path"
    :where :stack
    :match [#"^java\.lang\.(Integer|Long|Double)\.valueOf$"]
    :advice "Использовать примитивы/IntStream/fastutil/trove, избегать бокса в цикле."}

   ;; Random в каждом методе
   {:id :random-new
    :title "6) new Random() frequently"
    :where :stack
    :match [#"^java\.util\.Random\.<init>$"]
    :advice "Использовать ThreadLocalRandom.current() / SplittableRandom."}

   ;; Optional.of(...) в tight loop
   {:id :optional
    :title "7) Optional allocations in hot path"
    :where :stack
    :match [#"^java\.util\.Optional\.of(Nullable)?$"]
    :advice "Избавиться от лишних Optional в цикле; простое условие быстрее."}

   ;; Stream/forEach на коротких коллекциях
   {:id :streams
    :title "8) Stream pipeline in tight loop"
    :where :stack
    :match [#"^java\.util\.stream\."]
    :advice "Обычный for быстрее и без лишних аллокаций."}

   ;; map.keySet().contains(x)
   {:id :keyset-contains
    :title "9) keySet().contains instead of containsKey"
    :where :stack
    :match [#"\.keySet$" #"\.contains$"]
    :advice "Использовать Map.containsKey(x)."}

   ;; System.currentTimeMillis в цикле
   {:id :currentTime
    :title "10) System.currentTimeMillis in loop"
    :where :stack
    :match [#"^java\.lang\.System\.currentTimeMillis$"]
    :advice "Вынести в переменную вне цикла."}

   ;; BigDecimal(double)
   {:id :bigdecimal
    :title "11) new BigDecimal(double)"
    :where :stack
    :match [#"^java\.math\.BigDecimal\.<init>\(double\)$"]
    :advice "BigDecimal.valueOf(double) точнее и дешевле."}

   ;; toCharArray()
   {:id :tochar
    :title "12) String.toCharArray in hot path"
    :where :stack
    :match [#"^java\.lang\.String\.toCharArray$"]
    :advice "Использовать charAt/regionMatches вместо аллокации массива."}
   ])

;(test-detect)

(re-find #"^java\.lang\.String\.format\(java\.lang\.String,java\.lang\.Object\[\]\)$" "java.lang.String.format(java.lang.String,java.lang.Object[])")

;; ----------------------------
;; 2) Поиск совпадений по стеку/типу
;; ----------------------------

(defn- match-stack? [frames re-list]
;;   (println "Matching stack frames:" frames "against" re-list)
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
        :let [
            ;;   _ (println "RecordedEvent" (event-name e))
              ;;   _ (println "RecordedEvent" e)
              frames (vec (stack->strings e))
            ;;   _ (println frames)
              obj    (object-class e)
              sz     (alloc-size e)]
        p patterns
        :let [w (:where p)
              ;;   _ (println "checking pattern" (:id p) "on event" (event-name e) "frames" frames)
              res (case w
                    :stack (match-stack? frames (:match p))
                    :object (match-object? obj (:match p))
                    ;; можно комбинировать, если :where = [:stack :object]
                    (match-stack? frames (:match p)))]
        :when res]
    {:pattern (:id p) :title (:title p) :advice (:advice p)
     :event e :frames frames :obj obj :size sz :ename (event-name e) :top (first frames)}))

;; ----------------------------
;; 3) Агрегация результатов
;; ----------------------------

(defn summarize
  "Агрегирует хиты: количество, суммарный размер аллокаций (если есть), топ-стеки."
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
                                    (take top-stacks))]
                  {:pattern pid
                   :title title
                   :advice advice
                   :count cnt
                   :alloc-bytes (when (pos? allocsum) allocsum)
                   :top-stacks stacks})))
         (sort-by :count >))))

;; ----------------------------
;; 4) CLI-обвязка (java -jar / clj -M)
;; ----------------------------

;; (defn -main [& args]
;;   (let [jfr-path (or (first args)
;;                      (do (binding [*out* *err*]
;;                            (println "Usage: clj -M -m jfr.quickfix.detector <recording.jfr>"))
;;                          (System/exit 2)))
;;         hits (detect-patterns {:jfr-path jfr-path
;;                                :patterns default-patterns
;;                                :alloc-only? false})
;;         summary (summarize hits {:top-stacks 5})]
;;     (println "== Quick-fix patterns in" jfr-path)
;;     (doseq [{:keys [pattern title count alloc-bytes top-stacks advice]} summary]
;;       (println "\n--" (name pattern) ":" title)
;;       (println "   matches:" count (when alloc-bytes (str "  alloc-bytes≈" alloc-bytes)))
;;       (println "   advice: " advice)
;;       (doseq [[stk n] top-stacks]
;;         (println "   " (format "[%5d] %s" n stk))))))

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
      (println "   matches:" count (when alloc-bytes (str "  alloc-bytes≈" alloc-bytes)))
      (println "   advice: " advice)
      (doseq [[stk n] top-stacks]
        (println "   " (format "[%5d] %s" n stk))))))

(test-detect)


;; (println 123)


;; Пример использования:
;;   clj -M -m jfr.quickfix.detector /path/to/recording.jfr
;;
;; Если хочешь ограничиться только аллокациями:
;;   (detect-patterns {:jfr-path "x.jfr" :alloc-only? true})
;;
;; Как добавить свой паттерн на конкретный enum:
;;   (def my (conj default-patterns
;;                 {:id :my-enum-array
;;                  :title "Specific enum array allocations"
;;                  :where :object
;;                  :match [#"\[Lcom\.acme\.MyEnum;"]
;;                  :advice "Кэшируй MyEnum.values()"}))
;;   (detect-patterns {:jfr-path "x.jfr" :patterns my})


(def my-map {:a "A" :b "B" :c 3 :d 4})
(let [{a :a, x :x, :or {x "Not found!"}, :as all} my-map]
  (println "I got" a "from" all)
  (println "Where is x?" x))


 