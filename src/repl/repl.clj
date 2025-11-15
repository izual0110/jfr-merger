(ns repl
  (:require
   [jfr.core :as core]
   [jfr.detector.detector :as detector]))


(core/stop-server)
(core/-main)