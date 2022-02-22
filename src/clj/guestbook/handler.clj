(ns guestbook.handler
  (:require
    [guestbook.middleware :as middleware]
    [guestbook.layout :refer [error-page]]
    [guestbook.routes.services :refer [service-routes]]
    [reitit.ring :as ring]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.webjars :refer [wrap-webjars]]
    [guestbook.env :refer [defaults]]
    [mount.core :as mount]
    [reitit.ring.middleware.dev :as dev]
    [guestbook.routes.websockets :refer [websocket-routes]]
    [guestbook.routes.app :refer [app-routes]]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(mount/defstate routes
  :start
  (ring/ring-handler
    (ring/router
      [(app-routes)
       (service-routes)
       (websocket-routes)]
      ;{:reitit.middleware/transform dev/print-request-diffs}
      )
    (ring/routes
      (ring/create-resource-handler
        {:path "/"})
      (wrap-content-type
        (wrap-webjars (constantly nil)))
      (ring/create-default-handler
        {:not-found
         (constantly (error-page {:status 404, :title "404 - Page not found"}))
         :method-not-allowed
         (constantly (error-page {:status 405, :title "405 - Not allowed"}))
         :not-acceptable
         (constantly (error-page {:status 406, :title "406 - Not acceptable"}))}))))

(defn app []
  (middleware/wrap-base #'routes))
