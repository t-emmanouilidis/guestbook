(ns guestbook.db.util
  (:require [clojure.string :as string]))

(defn tags-regex [tags-raw]
  (let [tags (filter #(re-matches #"[-\w]+" %) tags-raw)]
    (when (not-empty tags)
      (str "'.*(\\s|^)#("
           (string/join "|" tags)
           ")(\\s|$).*'"))))