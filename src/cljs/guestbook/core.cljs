(ns guestbook.core
  (:require [reagent.core :as r]
            [reagent.dom :as dom]
            [re-frame.core :as rf]
            [ajax.core :refer [GET POST]]
            [clojure.string :as string]
            [guestbook.validation :refer [validate-message]]))

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
    {:db {:messages/loading? true}}))

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

(defn get-messages []
  (GET "/api/messages"
       {:headers {"Accept" "application/transit+json"}
        :handler (fn [r] (rf/dispatch [:messages/set (:messages r)]))}))

(defn message-list [messages]
  (println messages)
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p "@" name]])])

(rf/reg-event-fx
  :message/send!
  (fn [{:keys [db]} [_ fields]]
    (POST "/api/message"
          {:format        :json
           :headers
           {"Accept"       "application/transit+json"
            "x-csrf-token" (.-value (.getElementById js/document "token"))}
           :params        fields
           :handler       (fn [_] (rf/dispatch [:messages/add (assoc fields :timestamp (js/Date.))]))
           :error-handler (fn [e] (rf/dispatch [:form/set-server-errors (get-in e [:response :errors])]))})
    {:db (dissoc db :form/server-errors)}))

(defn send-message! [fields errors]
  (if-let [validation-errors (validate-message @fields)]
    (reset! errors validation-errors)
    (POST "/api/message"
          {:format        :json
           :headers       {"Accept"       "application/transit+json"
                           "x-csrf-token" (.-value (.getElementById js/document "token"))}
           :params        @fields
           :handler       (fn [r]
                            (.log js/console (str "response:" r))
                            (rf/dispatch [:messages/add (-> @fields
                                                            (assoc :timestamp (js/Date.)))])
                            (reset! fields nil)
                            (reset! errors nil))
           :error-handler (fn [e]
                            (.error js/console (str "error:" e))
                            (reset! errors (:errors (:response e))))})))

(defn errors-component [errors id]
  (when-let [error (id @errors)]
    [:div.notification.is-danger (string/join error)]))

(defn message-form []
  (let [fields (r/atom {})
        errors (r/atom {})]
    (fn []
      [:div
       [errors-component errors :server-error]
       [:div.field
        [:label.label {:for :name} "Name"]
        [errors-component errors :name]
        [:input.input {:type      :text
                       :name      :name
                       :value     (:name @fields)
                       :on-change (fn [eventObj]
                                    (swap! fields assoc :name (.-value (.-target eventObj))))}]]
       [:div.field
        [:label.label {:for :message} "Message"]
        [errors-component errors :message]
        [:textarea.textarea {:name      :message
                             :value     (:message @fields)
                             :on-change (fn [eventObj]
                                          (swap! fields assoc :message (.-value (.-target eventObj))))}]]
       [:input.button.is-primary {:type     :submit
                                  :value    "Comment"
                                  :on-click #(send-message! fields errors)}]])))

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
          [message-form]]]))))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting components...")
  (dom/render [#'home] (.getElementById js/document "content"))
  (.log js/console "Components mounted!"))

(defn init! []
  (.log js/console "Initializing app...")
  (rf/dispatch [:app/initialize])
  (get-messages)
  (mount-components))

(dom/render [home] (.getElementById js/document "content"))
