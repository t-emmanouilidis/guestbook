(ns guestbook.views.post
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [cljs.pprint :refer [pprint]]
            [guestbook.messages :as msg]))

(defn clear-post-keys [db]
  (dissoc db ::error ::post))

(rf/reg-event-fx
  :post/fetch
  (fn [{:keys [db]} [_ post-id]]
    {:db       (clear-post-keys db)
     :ajax/get {:url           (str "/api/message/" post-id)
                :success-path  [:message]
                :success-event [:post/set]
                :error-event   [:post/set-error]}}))

(rf/reg-event-db
  :post/set
  (fn [db [_ post]]
    (assoc db ::post post)))

(rf/reg-event-db
  :post/set-error
  (fn [db [_ error]]
    (assoc db ::error error)))

(rf/reg-event-db
  :post/clear
  (fn [db _]
    (clear-post-keys db)))

(rf/reg-sub
  ::post
  (fn [db _]
    (::post db nil)))

(rf/reg-sub
  ::error
  (fn [db _]
    (::error db)))

(rf/reg-sub
  :post/loading?
  :<- [::post]
  :<- [::error]
  (fn [[post error] _]
    (and (empty? post) (empty? error))))

(def post-controllers
  [{:parameters {:path [:post-id]}
    :start      (fn [{{:keys [post-id]} :path}]
                  (rf/dispatch [:post/fetch post-id]))
    :stop       (fn [_]
                  (rf/dispatch [:post/clear]))}])

(defn loading-bar []
  [:progress.progress.is-dark {:max 100} "30%"])

(defn post [{:keys [name author message timestamp avatar] :as post-content}]
  [:div.content
   [:button.button.is-info.is-outlined.is-fullwidth
    {:on-click #(.back (.-history js/window))}
    "Back to Feed"]
   [:h3.title.is-3 "Post by " name
    "<" [:a {:href (str "/user/" author)} (str "@" author)] ">"]
   [:h4.subtitle.is-4 "Posted at " (.toLocaleString timestamp)]
   [msg/message post-content {:include-link? false}]])

(defn post-page [_]
  (let [post-content @(rf/subscribe [::post])
        {status            :status
         {:keys [message]} :response :as error} @(rf/subscribe [::error])]
    (cond
      @(rf/subscribe [:post/loading?])
      [:div.content
       [:p "Loading message..."]
       [loading-bar]]
      (seq error)
      (case status
        404
        [:div.content
         [:p (or message "Post not found.")]
         [:pre (with-out-str (pprint @error))]]
        403
        [:div.content
         [:p (or message "You are not allowed to view this post.")]
         [:pre (with-out-str (pprint @error))]]
        [:div
         [:p (or message "Unknown error")]
         [:pre (with-out-str (pprint @error))]])
      (seq post-content)
      [post post-content])))