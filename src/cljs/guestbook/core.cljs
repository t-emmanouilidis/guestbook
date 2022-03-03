(ns guestbook.core
  (:require [reagent.core :as r]
            [reagent.dom :as dom]
            [re-frame.core :as rf]
            [ajax.core :refer [GET POST]]
            [guestbook.validation :refer [validate-message]]
            [guestbook.websockets :as ws]
            [guestbook.auth :as auth]
            [guestbook.messages :as msg]
            [guestbook.routes.app :refer [app-routes]]
            [guestbook.ajax :as ajax]
            [mount.core :as mount]
            [reitit.coercion.spec :as reitit-spec]
            [reitit.frontend :as rtf]
            [reitit.frontend.easy :as rtfe]
            [reitit.frontend.controllers :as rtfc]))

(rf/reg-event-fx
  :app/initialize
  (fn [_ _]
    {:db       {:session/loading? true}
     :dispatch [:session/load]}))

(def router
  (rtf/router
    (app-routes)
    {:data {:coercion reitit-spec/coercion}}))

(rf/reg-event-db
  :router/navigated
  (fn [db [_ new-match]]
    (assoc db :router/current-route new-match)))

(rf/reg-sub
  :router/current-route
  (fn [db _]
    (:router/current-route db)))

(defn init-routes! []
  (rtfe/start!
    router
    (fn [new-match _]
      (when new-match
        (let [{controllers :controllers} @(rf/subscribe [:router/current-route])
              new-match-with-controllers (assoc new-match :controllers (rtfc/apply-controllers controllers new-match))]
          (rf/dispatch [:router/navigated new-match-with-controllers]))))
    {:use-fragment false}))

(defn navbar []
  (let [burger-active (r/atom false)]
    (fn []
      [:nav.navbar.is-info
       [:div.container
        [:div.navbar-brand
         [:a.navbar-item {:href "/" :style {:font-weight "bold"}} "guestbook"]
         [:span.navbar-burger.burger
          {:data-target "nav-menu"
           :on-click    #(swap! burger-active not)
           :class       (when @burger-active "is-active")}
          [:span]
          [:span]
          [:span]]]
        [:div#nav-menu.navbar-menu
         {:class (when @burger-active "is-active")}
         [:div.navbar-start
          [:a.navbar-item {:href "/"} "Home"]
          (when (= @(rf/subscribe [:auth/user-state]) :authenticated)
            [:a.navbar-item
             {:href (rtfe/href :guestbook.routes.app/author
                               {:user (:login @(rf/subscribe [:auth/user]))})}
             "My Posts"])]
         [:div.navbar-end
          [:div.navbar-item
           (case @(rf/subscribe [:auth/user-state])
             :loading
             [:div {:style {:width "5em"}}
              [:progress.progress.is-dark.is-small {:max 100} "30%"]]
             :authenticated
             [:div.buttons
              [auth/nameplate @(rf/subscribe [:auth/user])]
              [auth/logout-button]]
             :anonymous
             [:div.buttons
              [auth/login-button]
              [auth/register-button]])]]]]])))

(defn home []
  (fn []
    (if @(rf/subscribe [:messages/loading?])
      [:div>div.row>div.span12>h3 "Loading Messages..."]
      [:div.content>div.columns.is-centered>div.column.is-two-thirds
       [:div.columns>div.column
        [:h3 "Messages"]
        [msg/message-list]]
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
            [auth/register-button]]])]])))

(defn page [{{:keys [view name]} :data
             path                :path
             :as                 match}]
  [:section.section>div.container
   (if view
     [view match]
     [:div "No view specified for route: " name " (" path ")"])])

(defn app []
  (let [current-route @(rf/subscribe [:router/current-route])]
    [:div.app
     [navbar]
     [page current-route]]))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting components...")
  (init-routes!)
  (dom/render [#'app] (.getElementById js/document "content"))
  (.log js/console "Components mounted!"))

(defn init! []
  (.log js/console "Initializing app...")
  (mount/start)
  (rf/dispatch-sync [:app/initialize])
  (mount-components))
