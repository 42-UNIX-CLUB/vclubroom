-- chat schema (docs/04-database.md §4.8): messages, kept forever (D-23), era-stamped like posts (BR-15).
create table chat.messages
(
    id           bigint generated always as identity primary key,
    channel_id   uuid        not null,
    space_id     uuid        not null,
    club_id      uuid        not null,
    author_id    uuid        not null,
    era          integer     not null,
    body         text        not null,
    media_ids    uuid[]      not null default '{}',
    client_nonce uuid        not null,
    created_at   timestamptz not null,
    edited_at    timestamptz,
    deleted_at   timestamptz,
    deleted_by   uuid
);
create index ix_messages_channel_id on chat.messages (channel_id, id desc);
create index ix_messages_channel_era_id on chat.messages (channel_id, era, id desc);
create unique index ux_messages_nonce on chat.messages (channel_id, author_id, client_nonce);   -- retries never duplicate
