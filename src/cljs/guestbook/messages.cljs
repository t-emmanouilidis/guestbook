(ns guestbook.messages
  (:require [clojure.string :as string]
            [guestbook.components :refer [image image-uploader md text-input textarea-input]]
            [guestbook.re-frame-utils :refer [<sub >evt]]
            [guestbook.modals :as modals]
            [guestbook.validation :refer [validate-message]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as dom]
            [reitit.frontend.easy :as rtfe]
            [guestbook.ajax]))

(def default-page-size 5)

(rf/reg-event-fx
  :messages/load-by-author
  (fn [{:keys [db]} [_ author page-size page-start]]
    {:db       (assoc db
                 :messages/loading? true
                 :messages/list nil
                 :messages/filter {:poster author})
     :ajax/get {:url           (str "/api/messages/by/" author "?page-size=" page-size "&page-start=" page-start)
                :success-path  [:messages :count]
                :success-event [:messages/set page-start]}}))

(rf/reg-event-fx
  :messages/load
  (fn [{:keys [db]} [_ page-size page-start]]
    {:db       (assoc db
                 :messages/loading? true
                 :messages/list (if (> page-start 0) (:messages/list db) nil)
                 :messages/start page-start
                 :messages/filter nil)
     :ajax/get {:url           (str "/api/messages?page-size=" page-size "&page-start=" page-start)
                :success-path  [:messages :count]
                :success-event [:messages/set page-start]}}))

(rf/reg-event-db
  :messages/set
  [rf/trim-v]
  (fn [db [page-start messages count]]
    (let [messages (if (> page-start 0)
                     (into (:messages/list db) messages)
                     messages)]
      (assoc db :messages/loading? false
                :messages/page-start page-start
                :messages/list messages
                :messages/count count))))

(rf/reg-sub
  :messages/loading?
  (fn [db _]
    (:messages/loading? db)))

(rf/reg-sub
  :messages/page-start
  (fn [db _]
    (:messages/page-start db)))

(rf/reg-sub
  :messages/count
  (fn [db _]
    (:messages/count db)))

(rf/reg-sub
  :messages/all
  (fn [db _]
    (:messages/list db [])))

