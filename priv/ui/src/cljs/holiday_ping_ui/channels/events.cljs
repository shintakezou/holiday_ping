(ns holiday-ping-ui.channels.events
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [holiday-ping-ui.common.events :as events]))

(defmethod events/load-view
  :channel-list
  [{:keys [db]} _]
  {:http-xhrio {:method          :get
                :uri             "/api/channels"
                :timeout         8000
                :headers         {:authorization (str "Bearer " (:access-token db))}
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [:channel-list-success]
                :on-failure      [:error-message "Channel loading failed."]}})

(defmethod events/load-view
  :channel-edit
  [{:keys [db]} [_ channel-name]]
  {:http-xhrio {:method          :get
                :uri             (str "/api/channels/" channel-name)
                :timeout         8000
                :headers         {:authorization (str "Bearer " (:access-token db))}
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [:channel-detail-success]
                :on-failure      [:switch-view :not-found]}})

(re-frame/reg-event-db
 :channel-list-success
 (fn [db [_ response]]
   (-> db
       (assoc :channels response)
       (assoc :loading-view? false))))

(re-frame/reg-event-db
 :channel-detail-success
 (fn [db [_ response]]
   (-> db
       (assoc :channel-to-edit response)
       (assoc :loading-view? false))))

(re-frame/reg-event-fx
 :channel-delete
 (fn [{db :db} [_ channel]]
   {:http-xhrio {:method          :delete
                 :uri             (str "/api/channels/" channel)
                 :timeout         8000
                 :headers         {:authorization (str "Bearer " (:access-token db))}
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:channel-delete-success channel]
                 :on-failure      [:error-message "Channel deleting failed."]}}))

(re-frame/reg-event-db
 :channel-delete-success
 (fn [db [_ channel]]
   (update db :channels
           (fn [channels]
             (remove #(= (:name %) channel) channels)))))

;; FIXME adapt this to new form
(re-frame/reg-event-fx
 :channel-submit
 (fn [{db :db} [_ {:keys [name type url channels username emoji days-before
                          same-day]}]]
   (let [
         days-before (js/parseInt days-before)
         params      {:name          name
                      :type          type
                      :same_day      same-day
                      :days_before   (if (zero? days-before) nil days-before)
                      :configuration {:channels channels
                                      :url      url
                                      :username username
                                      :emoji    emoji}}]
     (cond
       (some string/blank? [name url])
       {:dispatch [:error-message "Please fill required fields."]}

       :else {:http-xhrio {:method          :put
                           :uri             (str "/api/channels/" name)
                           :headers         {:authorization (str "Bearer " (:access-token db))}
                           :timeout         8000
                           :format          (ajax/json-request-format)
                           :params          params
                           :response-format (ajax/text-response-format)
                           :on-success      [:navigate :channel-list]
                           :on-failure      [:error-message "Channel submission failed"]}}))))

(re-frame/reg-event-fx
 :wizard-submit
 (fn [{db :db} [_ {:keys [type channel-config reminder-config]}]]
   (let [days-before  (js/parseInt (:days-before reminder-config))
         channel-name (:name channel-config)
         params       {:name          channel-name
                       :type          type
                       :same_day      (:same-day reminder-config)
                       :days_before   (if (zero? days-before) nil days-before)
                       :configuration (dissoc  channel-config :name)}]
     {:db         (assoc db :loading-view? true)
      :http-xhrio {:method          :put
                   :uri             (str "/api/channels/" channel-name)
                   :headers         {:authorization (str "Bearer " (:access-token db))}
                   :timeout         8000
                   :format          (ajax/json-request-format)
                   :params          params
                   :response-format (ajax/text-response-format)
                   :on-success      [:wizard-submit-success channel-name]
                   :on-failure      [:error-message "Channel submission failed"]}})))

(re-frame/reg-event-fx
 :wizard-submit-success
 (fn [_ [_ channel-name]]
   {:dispatch-n [[:holidays-save channel-name]
                 [:navigate :channel-list]]}))

(re-frame/reg-event-db
 :channel-test-start
 (fn [db [_ channel]]
   (assoc db :channel-to-test channel)))

(re-frame/reg-event-db
 :channel-test-cancel
 (fn [db _]
   (dissoc db :channel-to-test)))

(re-frame/reg-event-fx
 :channel-test-confirm
 (fn [{:keys [db]} [_ channel]]
   {:dispatch   [:channel-test-cancel]
    :http-xhrio {:method          :post
                 :uri             (str "/api/channels/" (:name channel) "/test")
                 :timeout         8000
                 :headers         {:authorization (str "Bearer " (:access-token db))}
                 :format          (ajax/json-request-format)
                 :params          {}
                 :response-format (ajax/text-response-format)
                 :on-success      [:success-message "Channel reminder sent."]
                 :on-failure      [:error-message "There was an error sending the reminder."]}}))
