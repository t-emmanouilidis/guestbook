CREATE OR REPLACE VIEW reply_count AS
SELECT p.id AS id, count(c.id) AS reply_count
FROM posts p
         LEFT JOIN posts c ON c.parent = p.id
GROUP BY p.id;
--;;

CREATE OR REPLACE VIEW posts_with_replies as
select *
from (WITH RECURSIVE posts_with_replies AS
                         (WITH replies AS
                                   (SELECT p.parent      as parent,
                                           p.id          as id,
                                           to_jsonb(pwm) as msg,
                                           p.id          as post_id
                                    from posts p
                                             left join posts_with_meta pwm on p.id = pwm.id)
                          SELECT parent, id, msg, post_id
                          FROM replies
                          UNION
                          SELECT r.parent, r.id, r.msg, p.post_id
                          FROM replies r
                          -- move up the tree e.g. the parent of the initial non-recursive term be equal to the id of the next results
                          -- we keep the leaf post_id (last reply_id) so that we can connect all the messages in the path with the same id and aggregate them
                                   INNER JOIN posts_with_replies p
                                              ON r.id = p.parent)
      SELECT post_id                    AS id,
             jsonb_agg(msg)             AS messages,
             -- last id in our aggregation will be the root id
             (array_agg(id))[count(id)] AS root_id,
             count(id) <> 1             AS is_reply
      FROM posts_with_replies
      GROUP BY post_id) as pwr;

--;;

ALTER VIEW posts_and_boosts RENAME TO posts_and_boosts_no_replies;
--;;

CREATE OR REPLACE VIEW posts_and_boosts AS
select *
from posts_with_replies
         inner join reply_count using (id)
         inner join posts_and_boosts_no_replies using (id);