(rf/reg-sub
  :messages/list
  :<- [:messages/all]
  (fn [messages _]
    (:list
      (reduce
        (fn [{:keys [ids list] :as acc} {:keys [id] :as msg}]
          (if (contains? ids id)
            acc
            {:list (conj list msg)
             :ids  (conj ids id)}))
        {:list []
         :ids  #{}}
        messages))))

(defn reload-messages-button []
  (let [loading? (<sub [:messages/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(>evt [:messages/load default-page-size 0])
      :disabled loading?}
     (if loading?
       "Loading messages from server"
       "Refresh messages")]))

(defn load-more-button []
  (let [loading? (<sub [:messages/loading?])
        page-start (<sub [:messages/page-start])
        message-count (<sub [:messages/count])
        new-page-start (+ page-start default-page-size)]
    (when (> message-count (+ page-start default-page-size))
      [:button.button.is-info.is-fullwidth
       {:on-click #(>evt [:messages/load default-page-size new-page-start])
        :disabled loading?}
       (if loading?
         "Loading messages from server"
         "Load more messages")])))

(defn post-meta [{:keys [id is_boost timestamp posted_at poster poster_avatar source source_avatar]}]
  (let [posted_at (or posted_at timestamp)]
    [:<>
     (when is_boost
       [:div.columns.is-centered.is-1.mb-0
        [:div.column.is-narrow.pb-0
         [image (or poster_avatar "/img/avatar_default.png") 24 24]]
        [:div.column.is-narrow.pb-0
         [:a {:href (str "/user/" poster "?post-id=" id)} poster]]
        [:div.column.is-narrow.pb-0 "♻️"]
        [:div.column.is-narrow.pb-0
         [image (or source_avatar "/img/avatar_default.png") 24 24]]
        [:div.column.pb-0 #_{:style {:text-align "left"}}
         [:a {:href (str "/user/" source "?post-id=" id)} source]]])
     [:div.mb-4>time
      (if posted_at
        (.toLocaleString posted_at)
        "NULL POSTED_AT")]]))

(defn message-content
  [{:keys [messages name author] :as m}]
  [:<>
   (if (seq messages)
     (doall
       (for [{:keys [message id]} (reverse messages)]
         ^{:key id}
         [md :p.reply-chain-item message]))
     [md (:message m)])
   [:p " - " name
    " <"
    (if author
      [:a {:href (str "/user/" author)} (str "@" author)]
      [:span.is-italic "account not found"])
    ">"]])

(defn expand-post-button [{:keys [id root_id]}]
  [:button.button.level-item.is-rounded.is-small.is-secondary.is-outlined
   {:on-click (fn [_]
                (let [{{:keys [name]}       :data
                       {:keys [path query]} :parameters}
                      @(rf/subscribe [:router/current-route])]
                  (rtfe/replace-state name path (assoc query :post-id id)))
                (rtfe/push-state :guestbook.routes.app/post {:post-id root_id}
                                 (when (not= root_id id
                                             {:reply-id id}))))}
   [:i.material-icons
    "open_in_new"]])

(defn boost-button [{:keys [boosts] :as m}]
  [:button.button.level-item.is-rounded.is-small.is-info.is-outlined
   {:on-click (fn [_] (>evt [:message/boost! m]))
    :disabled (nil? (<sub [:auth/user]))}
   "♻️ " boosts])

(declare reply-modal)

(defn reply-button
  [{:keys [reply_count] :as m}]
  [:<>
   [reply-modal m]
   [:button.button.is-rounded.is-small.is-outlined.level-item
    {:on-click (fn []
                 (>evt [:form/clear])
                 (>evt [:app/show-modal [:reply-modal (:id m)]]))
     :disabled (not= (<sub [:auth/user-state]) :authenticated)}
    [:span.material-icons
     {:style {:font-size "inherit"}}
     "chat"]
    [:span.ml-1 reply_count]]])

(defn message
  ([m] [message m {}])
  ([{:keys [avatar boosts] :or {boosts 0} :as m}
    {:keys [include-link? include-bar?] :or {include-link? true
                                             include-bar?  true}}]
   [:article.media
    [:figure.media-left
     [image (or avatar "/img/avatar_default.png") 128 128]]
    [:div.media-content
     [:div.content
      [post-meta m]
      [message-content m]]
     (when include-bar?
       [:nav.level
        [:div.level-left
         (when include-link?
           [expand-post-button m])
         [boost-button m]
         [reply-button m]]])]]))

(defn msg-li
  [m message-id]
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
  ([] (message-list nil))
  ([message-id]
   [:ul.messages
    (for [m (<sub [:messages/list])]
      ^{:key (:id m)}
      [msg-li m message-id])]))

(defn message-list-placeholder []
  [:ul.messages
   [:li
    [:p "Loading Messages..."]
    [:div {:style {:width "10em"}}
     [:progress.progress.is-dark {:max 100} "30%"]]]])

(defn add-message? [filter-map msg]
  (every?
    (fn [[k matcher]]
      (let [v (get msg k)]
        (cond
          (set? matcher)
          (matcher v)
          (fn? matcher)
          (matcher v)
          :else
          (= matcher v))))
    filter-map))

(rf/reg-event-db
  :messages/add
  [rf/trim-v]
  (fn [db [message]]
    (let [msg-filter (:messages/filter db)
          filters (cond
                    (nil? msg-filter)
                    {:matches-all (fn [_] true)}
                    :else
                    msg-filter)]
      (if (add-message? filters message)
        (update db :messages/list conj message)
        db))))

(rf/reg-event-db
  :form/set-field
  [(rf/path :form/fields) rf/trim-v]
  (fn [fields [id value]]
    (assoc fields id value)))

(rf/reg-event-fx
  :form/clear
  (fn [_ _]
    {:dispatch-n [[:form/clear-fields]
                  [:message/clear-media]]}))

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
  [(rf/path :form/server-errors) rf/trim-v]
  (fn [_ [errors]]
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
      {:dispatch-n [[:form/clear-fields]
                    [:message/clear-media]
                    [:app/hide-reply-modals]]}
      {:dispatch [:form/set-server-errors errors]})))

(rf/reg-event-db
  :app/hide-reply-modals
  (fn [db _]
    (update db :app/active-modals
            (fn [current-active]
              (into {}
                    ;; transducer
                    (remove (fn [[k _]] (= :reply-modal k)))
                    current-active)))))

(rf/reg-event-fx
  :message/send!
  (fn [{:keys [db]} [_ fields media]]
    (if (not-empty media)
      {:db (dissoc db :form/server-errors)
       :ajax/upload-media!
       {:url   "/api/my-account/media/upload"
        :files media
        :handler
        (fn [response]
          (rf/dispatch
            ;; resend the !message/send but without any media now
            [:message/send!
             (update fields :message
                     string/replace
                     #"\!\[(.*)\]\((.+)\)"
                     (fn [[_ alt url]]
                       (str "![" alt "]("
                            (if-some [name ((:message/urls db) url)]
                              (get response name)
                              url)
                            ")")))]))}}
      {:db       (dissoc db :form/server-errors)
       :ws/send! {:message        [:message/create! fields]
                  :timeout        10000
                  :callback-event [:message/send!-called-back]}})))

