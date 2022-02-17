(ns guestbook.core
  (:require [reagent.core :as r]
            [reagent.dom :as dom]
            [re-frame.core :as rf]
            [ajax.core :refer [GET POST]]
            [clojure.string :as string]
            [guestbook.validation :refer [validate-message]]
            [guestbook.websockets :as ws]
            [mount.core :as mount]))

(defn handle-ws-response! [response]
  (if-let [errors (:errors response)]
    (rf/dispatch [:form/set-server-errors errors])
    (do
      (rf/dispatch [:messages/add response])
      (rf/dispatch [:form/clear-fields response]))))

(rf/reg-event-fx
  :messages/load
  (fn [{:keys [db]} _]
    (GET "/api/messages"
         {:headers {"Accept" "application/transit+json"}
          :handler (fn [r] (rf/dispatch [:messages/set (:messages r)]))})
    {:db (assoc db :messages/loading? true)}))

(rf/reg-event-db
  :form/set-server-errors
  [(rf/path :form/server-errors)]
  (fn [_ [_ errors]]
    errors))

(rf/reg-sub
  :form/server-errors
  (fn [db _]
    (:form/server-errors db)))

(rf/reg-sub
  :form/validation-errors
  :<- [:form/fields]
  (fn [fields _]
    (validate-message fields)))

(rf/reg-sub
  :form/validation-errors?
  :<- [:form/validation-errors]
  (fn [errors _]
    (not (empty? errors))))

(rf/reg-sub
  :form/errors
  :<- [:form/validation-errors]
  :<- [:form/server-errors]
  (fn [[validation server] _]
    (merge validation server)))

(rf/reg-sub
  :form/error
  :<- [:form/errors]
  (fn [errors [_ id]]
    (get errors id)))

(rf/reg-event-db
  :form/set-field
  [(rf/path :form/fields)]
  (fn [fields [_ id value]]
    (assoc fields id value)))

(rf/reg-event-db
  :form/clear-fields
  [(rf/path :form/fields)]
  (fn [_ _] {}))

(rf/reg-sub
  :form/fields
  (fn [db _]
    (:form/fields db)))

(rf/reg-sub
  :form/field
  :<- [:form/fields]
  (fn [fields [_ id]]
    (get fields id)))

(rf/reg-event-fx
  :app/initialize
  (fn [_ _]
    {:db       {:messages/loading? true}
     :dispatch [:messages/load]}))

(rf/reg-event-db
  :messages/set
  (fn [db [_ messages]]
    (-> db
        (assoc :messages/loading? false
               :messages/list messages))))

(rf/reg-sub
  :messages/list
  (fn [db _]
    (:messages/list db [])))

(rf/reg-sub
  :messages/loading?
  (fn [db _]
    (:messages/loading? db)))

(rf/reg-event-db
  :messages/add
  (fn [db [_ message]]
    (update db :messages/list (fn [messages] (conj messages message)))))

(rf/reg-event-fx
  :message/send!
  (fn [{:keys [db]} [_ fields]]
    (ws/send! [:message/create! fields]
              10000
              (fn [{:keys [success errors] :as response}]
                (.log js/console "Called back: " (pr-str response))
                (if success
                  (rf/dispatch [:form/clear-fields])
                  (rf/dispatch [:form/set-server-errors errors]))))
    {:db (dissoc db :form/server-errors)}))

(defn reload-messages-button []
  (let [loading? (rf/subscribe [:messages/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(rf/dispatch [:messages/load])
      :disabled @loading?}
     (if @loading?
       "Loading messages"
       "Refresh messages")]))

(defn message-list [messages]
  (println messages)
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p "@" name]])])

(defn errors-component [id]
  (when-let [error @(rf/subscribe [:form/error id])]
    [:div.notification.is-danger (string/join error)]))

(defn message-form []
  [:div
   [errors-component :server-error]
   [:div.field
    [:label.label {:for :name} "Name"]
    [errors-component :name]
    [:input.input {:type      :text
                   :name      :name
                   :value     @(rf/subscribe [:form/field :name])
                   :on-change (fn [eventObj]
                                (rf/dispatch [:form/set-field :name (.-value (.-target eventObj))]))}]]
   [:div.field
    [:label.label {:for :message} "Message"]
    [errors-component :message]
    [:textarea.textarea {:name      :message
                         :value     @(rf/subscribe [:form/field :message])
                         :on-change (fn [eventObj]
                                      (rf/dispatch [:form/set-field :message (.-value (.-target eventObj))]))}]]
   [:input.button.is-primary {:type     :submit
                              :disabled @(rf/subscribe [:form/validation-errors?])
                              :value    "Comment"
                              :on-click (fn [_] (rf/dispatch [:message/send! @(rf/subscribe [:form/fields])]))}]])

(defn home []
  (let [messages (rf/subscribe [:messages/list])]
    (fn []
      (if @(rf/subscribe [:messages/loading?])
        [:div>div.row>div.span12>h3 "Loading Messages..."]
        [:div.content>div.columns.is-centered>div.column.is-two-thirds
         [:div.columns>div.column
          [:h3 "Messages"]
          [message-list messages]]
         [:div.columns>div.column
          [reload-messages-button]]
         [:div.columns>div.column
          [message-form]]]))))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting components...")
  (dom/render [#'home] (.getElementById js/document "content"))
  (.log js/console "Components mounted!"))

(defn init! []
  (.log js/console "Initializing app...")
  (mount/start)
  (rf/dispatch [:app/initialize])
  ;(ws/connect! (str "ws://" (.-host js/location) "/ws") handle-ws-response!)
  (mount-components))

(dom/render [home] (.getElementById js/document "content"))
