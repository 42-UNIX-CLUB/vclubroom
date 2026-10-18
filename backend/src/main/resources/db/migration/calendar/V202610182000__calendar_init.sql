-- calendar schema (docs/04-database.md §4.9): club events, RSVPs, reminder outbox and iCal feed tokens.
create table calendar.events
(
    id             uuid primary key,
    club_id        uuid         not null,
    space_id       uuid         not null,
    era            integer      not null,
    created_by     uuid         not null,
    title          varchar(140) not null,
    description    text,
    location       varchar(200),
    online_url     text,
    starts_at      timestamptz  not null,
    ends_at        timestamptz  not null,
    status         varchar(16)  not null check (status in ('SCHEDULED', 'CANCELLED')),
    cover_media_id uuid,
    cancel_reason  text,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    version        bigint       not null default 0,
    constraint ck_events_times check (ends_at > starts_at)                                      -- BR-21
);
create index ix_events_starts on calendar.events (starts_at);
create index ix_events_club_starts on calendar.events (club_id, starts_at);

create table calendar.rsvps
(
    event_id   uuid        not null references calendar.events (id),
    user_id    uuid        not null,
    status     varchar(16) not null check (status in ('GOING', 'INTERESTED', 'NOT_GOING')),
    updated_at timestamptz not null,
    primary key (event_id, user_id)
);
create index ix_rsvps_user on calendar.rsvps (user_id);

create table calendar.event_reminders
(
    id        uuid primary key,
    event_id  uuid        not null references calendar.events (id),
    kind      varchar(16) not null check (kind in ('DAY_BEFORE', 'HOUR_BEFORE')),
    remind_at timestamptz not null,
    sent_at   timestamptz
);
create index ix_reminders_due on calendar.event_reminders (remind_at) where sent_at is null;

create table calendar.feed_tokens
(
    user_id    uuid primary key,
    token_hash char(64)    not null unique,                                                     -- hex SHA-256
    created_at timestamptz not null
);
