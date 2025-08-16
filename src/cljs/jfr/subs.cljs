(ns jfr.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :url
 (fn [db _]
   (:url db)))

(rf/reg-sub
 :response
 (fn [db _]
   (:response db)))

(rf/reg-sub
 :files
 (fn [db _]
   (:files db)))
