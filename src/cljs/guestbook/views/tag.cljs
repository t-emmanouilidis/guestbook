(ns guestbook.views.tag
  (:require
    [guestbook.messages :as messages]
    [guestbook.subscriptions :as sub]
    [re-frame.core :as rf]))

(def tag-controllers
  [{:parameters {:path [:tag]}
    :start      (fn [{{:keys [tag]} :path}]
                  (rf/dispatch [:messages/load-by-tag tag messages/default-page-size 0]))}])

(defn tag [{{{:keys [tag]}     :path
             {:keys [post_id]} :query} :parameters}]
  [:div.content
   [:div.columns.is-centered>div.column.is-two-thirds
    [:div.columns>div.column
     [:h3 (str "Posts tagged #" tag)]
     [sub/subscribe-button :tags tag]
     (if @(rf/subscribe [:messages/loading?])
       [messages/message-list-placeholder]
       [messages/message-list post_id])]]])