(ns guestbook.views.feed
  (:require [guestbook.auth :as auth]
            [guestbook.messages :as msg]
            [re-frame.core :as rf]))

(def feed-controllers
  [{:identity #(js/Date.)
    :start    (fn [_]
                (rf/dispatch [:messages/load-feed msg/default-page-size 0]))}])

(defn feed [{{{:keys [post_id]} :query} :parameters}]
  [:div.content
   [:div.columns.is-centered>div.column.is-two-thirds
    (case @(rf/subscribe [:auth/user-state])
      :loading
      [:div.columns>div.column {:style {:width "5em"}}
       [:progress.progress.is-dark.is-small {:max 100} "30%"]]
      :authenticated
      [:<>
       [:div.columns>div.column
        [:h3 (str "My Feed")]
        (if @(rf/subscribe [:messages/loading?])
          [msg/message-list-placeholder]
          [msg/message-list post_id])]
       [:div.columns>div.column
        [msg/message-form]]]
      :anonymous
      [:div.columns>div.column
       [:div.notification.is-clearfix
        [:span
         "Log in or create an account to curate a personalized feed!"]
        [:div.buttons.is-pulled-right
         [auth/login-button]
         [auth/register-button]]]])]])