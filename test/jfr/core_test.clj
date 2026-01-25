(ns jfr.core-test
  (:require [clojure.test :refer :all]
            [jfr.core :as core]
            [jfr.detector.worker]
            [jfr.storage]
            [org.httpkit.server]))

(deftest index-html-served
  (let [response (core/app {:request-method :get
                            :uri "/index.html"})
        body (:body response)
        text (if (string? body)
               body
               (slurp body))]
    (is (= 200 (:status response)))
    (is (seq text))))

(deftest start-stop-server
  (let [original-server @core/server
        original-run-server (var-get #'org.httpkit.server/run-server)
        original-init (var-get #'jfr.storage/init)
        original-destroy (var-get #'jfr.storage/destroy)
        original-start-worker (var-get #'jfr.detector.worker/start!)
        original-stop-worker (var-get #'jfr.detector.worker/stop!)
        init-called (atom false)
        start-called (atom false)
        stop-worker-called (atom false)
        destroy-called (atom false)
        stop-args (atom nil)
        run-args (atom nil)]
    (alter-var-root #'org.httpkit.server/run-server
                    (constantly (fn [handler opts]
                                  (reset! run-args {:handler handler
                                                    :opts opts})
                                  (fn [& args]
                                    (reset! stop-args args)))))
    (alter-var-root #'jfr.storage/init (constantly (fn [] (reset! init-called true))))
    (alter-var-root #'jfr.storage/destroy (constantly (fn [] (reset! destroy-called true))))
    (alter-var-root #'jfr.detector.worker/start! (constantly (fn [] (reset! start-called true))))
    (alter-var-root #'jfr.detector.worker/stop! (constantly (fn [] (reset! stop-worker-called true))))
    (try
      (core/-main)
      (is @init-called)
      (is @start-called)
      (is (fn? @core/server))
      (is (= {:port 8080 :max-body (* 1024 1024 1024)}
             (:opts @run-args)))
      (let [handler (:handler @run-args)
            response (handler {:request-method :get
                               :uri "/index.html"})]
        (is (= 200 (:status response))))
      (core/stop-server)
      (is @stop-worker-called)
      (is @destroy-called)
      (is (= [:timeout 100] @stop-args))
      (is (nil? @core/server))
      (catch Exception e
        (is false (str "Unexpected exception: " e)))
      (finally
        (alter-var-root #'org.httpkit.server/run-server (constantly original-run-server))
        (alter-var-root #'jfr.storage/init (constantly original-init))
        (alter-var-root #'jfr.storage/destroy (constantly original-destroy))
        (alter-var-root #'jfr.detector.worker/start! (constantly original-start-worker))
        (alter-var-root #'jfr.detector.worker/stop! (constantly original-stop-worker))
        (reset! core/server original-server)))))
