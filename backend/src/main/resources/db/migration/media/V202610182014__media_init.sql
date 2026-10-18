-- media schema (docs/04-database.md §4.6): one row per uploaded file; bytes live in SeaweedFS, never in PostgreSQL.
create table media.media_objects
(
    id                uuid primary key,
    owner_id          uuid         not null,
    purpose           varchar(24)  not null check (purpose in
                                                   ('POST_ATTACHMENT', 'CHAT_ATTACHMENT', 'CLUB_AVATAR', 'CLUB_BANNER', 'EVENT_COVER')),
    kind              varchar(8)   not null check (kind in ('IMAGE', 'VIDEO', 'AUDIO')),
    content_type      varchar(100) not null,
    size_bytes        bigint       not null check (size_bytes > 0),
    original_filename varchar(255),
    bucket            varchar(63)  not null,
    storage_key       text         not null unique,
    status            varchar(16)  not null check (status in ('PENDING', 'READY', 'ATTACHED', 'DELETED')),
    created_at        timestamptz  not null,
    attached_at       timestamptz,
    version           bigint       not null default 0
);
create index ix_media_orphans on media.media_objects (created_at) where status in ('PENDING', 'READY');
