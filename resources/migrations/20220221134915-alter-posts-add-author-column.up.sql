ALTER TABLE posts
    ADD COLUMN author TEXT
        references users (login)
            ON DELETE SET NULL
            ON UPDATE CASCADE;