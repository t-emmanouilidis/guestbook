(ns guestbook.views.home
  (:require [re-frame.core :as rf]
            [guestbook.messages :as msg]
            [guestbook.auth :as auth]))

(def home-controllers
  [{:start (fn [_] (rf/dispatch [:messages/load msg/default-page-size 0]))}])

(defn home [{{{post-id :post-id} :query} :parameters}]
  (fn []
    [:div.content>div.columns.is-centered>div.column.is-two-thirds
     [:div.columns>div.column
      [:h3 "Messages"]
      (if @(rf/subscribe [:messages/loading?])
        [msg/message-list-placeholder]
        [:<>
         [msg/message-list post-id]
         [msg/load-more-button]])]
     [:div.columns>div.column
      [msg/reload-messages-button]]
     [:div.columns>div.column
      (case @(rf/subscribe [:auth/user-state])
        :loading
        [:div {:style {:width "5em"}}
         [:progress.progress.is-dark.is-small {:max 100} "30%"]]
        :authenticated
        [msg/message-form]
        :anonymous
        [:div.notification.is-clearfix
         [:span "Log in or create an account to post a message!"]
         [:div.buttons.is-pulled-right
          [auth/login-button]
          [auth/register-button]]])]]))