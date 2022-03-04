-- :name save-message! :<! :1
-- :doc creates a new message using the name and message keys
INSERT INTO posts(author, name, message, parent)
VALUES (:author, :name, :message, :parent)
RETURNING *;

-- :name get-messages :? :*
-- :doc returns all available messages
SELECT *
FROM posts_with_meta;

-- :name create-user!* :! :n
-- :doc creates a new user with the provided login and hashed password
INSERT INTO users(login, password)
VALUES (:login, :password);

-- :name get-user-for-auth* :? :1
-- :doc selects a user for authentication
SELECT *
FROM users
WHERE login = :login;

-- :name get-messages-by-author :? :*
-- :doc returns all messages by given author
SELECT *
from posts_with_meta
WHERE author = :author;

-- :name set-profile-for-user* :<! :1
-- :doc sets a profile map for the specified user
UPDATE users
SET profile = :profile
where login = :login
RETURNING *;

-- :name get-user* :? :1
-- :doc gets a user's publicly available information
SELECT login, created_at, profile
FROM users
WHERE login = :login;

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
select *
from media
where media.name = :name;

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

-- :name get-post :? :1
-- :doc returns a post
SELECT *
FROM posts_with_meta
WHERE id = :id;

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
SELECT user_id as user
FROM boosts
WHERE post_id = :post;

-- :name get-reboosts :? :*
-- Gets all boosts descended from a given boost
WITH RECURSIVE reboosts AS (
    WITH post_boosts AS
             (SELECT user_id, poster
              FROM boosts
              WHERE post_id = :post)
    SELECT user_id, poster
    FROM boosts
    WHERE post_id = :post
      AND user_id = :user
    UNION
    SELECT b.user_id, b.poster
    FROM post_boosts b
             INNER JOIN reboosts r ON r.user_id = b.poster)
SELECT user_id AS user, poster as source
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
             INNER JOIN reboosts r ON r.poster = b.user_id
)
SELECT user_id AS user, poster as source
FROM reboosts;

-- :name get-timeline :? :*
-- :doc Gets the latest post or boost for each post
SELECT t.*
FROM (
         SELECT DISTINCT ON (p.id) *
         FROM posts_and_boosts p
         ORDER BY p.id, p.posted_at DESC
     ) as t
ORDER BY t.posted_at;

-- :name get-timeline-for-poster :? :*
-- :doc Gets the latest post or boost for each post for a specific poster
SELECT t.*
FROM (
         SELECT DISTINCT ON (p.id) *
         FROM posts_and_boosts p
         WHERE p.poster = :poster
         ORDER BY p.id, p.posted_at DESC
     ) as t
ORDER BY t.posted_at;

-- :name get-timeline-post :? :1
-- :doc Gets the boosted post for updating timelines
SELECT *
FROM posts_and_boosts p
WHERE p.is_boost = :is_boost
  AND poster = :user
  AND id = :post
ORDER BY posted_at
limit 1;
