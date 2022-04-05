(ns guestbook.db.util
  (:require [clojure.string :as string]))

(defn tags-regex [tags-raw]
  (let [tags (filter #(re-matches #"[-\w]+" %) tags-raw)]
    (when (not-empty tags)
      (str "'.*(\\s|^)#("
           (string/join "|" tags)
           ")(\\s|$).*'"))))

(defn tag-regex [tag]
  "Checks that tag only contains alphanumeric and/or dashes and returns the a regex that matches the tag"
  (let [regex (tags-regex [tag])]
    (if-not regex
      (throw (ex-info "Tag must only contain alphanumeric characters!"
                      {:tag tag}))
      regex)))