(ns guestbook.auth.ring
  (:require
    [clojure.tools.logging :as log]
    [guestbook.auth :as auth]
    [reitit.ring :as ring]
    [ring.util.http-response :as response]))

(defn- authorized? [roles req]
  (if (seq roles)
    (->> req
         :session
         :identity
         auth/identity->roles
         (some roles)
         boolean)
    (do
      (log/error "roles: " roles " is empty for route: " (:uri req))
      false)))

(defn- get-roles-from-match [req]
  (-> req
      ring/get-match
      (get-in,,, [:data ::auth/roles] #{})))

(defn- wrap-authorized [handler unauthorized-handler]
  (fn [req]
    (if (authorized? (get-roles-from-match req) req)
      (handler req)
      (unauthorized-handler req))))

(defn mw [handler]
  (wrap-authorized
    handler
    (fn handle-unauthorized [req]
      (let [route-roles (get-roles-from-match req)]
        (log/debug "Roles for route: " (:uri req) route-roles)
        (log/debug "User is unauthorized!"
                   (-> req
                       :session
                       :identity
                       :roles))
        (response/forbidden
          {:message (str "User must have one of the following roles: " route-roles)})))))