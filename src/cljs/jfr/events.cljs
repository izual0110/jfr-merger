(ns jfr.events
  (:require [re-frame.core :as rf]
            [cljs-http.client :as http]))

(rf/reg-event-db
 :init
 (fn [_ _]
   {:url "" :response nil}))

(rf/reg-event-db
 :set-url
 (fn [db [_ url]]
   (assoc db :url url)))

(rf/reg-event-fx
 :send-url
 (fn [{:keys [db]} _]
   {:http-xhrio {:method          :post
                 :uri             "/api/heatmap"
                 :params          {:url (:url db)}
                 :format          :json
                 :response-format :json
                 :on-success      [:url-success]
                 :on-failure      [:url-failure]}}))

(rf/reg-event-db
 :url-success
 (fn [db [_ response]]
   (assoc db :response response)))

(rf/reg-event-db
 :url-failure
 (fn [db [_ error]]
   (assoc db :response (str "Ошибка: " error))))