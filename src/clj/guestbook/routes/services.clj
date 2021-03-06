(ns guestbook.routes.services
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [guestbook.auth :as auth]
            [guestbook.auth.ring :as gauth]
            [guestbook.author :as author]
            [guestbook.db.core :as db]
            [guestbook.media :as media]
            [guestbook.messages :as msg]
            [guestbook.middleware.formats :as formats]
            [guestbook.session :as session]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.http-response :as response]
            [spec-tools.data-spec :as ds])
  (:import (clojure.lang ExceptionInfo)))

(def default-page-start 0)
(def default-page-size 5)

(defn service-routes []
  ["/api"
   {:middleware [parameters/parameters-middleware
                 muuntaja/format-negotiate-middleware
                 muuntaja/format-response-middleware
                 exception/exception-middleware
                 muuntaja/format-request-middleware
                 coercion/coerce-request-middleware
                 coercion/coerce-response-middleware
                 multipart/multipart-middleware
                 gauth/mw]
    :muuntaja   formats/instance
    :coercion   spec-coercion/coercion
    :swagger    {:id ::api}}
   ["" {:no-doc      true
        ::auth/roles (auth/roles :swagger/swagger)}
    ["/swagger.json" {:get (swagger/create-swagger-handler)}]
    ["/swagger-ui*" {:get (swagger-ui/create-swagger-ui-handler {:url "/api/swagger.json"})}]]
   ["/messages"
    {::auth/roles (auth/roles :messages/list)
     :parameters  {:query {(ds/opt :boosts)     boolean?
                           (ds/opt :page-size)  int?
                           (ds/opt :page-start) int?}}}
    [""
     {:get
      {:responses
       {200
        {:body
         {:count int?
          :messages    [msg/post?]}}}
       :handler
       (fn [{{{:keys [boosts page-size page-start]
               :or   {boosts     true
                      page-size  default-page-size
                      page-start default-page-start}} :query} :parameters}]
         (response/ok (if boosts
                        (merge
                          (msg/timeline-message-count)
                          (msg/timeline page-size page-start))
                        (merge
                          (msg/message-count)
                          (msg/message-list page-size page-start)))))}}]
    ["/by/:author"
     {::auth/roles (auth/roles :messages/list)
      :get
      {:parameters {:path {:author string?}}
       :responses
       {200
        {:body
         {:messages
          [msg/post?]}}}
       :handler
       (fn [{{{:keys [author]}                        :path
              {:keys [boosts page-size page-start]
               :or   {boosts     true
                      page-size  default-page-size
                      page-start default-page-start}} :query} :parameters}]
         (response/ok (if boosts
                        (msg/timeline-for-poster author page-size page-start)
                        (msg/messages-by-author author page-size page-start))))}}]
    ["/tagged/:tag"
     {::auth/roles (auth/roles :messages/list)
      :get
      {:parameters {:path {:tag string?}}
       :responses
       {200
        {:body
         {:messages
          [msg/post?]}}}
       :handler
       (fn [{{{:keys [tag]}                           :path
              {:keys [boosts page-size page-start]
               :or   {boosts     true
                      page-size  default-page-size
                      page-start default-page-start}} :query} :parameters}]
         (if boosts
           (response/ok (msg/get-feed-for-tag tag page-size page-start))
           (response/not-implemented {:message "Tags cannot filter out boosts."})))}}]
    ["/feed"
     {::auth/roles (auth/roles :messages/feed)
      :get
      {:responses
       {200
        {:body
         {:messages
          [msg/post?]}}}
       :handler
       (fn [{{{:keys [boosts page-size page-start]
               :or   {boosts     true
                      page-size  default-page-size
                      page-start default-page-start}} :query} :parameters
             {{{:keys [subscriptions]} :profile} :identity}   :session}]
         (if boosts
           (response/ok (msg/get-feed subscriptions page-size page-start))
           (response/not-implemented {:message "Feed cannot filter out boosts."})))}}]]
   ["/message"
    ["/:post-id"
     {:parameters
      {:path {:post-id pos-int?}}}
     [""
      {::auth/roles (auth/roles :message/get)
       :get
       {:responses
        {200 {:message msg/post?}
         403 {:message string?}
         404 {:message string?}
         500 {:message string?}}
        :handler
        (fn [{{{:keys [post-id]} :path} :parameters}]
          (if-some [post (msg/get-post post-id)]
            (response/ok {:message post})
            (response/not-found {:message "Post not found"})))}}]
     ["/boost"
      {::auth/roles (auth/roles :message/boost!)
       :post
       {:parameters {:body {:poster (ds/maybe string?)}}
        :responses
        {200 {:body {:status keyword?
                     :post   msg/post?}}
         400 {:message string?}}
        :handler
        (fn [{{{:keys [post-id]} :path
               {:keys [poster]}  :body}   :parameters
              {{:keys [login]} :identity} :session}]
          ((try
             (let [post (msg/boost-post login post-id poster)]
               (response/ok {:status :ok
                             :post   post}))
             (catch Exception e
               (response/bad-request
                 {:message (str "Could not boost post with id " post-id " as " login)})))))}}]
     ["/replies"
      {::auth/roles (auth/roles :message/get)
       :get
       {:responses
        {200
         {:body
          {:replies [msg/post?]}}}
        :handler
        (fn [{{{:keys [post-id]} :path} :parameters}]
          (let [replies (msg/get-replies post-id)]
            (response/ok {:replies replies})))}}]
     ["/parents"
      {::auth/roles (auth/roles :message/get)
       :get
       {:responses
        {200
         {:body
          {:parents [msg/post?]}}}
        :handler
        (fn [{{{:keys [post-id]} :path} :parameters}]
          (let [parents (msg/get-parents post-id)]
            (response/ok {:parents parents})))}}]]
    [""
     {::auth/roles (auth/roles :message/create!)
      :post
      {:parameters
       {:body
        {:message         string?
         (ds/opt :parent) (ds/maybe int?)}}
       :responses
       {200 {:body {:status keyword?
                    :post   msg/post?}}
        400 {:body map?}
        500 {:errors map?}}
       :handler
       (fn [{{params :body}     :parameters
             {:keys [identity]} :session}]
         (try
           (->> (msg/save-message! identity params)
                (assoc {:status :ok} :post)
                (response/ok))
           (catch Exception e
             (let [{id     :guestbook/error-id
                    errors :errors} (ex-data e)]
               (case id
                 :validation
                 (response/bad-request {:errors errors})
                 (response/internal-server-error {:errors {:server-error ["Failed to save message!"]}}))))))}}]]
   ["/login"
    {::auth/roles (auth/roles :auth/login)
     :post
     {:parameters
      {:body
       {:login    string?
        :password string?}}
      :responses
      {:200
       {:body
        {:identity auth/user?}}
       :401
       {:body
        {:message string?}}}
      :handler
      (fn [{session                          :session
            {{:keys [login password]} :body} :parameters
            :as                              req}]
        (log/debug (str "login: " login ", password: " password))
        (if-some [user (auth/authenticate-user login password)]
          (let [new-session (assoc (or session {}) :identity user)]
            (log/debug "New session: " new-session)
            (session/write-session req new-session)
            (->
              (response/ok {:identity user})
              (assoc :session new-session)))
          (response/unauthorized {:message "Incorrect login or password."})))}}]
   ["/register"
    {::auth/roles (auth/roles :account/register)
     :post
     {:parameters
      {:body
       {:login    string?
        :password string?
        :confirm  string?}}
      :responses
      {:200
       {:body
        {:message string?}}
       :400
       {:body
        {:message string?}}
       :409
       {:body
        {:message string?}}}
      :handler
      (fn [{{{:keys [login password confirm]} :body} :parameters}]
        (if-not (= password confirm)
          (response/bad-request
            {:message "Password and confirm do not match."})
          (try
            (auth/create-user! login password)
            (response/ok
              {:message "User registration successful. Please log in."})
            (catch ExceptionInfo e
              (if (= (:guestbook/error-id (ex-data e)) ::auth/duplicate-user)
                (response/conflict
                  {:message "Registration failed! User with login already exists!"})
                (throw e))))))}}]
   ["/logout"
    {::auth/roles (auth/roles :auth/logout)
     :post
     {:handler
      (fn [_]
        (->
          (response/ok)
          (assoc,,, :session nil)))}}]
   ["/session"
    {::auth/roles (auth/roles :session/get)
     :get
     {:responses
      {200
       {:body
        {:session
         {:identity
          (ds/maybe auth/user?)}}}}
      :handler
      (fn [{{:keys [identity]} :session}]
        (let [toReturn (select-keys identity [:login :created_at :profile])]
          (response/ok {:session {:identity (not-empty toReturn)}})))}}]
   ["/author/:login"
    {::auth/roles (auth/roles :author/get)
     :get
     {:parameters
      {:path {:login string?}}
      :responses
      {200
       {:body auth/user?}
       500
       {:errors map?}}
      :handler
      (fn [{{{:keys [login]} :path} :parameters}]
        (response/ok (author/get-author login)))}}]
   ["/my-account"
    ["/delete-account"
     {::auth/roles (auth/roles :account/set-profile!)
      :post
      {:parameters
       {:body {:login    string?
               :password string?}}
       :handler
       (fn [{{{:keys [login password]} :body} :parameters
             {{user :login} :identity}        :session
             :as                              req}]
         (if (not= login user)
           (response/bad-request
             {:message "Login must match the current user!"})
           (try
             (auth/delete-account! user password)
             (-> (response/ok)
                 (assoc :session
                        (select-keys
                          (:session req)
                          [:ring.middleware.anti-forgery/anti-forgery-token])))
             (catch ExceptionInfo e
               (if (= (:guestbook/error-id (ex-data e)) ::auth/authentication-failure)
                 (response/unauthorized {:error   :incorrect-password
                                         :message "Password is incorrect, please try again!"})
                 (throw e))))))}}]
    ["/change-password"
     {::auth/roles (auth/roles :account/set-profile!)
      :post
      {:parameters
       {:body
        {:old-password     string?
         :new-password     string?
         :confirm-password string?}}
       :responses
       {200
        {:body map?}
        400
        {:body map?}
        401
        {:body map?}}
       :handler
       (fn [{{{:keys [old-password new-password :confirm-password]} :body} :parameters
             {{:keys [login]} :identity}                                   :session}]
         (if (not= new-password :confirm-password)
           (response/bad-request
             {:error   :mismatch
              :message "Password and confirm do not match!"})
           (try
             (auth/change-password! login old-password new-password)
             (response/ok {:success true})
             (catch ExceptionInfo e
               (if (= (:guestbook/error-id (ex-data e)) ::auth/authentication-failure)
                 (response/unauthorized
                   {:error   :incorrect-password
                    :message "Old password is incorrect, please try again"})
                 (throw e))))))}}]
    ["/set-profile"
     {::auth/roles (auth/roles :account/set-profile!)
      :post
      {:parameters
       {:body
        {:profile map?}}
       :responses
       {200
        {:body map?}
        500
        {:errors map?}}
       :handler
       (fn [{{{:keys [profile]} :body}   :parameters
             {{:keys [login]} :identity} :session}]
         (try
           (let [identity (author/set-author-profile login profile)]
             (update (response/ok {:success true})
                     :session
                     assoc :identity identity))
           (catch Exception e
             (log/error e)
             (response/internal-server-error
               {:errors {:server-error ["Failed to set profile!"]}}))))}}]
    ["/media/upload"
     {::auth/roles (auth/roles :media/upload)
      :post
      {:parameters
       {:multipart (s/map-of keyword? multipart/temp-file-part)}
       :handler
       (fn [{{multipart-items :multipart} :parameters
             {{:keys [login]} :identity}  :session}]
         (log/debug (str "Multipart-items: " multipart-items))
         (response/ok
           (reduce-kv
             (fn [acc name {:keys [size content-type] :as file-part}]
               (log/debug (str "The name of the file is: " name))
               (cond
                 (> size (* 5 1024 1024))
                 (do
                   (log/error "File " name " exceeds maximum size of 5 mgbs (size: " size ")")
                   (update acc :failed-uploads (fnil conj []) name))
                 (re-matches #"image/.*" content-type)
                 (-> acc
                     (update :files-uploaded conj name)
                     (assoc name
                            (str "/api/media/"
                                 (cond
                                   (= name :avatar)
                                   (media/insert-image-returning-name
                                     (assoc file-part :filename (str login "_avatar.png"))
                                     {:width  128
                                      :height 128
                                      :owner  login})
                                   (= name :banner)
                                   (media/insert-image-returning-name
                                     (assoc file-part :filename (str login "_banner.png"))
                                     {:width  1200
                                      :height 400
                                      :owner  login})
                                   :else
                                   (media/insert-image-returning-name
                                     (update
                                       file-part
                                       :filename
                                       string/replace #"\.[^\.]+$" ".png")
                                     {:max-width  800
                                      :max-height 2000
                                      :owner      login})))))
                 :else
                 (do
                   (log/error "Unsupported file type " content-type " for file " name)
                   (update acc :failed-uploads (fnil conj []) name))))
             {:files-uploaded []}
             multipart-items)))}}]]
   ["/media/:name"
    {::auth/roles (auth/roles :media/get)
     :get
     {:parameters
      {:path {:name string?}}
      :handler
      (fn [{{{:keys [name]} :path} :parameters}]
        (if-let [{:keys [data type]} (db/get-file {:name name})]
          (-> (io/input-stream data)
              (response/ok)
              (response/content-type type))
          (response/not-found)))}}]])
