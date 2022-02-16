(ns guestbook.routes.services
  (:require [guestbook.messages :as msg]
            [guestbook.middleware :as middleware]
            [ring.util.http-response :as response]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [guestbook.middleware.formats :as formats]))

(defn message-list [_] (response/ok (msg/message-list)))

(defn save-message! [{{params :body} :parameters}]
  (try
    (msg/save-message! params)
    (response/ok {:status :ok})
    (catch Exception e
      (let [{id     :guestbook/error-id
             errors :errors} (ex-data e)]
        (case id
          :validation
          (response/bad-request {:errors errors})
          (response/internal-server-error {:errors {:server-error ["Failed to save message!"]}}))))))

(defn service-routes []
  ["/api"
   {:middleware [parameters/parameters-middleware
                 muuntaja/format-negotiate-middleware
                 muuntaja/format-response-middleware
                 exception/exception-middleware
                 muuntaja/format-request-middleware
                 coercion/coerce-request-middleware
                 coercion/coerce-response-middleware
                 multipart/multipart-middleware]
    :muuntaja  formats/instance
    :coercion   spec-coercion/coercion
    :swagger    {:id ::api}}
   ["" {:no-doc true}
    ["/swagger.json" {:get (swagger/create-swagger-handler)}]
    ["/swagger-ui*" {:get (swagger-ui/create-swagger-ui-handler {:url "/api/swagger.json"})}]]
   ["/messages"
    {:get
     {:responses
      {200
       {:body
        {:messages
         [{:id        pos-int?
           :name      string?
           :message   string?
           :timestamp inst?}]}}}
      :handler message-list}}]
   ["/message"
    {:post
     {:parameters
      {:body
       {:name    string?
        :message string?}}
      :responses
      {200 {:body map?}
       400 {:body map?}
       500 {:errors map?}}
      :handler save-message!}}]])
