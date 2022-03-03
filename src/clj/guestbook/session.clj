(ns guestbook.session
  (:require
    [ring-ttl-session.core :refer [ttl-memory-store]]
    [clojure.tools.logging :as log]))

(defonce store (ttl-memory-store (* 60 30)))

(defn ring-req->session-key [req]
  (get-in req [:cookies "ring-session" :value]))

(defn read-session [req]
  (let [session-key (ring-req->session-key req)
        session (.read-session store session-key)]
    (log/debug (str "Session key = " session-key))
    (log/debug (str "Session = " session))
    session))

(defn write-session [req v]
  (log/debug (str "Writing session to store: " v))
  (.write-session store (ring-req->session-key req) v))
