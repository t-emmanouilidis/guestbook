CREATE TABLE media
(
    name  text primary key,
    owner text references users (login) ON DELETE set null ON UPDATE CASCADE,
    type  text  not null,
    data  bytea not null
);