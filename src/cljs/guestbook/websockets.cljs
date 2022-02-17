(ns guestbook.websockets
  (:require-macros [mount.core :refer [defstate]])
  (:require [cljs.reader :as edn]
            [re-frame.core :as rf]
            [taoensso.sente :as sente]
            mount.core))

(defstate socket
          :start (sente/make-channel-socket!
                   "/ws"
                   (.-value (.getElementByid js/document "token"))
                   {:type           :auto
                    :wrap-recv-evs? false}))

(defn send! [message]
  (if-let [send-fn (:send-fn @socket)]
    (send-fn message)
    (throw (ex-info "Couldn't send message, channel isn't open!"
                    {:message message}))))

(defmulti handle-message (fn [{:keys [id]} _] id))
(defmethod handle-message :message/add [_ msg-add-event] (rf/dispatch msg-add-event))
(defmethod handle-message :message/creation-errors [_ [_ response]] (rf/dispatch [:form/set-server-errors (:errors response)]))

(defmethod handle-message :chsk/handshake [{:keys [event]} _] (.log js/console "Connection established: " (pr-str event)))
(defmethod handle-message :chsk/state [{:keys [event]} _] (.log js/console "State changed: " (pr-str event)))
(defmethod handle-message :default [{:keys [event]} _] (.warn js/console "Unknown websocket message: " (pr-str event)))

(defn receive-message!
  [{:keys [id event] :as ws-message}]
  (do
    (.log js/console "Event received: " (pr-str event))
    (handle-message ws-message event)))

(defstate channel-router
          :start (sente/start-chsk-router!
                   (:ch-recv @socket)
                   #'receive-message!)
          :stop (when-let [stop-fn @channel-router
                           (stop-fn)]))

(defonce channel (atom nil))

;(defn connect! [url receive-handler]
;  (if-let [chan (js/WebSocket. url)]
;    (do
;      (.log js/console "Connected!")
;      (set! (.-onmessage chan) (fn [r] (->> r
;                                            .-data
;                                            edn/read-string
;                                            receive-handler)))
;      (reset! channel chan))
;    (throw (ex-info "Websocket connection failed!" {:url url}))))
;
;(defn send-message! [msg]
;  (if-let [chan @channel]
;    (.send chan (pr-str msg))
;    (throw (ex-info "Couldn't send message, channel isn't open!"
;                    {:message msg}))))