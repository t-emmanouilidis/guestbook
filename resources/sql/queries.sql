-- :name count-posts-with :? :1
-- :doc returns the number of posts that match the criteria
SELECT count(pwm.id)
FROM posts_with_meta pwm
         INNER JOIN (select id, parent from posts) as p using (id)
         INNER JOIN reply_count AS r USING (id)
WHERE
--~ (if (contains? params :author) " pwm.author = :author " " TRUE ")
--~ (if (contains? params :id) " AND pwm.id = :id " " AND TRUE ")
--~ (if (contains? params :parent) " AND pwm.id IN (SELECT p2.id FROM posts p2 WHERE p2.parent = :parent) " " AND TRUE ")
/*~ (if (contains? params :child) */
  AND pwm.id IN (WITH RECURSIVE parents AS (
    SELECT p2.id, p2.parent
    FROM posts AS p2
    WHERE p2.id = :child
    UNION
    SELECT p3.id, p3.parent
    FROM posts AS p3
    INNER JOIN parents pp ON p3.id = pp.parent)
    SELECT p4.id
    FROM parents AS p4)
/*~*/
  AND TRUE
/*~ ) ~*/

-- :name get-posts-with :? :*
-- :doc returns a specific post
SELECT pwm.id,
       pwm.name,
       pwm.message,
       pwm.author,
       pwm.timestamp,
       pwm.avatar,
       pwm.boosts,
       p.parent,
       r.reply_count
FROM posts_with_meta pwm
         INNER JOIN (select id, parent from posts) as p using (id)
         INNER JOIN reply_count AS r USING (id)
WHERE
--~ (if (contains? params :author) " pwm.author = :author " " TRUE ")
--~ (if (contains? params :id) " AND pwm.id = :id " " AND TRUE ")
--~ (if (contains? params :parent) " AND pwm.id IN (SELECT p2.id FROM posts p2 WHERE p2.parent = :parent) " " AND TRUE ")
/*~ (if (contains? params :child) */
  AND pwm.id IN (WITH RECURSIVE parents AS (
    SELECT p2.id, p2.parent
    FROM posts AS p2
    WHERE p2.id = :child
    UNION
    SELECT p3.id, p3.parent
    FROM posts AS p3
    INNER JOIN parents pp ON p3.id = pp.parent)
    SELECT p4.id
    FROM parents AS p4)
/*~*/
  AND TRUE
/*~ ) ~*/
ORDER BY pwm.timestamp DESC
--~ (when (and (contains? params :limit) (contains? params :offset)) " LIMIT :limit OFFSET :offset ")

-- :name count-timeline-messages :? :1
-- :doc return the total number of messages in timeline
SELECT COUNT(t.id)
FROM (SELECT DISTINCT ON (p.id) *
      FROM posts_and_boosts p
      WHERE
--~ (if (contains? params :poster) " p.poster = :poster " " TRUE ")
--~ (if (and (contains? params :is_boost) (contains? params :user) (contains? params :post)) " AND p.is_boost = :is_boost AND p.poster = :user AND p.id = :post " " AND TRUE ")
     ) AS t

-- :name get-timeline :? :*
-- :doc Gets the latest post or boost for each post
SELECT t.root_id,
       t.id,
       t.name,
       t.message,
       t.author,
       t.timestamp,
       t.avatar,
       t.is_reply,
       t.reply_count,
       t.is_boost,
       t.boosts,
       t.posted_at,
       t.source,
       t.source_avatar,
       t.poster,
       t.poster_avatar,
       t.messages
FROM (SELECT DISTINCT ON (p.id) *
      FROM posts_and_boosts p
      WHERE
--~ (if (contains? params :poster) " p.poster = :poster " " TRUE ")
--~ (if (and (contains? params :is_boost) (contains? params :user) (contains? params :post)) " AND p.is_boost = :is_boost AND p.poster = :user AND p.id = :post " " AND TRUE ")
      ORDER BY p.id, p.posted_at DESC
--~ (when (contains? params :post) " LIMIT 1 ")
     ) AS t
ORDER BY t.posted_at DESC
--~ (when (and (contains? params :limit) (contains? params :offset)) " LIMIT :limit OFFSET :offset ")

-- :name count-feed-messages :? :1
-- :require [guestbook.db.util :refer [tags-regex]]
-- :doc given a vector of follows and a vector of tags, count the messages that match
SELECT COUNT(t.id)
FROM (
         SELECT DISTINCT ON (p.id) *
         FROM posts_and_boosts p
         WHERE
/*~ (if (seq (:follows params)) */
                 p.poster IN (:v*:follows)
/*~*/
             false
/*~ ) ~*/
OR
/*~ (if (seq (:tags params)) */
p.message ~*
/*~*/
false
/*~ ) ~*/
--~ (when (seq (:tags params)) (tags-regex (:tags params)))
ORDER BY p.id, posted_at DESC) AS t


