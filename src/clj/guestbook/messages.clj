(ns guestbook.messages
  (:require
    [guestbook.db.core :as db]
    [guestbook.validation :refer [validate-message]]
    [conman.core :as conman]
    [spec-tools.data-spec :as ds]))

(defn message-count []
  (db/count-posts-with {}))

(defn message-list
  [page-size page-start]
  {:messages (vec (db/get-posts-with {:limit  page-size
                                      :offset page-start}))})

(defn save-message! [{{:keys [display-name]} :profile
                      :keys                  [login]}
                     message]
  (if-let [errors (validate-message message)]
    (throw (ex-info "Message is invalid"
                    {:guestbook/error-id :validation
                     :errors             errors}))
    (conman/with-transaction
      [db/*db*]
      (let [post-id (:id (db/save-message!
                           (assoc message
                             :author login
                             :name (or display-name login)
                             :parent (:parent message))))]
        (first (db/get-timeline {:post     post-id
                          :user     login
                          :is_boost false}))))))

(defn count-message-by-author [author]
  (:count (db/count-posts-with {:author author})))

(defn messages-by-author
  [author page-size page-start]
  {:messages (vec (db/get-posts-with {:author author
                                      :limit  page-size
                                      :offset page-start}))})

(defn get-post [post-id]
  (first (db/get-posts-with {:id post-id})))

(defn get-replies [parent-id]
  (db/get-posts-with {:parent parent-id}))

(defn get-parents [id]
  (db/get-posts-with {:child id}))

(defn boost-post [login post-id poster]
  (conman/with-transaction [db/*db*]
                           (db/boost-post! db/*db* {:post   post-id
                                                    :poster poster
                                                    :user   login})
                           (db/get-timeline db/*db* {:post     post-id
                                                     :user     login
                                                     :is_boost true})))

(defn timeline-message-count []
  (db/count-timeline-messages))

(defn timeline
  [page-size page-start]
  {:messages (vec (db/get-timeline {:limit  page-size
                                    :offset page-start}))})

(defn poster-timeline-message-count [poster]
  (:count (db/count-timeline-messages {:poster poster})))

(defn timeline-for-poster
  [poster page-size page-start]
  {:messages (vec (db/get-timeline {:poster poster
                                    :limit  page-size
                                    :offset page-start}))})

(defn count-feed-messages [feed-map]
  (when-not (every? #(re-matches #"[-\w]+" %) (:tags feed-map))
    (throw (ex-info
             "Tags must only contain alphanumeric characters, dashes or underscores"
             feed-map)))
  (:count (db/count-feed-messages (merge {:follows []
                                          :tags    []}
                                         feed-map))))

(defn get-feed
  [feed-map page-size page-start]
  (when-not (every? #(re-matches #"[-\w]+" %) (:tags feed-map))
    (throw (ex-info
             "Tags must only contain alphanumeric characters, dashes or underscores"
             feed-map)))
  {:messages (db/get-feed (merge {:follows []
                                  :tags    []
                                  :limit   page-size
                                  :offset  page-start}
                                 feed-map))})

(defn count-feed-messages-for-tag [tag]
  (count-feed-messages {:tags [tag]}))

(defn get-feed-for-tag
  [tag page-size page-start]
  (get-feed {:tags [tag]} page-size page-start))

(def post?
  {:id                     pos-int?
   :name                   string?
   :message                string?
   :timestamp              inst?
   :author                 (ds/maybe string?)
   :avatar                 (ds/maybe string?)
   (ds/opt :boosts)        (ds/maybe int?)
   (ds/opt :poster_avatar) (ds/maybe string?)
   (ds/opt :root_id)       pos-int?
   (ds/opt :is_boost)      boolean?
   (ds/opt :source_avatar) (ds/maybe string?)
   (ds/opt :source)        (ds/maybe string?)
   (ds/opt :posted_at)     inst?
   (ds/opt :poster)        (ds/maybe string?)
   (ds/opt :is_reply)      boolean?
   (ds/opt :reply_count)   int?})
