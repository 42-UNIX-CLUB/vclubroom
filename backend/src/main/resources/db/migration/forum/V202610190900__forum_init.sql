-- forum schema (docs/04-database.md §4.7): posts (a post is a thread), their media, comments, reactions.
create table forum.posts
(
    id               uuid primary key,
    channel_id       uuid        not null,
    space_id         uuid        not null,
    club_id          uuid        not null,
    author_id        uuid        not null,
    era              integer     not null,
    title            varchar(140),
    body             text        not null,
    pinned           boolean     not null default false,
    status           varchar(16) not null check (status in ('PUBLISHED', 'DELETED')),
    comment_count    integer     not null default 0,
    created_at       timestamptz not null,
    last_activity_at timestamptz not null,
    edited_at        timestamptz,
    deleted_at       timestamptz,
    deleted_by       uuid,
    version          bigint      not null default 0
);
create index ix_posts_channel_created on forum.posts (channel_id, created_at desc, id desc);
create index ix_posts_channel_era_created on forum.posts (channel_id, era, created_at desc, id desc);
create index ix_posts_channel_pinned on forum.posts (channel_id) where pinned;
create index ix_posts_club_created on forum.posts (club_id, created_at desc);

create table forum.post_media
(
    post_id  uuid    not null references forum.posts (id),
    position integer not null,
    media_id uuid    not null,
    primary key (post_id, position)
);

create table forum.comments
(
    id         uuid primary key,
    post_id    uuid        not null references forum.posts (id),
    parent_id  uuid references forum.comments (id),
    author_id  uuid        not null,
    body       text        not null,
    created_at timestamptz not null,
    edited_at  timestamptz,
    deleted_at timestamptz,
    deleted_by uuid
);
create index ix_comments_post_created on forum.comments (post_id, created_at, id);

create table forum.reactions
(
    id          uuid primary key,
    target_type varchar(8)  not null check (target_type in ('POST', 'COMMENT')),
    target_id   uuid        not null,
    user_id     uuid        not null,
    emoji       varchar(32) not null,
    created_at  timestamptz not null
);
create unique index ux_reactions_unique on forum.reactions (target_type, target_id, user_id, emoji); -- BR-18
