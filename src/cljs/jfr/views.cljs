(ns jfr.views
  (:require [re-frame.core :as rf]
            [jfr.events]
            [jfr.subs]))

(defn file-drop []
  (let [files @(rf/subscribe [:files])]
    [:div
     {:style {:border "2px dashed #ccc"
              :padding "2em"
              :margin-bottom "1em"
              :text-align "center"}
      :on-drag-over #(do (.preventDefault %) (.stopPropagation %))
      :on-drop (fn [e]
                 (.preventDefault e)
                 (.stopPropagation e)
                 (let [dropped-files (.. e -dataTransfer -files)]
                   (rf/dispatch [:set-files dropped-files])))}
     (if files
       [:div
        [:p (str "Выбрано файлов: " (.-length files))]
        [:ul
         (for [i (range (.-length files))]
           ^{:key i} [:li (.-name (.item files i))])]]
       [:p "Перетащите сюда файлы для загрузки"])
     [:div {:style {:margin-top "1em"}}
      [:input {:type "file"
               :multiple true
               :style {:display "none"}
               :id "file-input"
               :on-change #(rf/dispatch [:set-files (.. % -target -files)])}]
      [:label {:for "file-input" :class "button is-link is-light"} "Выбрать файлы"]]
     (when files
       [:button {:class "button is-primary"
                 :style {:margin-top "1em"}
                 :on-click #(rf/dispatch [:send-files])}
        "Отправить файлы"])]))

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
                     :on-click #(rf/dispatch [:send-url])} "Submit"]]]
         [:hr]
         [file-drop]]]
      (when response
        [:p "Server response: " response])]))
