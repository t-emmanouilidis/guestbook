(ns guestbook.messages
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as dom]
            [clojure.string :as string]
            [guestbook.validation :refer [validate-message]]
            [guestbook.components :refer [text-input textarea-input image md]]
            [reitit.frontend.easy :as rtfe]))

(rf/reg-event-fx
  :messages/load-by-author
  (fn [{:keys [db]} [_ author]]
    {:db       (assoc db
                 :messages/loading? true
                 :messages/list nil
                 :messages/filter {:author author})
     :ajax/get {:url           (str "/api/messages/by/" author)
                :success-path  [:messages]
                :success-event [:messages/set]}}))

(rf/reg-event-fx
  :messages/load
  (fn [{:keys [db]} _]
    {:db       (assoc db
                 :messages/loading? true
                 :messages/list nil
                 :messages/filter nil)
     :ajax/get {:url           "/api/messages"
                :success-path  [:messages]
                :success-event [:messages/set]}}))

(rf/reg-event-db
  :messages/set
  (fn [db [_ messages]]
    (-> db
        (assoc :messages/loading? false
               :messages/list messages))))

(rf/reg-sub
  :messages/loading?
  (fn [db _]
    (:messages/loading? db)))

(rf/reg-sub
  :messages/list
  (fn [db _]
    (:messages/list db [])))

(defn reload-messages-button []
  (let [loading? (rf/subscribe [:messages/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(rf/dispatch [:messages/load])
      :disabled @loading?}
     (if @loading?
       "Loading messages from server"
       "Refresh messages")]))

(defn message
  ([m] [message m {}])
  ([{:keys [id timestamp message name author avatar] :as m}
    {:keys [include-link?] :or {include-link? true}}]
   [:article.media
    [:figure.media-left
     [image (or avatar "/img/avatar_default.png") 128 128]]
    [:div.media-content>div.content
     [:time (.toLocaleString timestamp)]
     [md message]
     (when include-link?
       [:p>a {:on-click (fn [_]
                          (let [{{:keys [name]}       :data
                                 {:keys [path query]} :parameters}
                                @(rf/subscribe [:router/current-route])]
                            (rtfe/replace-state name path (assoc query :post-id id)))
                          (rtfe/push-state :guestbook.routes.app/post {:post-id id}))}
        "View Post"])
     [:p " - " name
      " <"
      (if author
        [:a {:href (str "/user/" author)} (str "@" author)]
        [:span.is-italic "account not found"])
      ">"]]]))

(defn msg-li [m message-id]
  (r/create-class
    {:component-did-mount
     (fn [this]
       (when (= message-id (:id m))
         (.scrollIntoView (dom/dom-node this))))
     :reagent-render
     (fn [_]
       [:li
        [message m]])}))

(defn message-list
  ([] [message-list nil])
  ([message-id]
   [:ul.messages
    (for [m @(rf/subscribe [:messages/list])]
      ^{:key (:timestamp m)}
      [msg-li m message-id])]))

(defn message-list-placeholder []
  [:ul.messages
   [:li
    [:p "Loading Messages..."]
    [:div {:style {:width "10em"}}
     [:progress.progress.is-dark {:max 100} "30%"]]]])

(defn add-message? [filter-map message]
  (every?
    (fn [[filter-map-key filter-map-value]]
      (let [message-value-for-key (get message filter-map-key)]
        (cond
          (set? filter-map-value)
          (filter-map-value message-value-for-key)
          (fn? filter-map-value)
          (filter-map-value message-value-for-key)
          :else
          (= filter-map-value message-value-for-key))))
    filter-map))

(rf/reg-event-db
  :messages/add
  (fn [db [_ message]]
    (if (add-message? (:messages/filter db) message)
      (update db :messages/list (fn [messages] (conj messages message)))
      db)))

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

(rf/reg-event-fx
  :message/send!-called-back
  (fn [_ [_ {:keys [success errors]}]]
    (if success
      {:dispatch [:form/clear-fields]}
      {:dispatch [:form/set-server-errors errors]})))

(rf/reg-event-fx
  :message/send!
  (fn [{:keys [db]} [_ fields]]
    {:db       (dissoc db :form/server-errors)
     :ws/send! {:message        [:message/create! fields]
                :timeout        10000
                :callback-event [:message/send!-called-back]}}))

(rf/reg-event-db
  :message/save-media
  (fn [db [_ img]]
    (let [url (.createObjectURL js/URL img)
          name (keyword (str "msg-" (random-uuid)))]
      (-> db
          (update-in [:form/fields :message] str "![](" url ")")
          (update :message/media (fnil assoc {}) name img)
          (update :message/urls (fnil assoc {}) url name)))))

(defn errors-component [id & [message]]
  (when-let [error @(rf/subscribe [:form/error id])]
    [:div.notification.is-danger (if message
                                   message
                                   (string/join error))]))

(defn message-preview [m]
  (r/with-let
    [expanded (r/atom false)]
    [:<>
     [:button.button.is-secondary.is-fullwidth
      {:on-click #(swap! expanded not)}
      (if @expanded
        "Hide Preview"
        "Show Preview")]
     (when @expanded
       [:ul.messages
        {:style
         {:margin-left 0}}
        [:li
         [message m {:include-link? false}]]])]))

(defn message-form []
  [:div.card
   [:div.card-header>p.card-header-title
    "Post Something!"]
   (let [{:keys [login profile]} @(rf/subscribe [:auth/user])
         display-name (:display-name profile login)]
     [:div.card-content
      [message-preview {:message   @(rf/subscribe [:form/field :message])
                        :id        -1
                        :timestamp (js/Date.)
                        :name      display-name
                        :author    login
                        :avatar    (:avatar profile)}]
      [errors-component :server-error]
      [errors-component :unauthorized "Please log in before posting."]
      [:div.field
       [:label.label {:for :name} "Name"]
       display-name
       [:div.field
        [:label.label {:for :message} "Message"]
        [errors-component :message]
        [textarea-input {:attrs        {:name :message}
                         :save-timeout 1000
                         :value        (rf/subscribe [:form/field :message])
                         :on-save      #(rf/dispatch [:form/set-field :message %])}]]
       [:input.button.is-primary {:type     :submit
                                  :disabled @(rf/subscribe [:form/validation-errors?])
                                  :value    "Comment"
                                  :on-click (fn [_]
                                              (rf/dispatch [:message/send!
                                                            @(rf/subscribe [:form/fields])]))}]]])])
