-- :name save-message! :! :n
-- :doc creates a new message using the name and message keys
INSERT INTO posts
    (name, message)
VALUES (:name, :message);

-- :name get-messages :? :*
-- :doc returns all available messages
SELECT *
from posts;