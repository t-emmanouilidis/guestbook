(ns guestbook.routes.websockets
  (:require [clojure.tools.logging :as log]
            [org.httpkit.server :as http-kit]
            [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [guestbook.messages :as msg]
            [guestbook.middleware :as middleware])
  (:import (java.util Date)))

(defstate socket
          :start (sente/make-channel-socket!
                   (get-sch-adapter)
                   {:user-id-fn (fn [ring-req]
                                  (get-in ring-req [:params :client-id]))}))

(defn send! [uid message]
  (println "Sending message: " message)
  ((:send-fn socket) uid message))

(defmulti handle-message (fn [{:keys [id]}] id))
(defmethod handle-message :default [{:keys [id]}]
  (log/debug "Received unrecognized websocket event type: " id))
(defmethod handle-message :message/create!
  [{:keys [?data uid] :as message}]
  (let [response (try
                   (msg/save-message! ?data)
                   (assoc ?data :timestamp (Date.))
                   (catch Exception e
                     (let [{id     :guestbook/error-id
                            errors :errors} (ex-data e)]
                       (case id
                         :validation
                         {:errors errors}
                         {:errors {:server-error ["Failed to save message!"]}}))))]
    (if (:errors response)
      (send! uid [:message/creation-errors response])
      (doseq [uid (:any @(:connected-uids socket))]
        (send! uid [:messages/add response])))))

(defn receive-message! [{:keys [id] :as message}]
  (log/debug "Got message with id: " id)
  (handle-message message))

(defstate channel-router
          :start (sente/start-chsk-router!
                   (:ch-recv socket)
                   #'receive-message!)
          :stop (when-let [stop-fn channel-router]
                  (stop-fn)))

(defonce channels (atom #{}))

;(defn connect! [channel]
;  (log/info "Channel opened")
;  (swap! channels conj channel))

;(defn disconnect! [channel status]
;  (log/info "Channel closed: " status)
;  (swap! channels disj channel))

;(defn handle-message! [channel ws-message]
;  (let [message (edn/read-string ws-message)
;        response (try
;                   (msg/save-message! message)
;                   (assoc message :timestamp (Date.))
;                   (catch Exception e
;                     (let [{id     :guestbook/error-id
;                            errors :errors} (ex-data e)]
;                       (case id
;                         :validation
;                         {:errors errors}
;                         {:errors {:server-error ["Failed to save message!"]}}))))]
;    (if (:errors response)
;      (http-kit/send! channel (pr-str response))
;      (doseq [channel @channels]
;        (http-kit/send! channel (pr-str response))))))

;(defn handler [request]
;  (http-kit/with-channel request channel
;                         (connect! channel)
;                         (http-kit/on-close channel (partial disconnect! channel))
;                         (http-kit/on-receive channel (partial handle-message! channel))))

(defn websocket-routes []
  ["/ws"
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]
    :get        (:ajax-get-or-ws-handshake-fn socket)
    :post       (:ajax-post-fn socket)}])