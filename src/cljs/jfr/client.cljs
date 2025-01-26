(ns jfr.client
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [react-dom :as re]))


(defn app []
      [:div {:class "container"} 
       [:section {:class "section"}
       [:div {:class "box"} 
        [:p {:class "title"} "paste the link"]
        [:div {:class "field has-addons"}
         [:div {:class "control is-expanded"}
          [:input {:type "text" :class "input"}]]
         [:div {:class "control"}
          [:button {:class "button"} "Submit"]]]]]])

(defn ^:export init []
  (js/console.log "Hello World")
  (rdom/render [app] (js/document.getElementById "root")))