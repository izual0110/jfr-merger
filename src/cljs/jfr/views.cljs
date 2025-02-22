(ns jfr.views
  (:require [re-frame.core :as rf]
            [jfr.events]
            [jfr.subs]))

(defn app []
  (let [url @(rf/subscribe [:url])
        response @(rf/subscribe [:response])]
     [:div {:class "container"}
       [:section {:class "section"}
        [:div {:class "box"}
         [:p {:class "title"} "paste the link"]
         [:div {:class "field has-addons"}
          [:div {:class "control is-expanded"}
           [:input {:type "text"
                    :value url
                    :class "input"
                    :on-change   #(rf/dispatch [:set-url (-> % .-target .-value)])}]]
          [:div {:class "control"}
           [:button {:class "button"
                     :on-click #(rf/dispatch [:send-url])} "Submit"]]]]]
      (when response
        [:p "Server response: " response])]))