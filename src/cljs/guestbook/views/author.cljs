(ns guestbook.views.author
  (:require [guestbook.messages :as msg]
            [guestbook.subscriptions :as sub]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rtfe]))

(defn banner-component [url]
  [:figure.image {:style {:width        "100%"
                          :height       "10vw"
                          :overflow     "hidden"
                          :margin-left  0
                          :margin-right 0}}
   [:img {:src url}]])

(defn title []
  (if @(rf/subscribe [:author/is-current?])
    [:div.level
     [:h2.level-left "My Author Page"]
     [:a.level-right {:href (rtfe/href :guestbook.routes.app/profile)} "Edit Page"]]
    (let [{:keys [display-name login]} @(rf/subscribe [:author/author])]
      [:h2 display-name " <@" login ">'s Page"])))

(defn author [{{{:keys [user]}    :path
                {:keys [post-id]} :query} :parameters}]
  (let [author @(rf/subscribe [:author/author])]
    (if @(rf/subscribe [:author/loading?])
      [:div.content
       [:div {:style {:width "100%"}}
        [:progress.progress.is-dark {:max 100} "30%"]]]
      (let [{{:keys [display-name banner bio]} :profile} author]
        [:div.content
         [banner-component (or banner "/img/banner_default.png")]
         [title]
         (when bio
           [:p bio])
         [:div.columns.is-centered>div.column.is-two-thirds
          [:div.columns>div.column
           [:h3 "Posts by " display-name " <@" user ">"]
           (when-not @(rf/subscribe [:author/is-current?])
             [sub/subscribe-button :follows user])
           (if @(rf/subscribe [:messages/loading?])
             [msg/message-list-placeholder]
             [msg/message-list post-id])]
          (when @(rf/subscribe [:author/is-current?])
            [:div.columns>div.column
             [:h4 "New Post"]
             [msg/message-form]])]]))))

(rf/reg-event-fx
  :author/fetch
  (fn [{:keys [db]} [_ login]]
    {:db       (assoc db
                 :author/author nil
                 :author/loading? true)
     :ajax/get {:url           (str "/api/author/" login)
                :success-event [:author/set]}}))

(rf/reg-event-db
  :author/set
  (fn [db [_ author]]
    (if author
      (assoc db
        :author/author author
        :author/loading? false)
      (dissoc db :author/author))))

(rf/reg-sub
  :author/author
  (fn [db _]
    (get db :author/author)))

(rf/reg-sub
  :author/is-current?
  :<- [:auth/user]
  :<- [:author/author]
  (fn [[user author] _]
    (= (:login user) (:login author))))

(rf/reg-sub
  :author/loading?
  (fn [db _]
    (:author/loading? db)))

(def author-controllers
  [{:parameters {:path [:user]}
    :start      (fn [{{:keys [user]} :path}]
                  (rf/dispatch [:messages/load-by-author user msg/default-page-size 0]))}
   {:parameters {:path [:user]}
    :start      (fn [{{:keys [user]} :path}]
                  (rf/dispatch [:author/fetch user]))
    :stop       (fn [_] (rf/dispatch [:author/set nil]))}])