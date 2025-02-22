(ns jfr.client
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [react-dom :as re]
   [jfr.views :as views]
  ))

(defn ^:export init []
  (js/console.log "Hello World")
  (rdom/render [views/app] (js/document.getElementById "root")))