-- :name get-feed :? :*
-- :require [guestbook.db.util :refer [tags-regex]]
-- :doc given a vector of follows and a vector of tags, return a feed
SELECT t.root_id,
       t.id,
       t.name,
       t.message,
       t.author,
       t.timestamp,
       t.avatar,
       t.is_reply,
       t.reply_count,
       t.is_boost,
       t.boosts,
       t.posted_at,
       t.source,
       t.source_avatar,
       t.poster,
       t.poster_avatar,
       t.messages
FROM (
         SELECT DISTINCT ON (p.id) *
         FROM posts_and_boosts p
         WHERE
/*~ (if (seq (:follows params)) */
             p.poster IN (:v*:follows)
/*~*/
             false
/*~ ) ~*/
OR
/*~ (if (seq (:tags params)) */
p.message ~*
/*~*/
false
/*~ ) ~*/
--~ (when (seq (:tags params)) (tags-regex (:tags params)))
         ORDER BY p.id, posted_at DESC) AS t
ORDER BY t.posted_at DESC
--~ (when (and (contains? params :limit) (contains? params :offset)) " LIMIT :limit OFFSET :offset ")

-- :name save-message! :<! :1
-- :doc creates a new message using the name and message keys
INSERT INTO posts(author, name, message, parent)
VALUES (:author, :name, :message, :parent)
RETURNING *;

-- :name create-user!* :! :n
-- :doc creates a new user with the provided login and hashed password
INSERT INTO users(login, password)
VALUES (:login, :password);

-- :name get-user-for-auth* :? :1
-- :doc selects a user for authentication
SELECT u.login, u.password, u.created_at, u.profile
FROM users AS u
WHERE u.login = :login;

-- :name set-profile-for-user* :<! :1
-- :doc sets a profile map for the specified user
UPDATE users
SET profile = :profile
WHERE login = :login
RETURNING *;

-- :name get-user* :? :1
-- :doc gets a user's publicly available information
SELECT u.login, u.created_at, u.profile
FROM users AS u
WHERE u.login = :login;

-- :name save-file! :! :n
-- :doc saves a file to the database
INSERT INTO media(name, type, owner, data)
VALUES (:name, :type, :owner, :data)
ON CONFLICT (name) DO UPDATE
    SET type = :type,
        data = :data
WHERE media.owner = :owner;

-- :name get-file :? :1
-- :doc gets a file from the database
SELECT m.name, m.owner, m.type, m.data
FROM media AS m
WHERE m.name = :name;

-- :name set-password-for-user!* :! :n
-- :doc updates the password for a user
UPDATE users
SET password = :password
WHERE login = :login;

-- :name delete-user!* :! :n
-- :doc deletes a user from db
DELETE
from users
where login = :login;

-- :name boost-post! :! :n
-- :doc Boosts a post, or moves a boost to the top of the user's timeline
INSERT INTO boosts(user_id, post_id, poster)
VALUES (:user, :post, nullif(:poster, :user))
ON CONFLICT (user_id, post_id) DO UPDATE
    SET timestamp = now()
WHERE boosts.user_id = :user
  AND boosts.post_id = :post;

-- :name boosters-of-post :? :*
-- :doc Get all boosters for a post
SELECT user_id AS "user"
FROM boosts
WHERE post_id = :post;

-- :name get-reboosts :? :*
-- Gets all boosts descended from a given boost
WITH RECURSIVE reboosts AS (
    -- ola ta boosts gia ayto to post
    WITH post_boosts AS
             (SELECT user_id, poster
              FROM boosts
              WHERE post_id = :post)
    SELECT user_id, poster
    FROM boosts
    WHERE user_id = :user
    UNION
    SELECT b.user_id, b.poster
    FROM post_boosts b
             -- we start from the given user_id and we recursively search the boosts table for this post
             -- to get the next record where the initial user_id is the poster of the next row
             -- i.e. we move down the tree
             INNER JOIN reboosts r ON r.user_id = b.poster)
SELECT user_id AS "user", poster as source
FROM reboosts;

-- :name get-boost-chain :? :*
-- :doc Gets all boosts above the original boost
WITH RECURSIVE reboosts AS (
    WITH post_boosts AS (
        SELECT user_id, poster
        FROM boosts
        WHERE post_id = :post
    )
    SELECT user_id, poster
    FROM post_boosts
    WHERE user_id = :user
    UNION
    SELECT b.user_id, b.poster
    FROM post_boosts b
             -- the result of the non-recursive term (:user_id and :post from boosts) is joined with the post_boosts
             -- where the user_id of the post_boosts need to be the same with the poster of the temporary table
             -- i.e. we move up the tree.
             INNER JOIN reboosts r ON r.poster = b.user_id
)
SELECT user_id AS "user", poster as source
FROM reboosts;
