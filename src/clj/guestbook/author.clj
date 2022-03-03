(ns guestbook.author
  (:require [guestbook.db.core :as db]
            [clojure.tools.logging :as log]))

(defn get-author [login]
  (db/get-user* {:login login}))

(defn set-author-profile [login profile]
  (log/debug (str "Login: " login ", Profile: " profile))
  (db/set-profile-for-user* {:login   login
                             :profile profile}))