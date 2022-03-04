CREATE OR REPLACE VIEW reply_count as
select p.id as id, count(c.id) as reply_count
from posts p
         left join posts c on c.parent = p.id
group by p.id;
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
                                   INNER JOIN posts_with_replies p
                                              ON r.id = p.parent)
      SELECT post_id                    AS id,
             jsonb_agg(msg)             AS messages,
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