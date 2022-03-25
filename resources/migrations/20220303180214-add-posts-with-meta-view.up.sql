CREATE OR REPLACE VIEW posts_with_meta AS
(
SELECT p.id,
       p.timestamp,
       p.message,
       p.name,
       p.author,
       u.profile ->> 'avatar' as avatar,
       count(b.user_id)       as boosts
FROM posts p
         LEFT JOIN users u ON p.author = u.login
         LEFT JOIN boosts b ON p.id = b.post_id
GROUP BY p.id, u.login
    );
--;;

-- we take the list of boosts together with the original post data
-- we do not keep a record for the original post i.e. without boost data, a record that is not a boost!
-- we keep the original post record (not-boost) only if we have no boost for post
CREATE OR REPLACE VIEW posts_and_boosts AS
(
SELECT p.id,
       p.timestamp,
       p.message,
       p.name,
       p.author,
       p.avatar,
       p.boosts,
       b.post_id IS NOT NULL                      as is_boost,
       coalesce(b.timestamp, p.timestamp)         AS posted_at,
       coalesce(b.user_id, p.author)              AS poster,
       coalesce(u.profile ->> 'avatar', p.avatar) AS poster_avatar,
       coalesce(b.poster, p.author)               AS source,
       coalesce(s.profile ->> 'avatar', p.avatar) AS source_avatar
FROM posts_with_meta p
         LEFT JOIN boosts b ON p.id = b.post_id
         LEFT JOIN users u ON u.login = b.user_id
         LEFT JOIN users s ON s.login = b.poster
    );
