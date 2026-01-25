(ns jfr.core-test
  (:require [clojure.test :refer :all]
            [jfr.core :as core]
            [jfr.detector.worker :as detector-worker]
            [jfr.storage :as storage]
            [org.httpkit.server :as http-kit]))

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
        init-called (atom false)
        start-called (atom false)
        stop-worker-called (atom false)
        destroy-called (atom false)
        stop-args (atom nil)
        run-args (atom nil)]
    (with-redefs [http-kit/run-server (fn [handler opts]
                                        (reset! run-args {:handler handler
                                                          :opts opts})
                                        (fn [& args]
                                          (reset! stop-args args)))
                  storage/init (fn [] (reset! init-called true))
                  storage/destroy (fn [] (reset! destroy-called true))
                  detector-worker/start! (fn [] (reset! start-called true))
                  detector-worker/stop! (fn [] (reset! stop-worker-called true))]
      (try
        (is (nil? (try
                    (core/-main)
                    nil
                    (catch Exception e e))))
        (is @init-called)
        (is @start-called)
        (is (fn? @core/server))
        (let [handler (:handler @run-args)
              response (handler {:request-method :get
                                 :uri "/index.html"})]
          (is (= 200 (:status response))))
        (core/stop-server)
        (is @stop-worker-called)
        (is @destroy-called)
        (is (= [:timeout 100] @stop-args))
        (is (nil? @core/server))
        (finally
          (reset! core/server original-server))))))