(rf/reg-event-fx
  :message/boost!
  (fn [_ [_ message]]
    (when message
      {:ws/send!
       {:message [:message/boost! (select-keys message [:id :poster])]}})))

(rf/reg-event-db
  :message/save-media
  [rf/trim-v]
  (fn [db [img]]
    (let [url (js/URL.createObjectURL img)
          ;; create a random name instead of the user name that we had for the avatar
          name (keyword (str "msg-" (random-uuid)))]
      (-> db
          (update-in [:form/fields :message] str "![](" url ")")
          ;; if no other :message/media in db pass empty map
          (update :message/media (fnil assoc {}) name img)
          (update :message/urls (fnil assoc {}) url name)))))

(rf/reg-event-db
  :message/clear-media
  (fn [db _]
    (dissoc db :message/media :message/urls)))

(rf/reg-sub
  :message/media
  (fn [db _]
    (:message/media db)))

(rf/reg-event-fx
  :messages/load-by-tag
  (fn [{:keys [db]} [_ tag page-size page-start]]
    {:db       (assoc db
                 :messages/loading? true
                 :messages/filter
                 {:message #(re-find (re-pattern (str "(?<=\\s|^)#" tag "(?=\\s|$)")) %)}
                 :messages/list nil)
     :ajax/get {:url           (str "/api/messages/tagged/" tag "?page-size=" page-size "&page-start" page-start)
                :success-path  [:messages :count]
                :success-event [:messages/set page-start]}}))

(rf/reg-event-fx
  :messages/load-feed
  (fn [{:keys [db]} [_ page-size page-start]]
    (let [{:keys [follows tags]} (get-in db [:auth/user :profile :subscriptions])]
      {:db       (assoc db
                   :messages/loading? true
                   :messages/list nil
                   :messages/filter
                   [{:message
                     #(some (fn [tag] (re-find (re-pattern (str "(?<=\\s|^)#" tag "(?=\\s|$)")) %)) tags)
                     :poster #(some (partial = %) follows)}])
       :ajax/get {:url           (str "/api/messages/feed?page-size=" page-size "&page-start=" page-start)
                  :success-path  [:messages :count]
                  :success-event [:messages/set page-start]}})))

(defn errors-component [id & [message]]
  (when-let [error (<sub [:form/error id])]
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

(defn message-form-preview [parent]
  (let [{:keys [login profile]} (<sub [:auth/user])
        display-name (:display-name profile login)
        msg {:message   (<sub [:form/field :message])
             :id        -1
             :timestamp (js/Date.)
             :name      display-name
             :author    login
             :avatar    (:avatar profile)}]
    [message-preview
     (assoc msg :messages (cons msg (:messages parent)))]))

(defn message-form-content []
  (let [{:keys [login profile]} (<sub [:auth/user])
        display-name (:display-name profile login)]
    [:<>
     [errors-component :server-error]
     [errors-component :unauthorized "Please log in before posting."]
     [:div.field
      [:label.label {:for :name} "Name"]
      display-name]
     [:div.field
      [:div.control
       [image-uploader
        #(>evt [:message/save-media %])
        "Insert an image"]]]
     [:div.field
      [:label.label {:for :message} "Message"]
      [errors-component :message]
      [textarea-input {:attrs        {:name :message}
                       :save-timeout 1000
                       :value        (rf/subscribe [:form/field :message])
                       :on-save      #(rf/dispatch [:form/set-field :message %])}]]]))

(defn reply-modal [parent]
  [modals/modal-card
   {:id       [:reply-modal (:id parent)]
    :on-close #(>evt [:form/clear])}
   (str "Reply to post by user: " (:author parent))
   [:<>
    [message-form-preview parent]
    [message-form-content]]
   [:input.button.is-primary.is-fullwidth
    {:type     :submit
     :disabled (<sub [:form/validation-errors?])
     :on-click #(>evt [:message/send!
                       (assoc
                         (<sub [:form/fields])
                         :parent (:id parent))
                       (<sub [:message/media])])
     :value    (str "Reply to " (:author parent))}]])

(defn message-form []
  [:div.card
   [:div.card-header>p.card-header-title
    "Post Something!"]
   [:div.card-content
    [message-form-preview {}]
    [message-form-content]
    [:input.button.is-primary
     {:type     :submit
      :disabled (<sub [:form/validation-errors?])
      :value    "Comment"
      :on-click (fn [_]
                  (>evt [:message/send!
                         (<sub [:form/fields])
                         (<sub [:message/media])]))}]]])
