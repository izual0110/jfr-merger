(ns  jfr.utils)

(defn if-nil [f & ns]
  (let [v (f)]
    (cond
      (not (nil? v)) v
      (some? ns) (apply if-nil ns)
      :else v)))

  (assert (= 1 (if-nil (fn [] nil)
          #(+ 1)
          (fn [] 2))))

