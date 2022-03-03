-- :name save-message! :<! :1
-- :doc creates a new message using the name and message keys
INSERT INTO posts(author, name, message)
VALUES (:author, :name, :message)
RETURNING *;

-- :name get-messages :? :*
-- :doc returns all available messages
SELECT p.*, u.profile ->> 'avatar' as avatar
from posts p
         inner join users u on u.login = p.author;

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
SELECT p.*, u.profile ->> 'avatar' as avatar
from posts p
         inner join users u on p.author = u.login
WHERE p.author = :author;

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
SELECT p.*, u.profile ->> 'avatar' AS avatar
FROM posts p
         INNER JOIN users u ON u.login = p.author
WHERE p.id = :id;