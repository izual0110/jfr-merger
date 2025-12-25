(ns repl
  (:require
   [jfr.core :as core]))


(core/stop-server)
(core/-main)