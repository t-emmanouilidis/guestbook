(ns guestbook.routes.websockets
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [guestbook.messages :as msg]
            [guestbook.middleware :as middleware]
            [guestbook.session :as session]
            [guestbook.auth :as auth]
            [guestbook.auth.ws :refer [authorized?]]))

(defstate socket
          :start (sente/make-channel-socket!
                   (get-sch-adapter)
                   {:user-id-fn (fn [ring-req]
                                  (get-in ring-req [:params :client-id]))}))

(defn send! [uid message]
  ((:send-fn socket) uid message))

(defmulti handle-message (fn [{:keys [id]}] id))

(defmethod handle-message :default [{:keys [id]}]
  (log/debug "Received unrecognized websocket event type: " id)
  {:error (str "Unrecognized websocket event type: " (pr-str id))
   :id    id})

(defmethod handle-message :message/create!
  [{:keys [?data session]}]
  (let [response (try
                   (-> (:identity session)
                       (msg/save-message!,,, ?data)
                       (assoc,,, :timestamp (java.util.Date.)))
                   (catch Exception e
                     (let [{id     :guestbook/error-id
                            errors :errors} (ex-data e)]
                       (case id
                         :validation
                         {:errors errors}
                         {:errors {:server-error ["Failed to save message!"]}}))))]
    (if (:errors response)
      (do
        (log/debug "Failed to save message: " ?data)
        response)
      (do
        (doseq [uid (:any @(:connected-uids socket))]
          (send! uid [:messages/add response]))
        {:success true}))))

(defn receive-message! [{:keys [id ?reply-fn ring-req] :as message}]
  (case id
    :chsk/bad-package (log/debug "Bad package:\n" message)
    :chsk/bad-event (log/debug "Bad event:\n" message)
    :chsk/uidport-open (log/trace (:event message))
    :chsk/uidport-close (log/trace (:event message))
    :chsk/ws-ping nil
    ;; ELSE
    (let [reply-fn (or ?reply-fn (fn [_]))
          session (session/read-session ring-req)
          message (-> message
                      (assoc :session session))]
      (log/debug (str "ring-req: " ring-req))
      (log/debug (str "Session: " session))
      (log/debug "Got a message with id: " id)
      (if (authorized? auth/roles message)
        (when-some [response (handle-message message)]
          (reply-fn response))
        (do
          (log/info "Unauthorized message: " id)
          (reply-fn {:message "You are not authorized to perform this action!"
                     :errors  {:unauthorized true}}))))))

(defstate channel-router
          :start (sente/start-chsk-router!
                   (:ch-recv socket)
                   #'receive-message!)
          :stop (when-let [stop-fn channel-router]
                  (stop-fn)))

(defn websocket-routes []
  ["/ws"
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]
    :get        (:ajax-get-or-ws-handshake-fn socket)
    :post       (:ajax-post-fn socket)}])