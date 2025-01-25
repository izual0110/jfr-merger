(ns jfr.client
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [react-dom :as re]))

;; (js/alert (str "test " (+ 1 2 3 4)))

;; (js/console.log "Hello World  124")

;; (re/render)

;; (defn ^:export init []
  ;; (js/console.log "Hello World"))

(defn app []
      [:div "Hello World"])

;; (js/console.log (js/document.getElementById "root"))

(defn ^:export init []
  (js/console.log "Hello World")
  (rdom/render [app] (js/document.getElementById "root")))