(ns jfr.client
;;   (:require
;;    [reagent.core :as reagent]
  ;;  [reagent.dom :as rdom]
;;    [cljsjs.react]
;;    )
  )

;; (js/alert (str "test " (+ 1 2 3 4)))

(js/console.log "Hello World  124")



(defn ^:export init []
  (js/console.log "Hello World"))




;; (defn ^:export init []
;;   (let [root (rdom/createRoot (js/document.getElementById "app"))]
;;     (.render root ($ app))))