-- club schema (docs/04-database.md §4.2): clubs, their eras, and revival requests (used from E14-S02).
create table club.clubs
(
    id              uuid primary key,
    slug            varchar(40) not null unique,                                          -- BR-01
    name            varchar(60) not null,
    tagline         varchar(140),
    description     text,
    category        varchar(32) not null,
    tags            text[]      not null default '{}',
    avatar_media_id uuid,
    banner_media_id uuid,
    status          varchar(20) not null check (status in ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'ARCHIVED')),
    join_policy     varchar(16) not null default 'APPROVAL' check (join_policy in ('OPEN', 'APPROVAL')),
    founder_id      uuid        not null,
    current_era     integer     not null default 0,
    review_note     text,
    reviewed_by     uuid,
    reviewed_at     timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint      not null default 0
);
create index ix_clubs_status_created on club.clubs (status, created_at);
create index ix_clubs_founder_status on club.clubs (founder_id, status);

create table club.club_eras
(
    club_id      uuid        not null references club.clubs (id),
    era_number   integer     not null,
    started_at   timestamptz not null,
    start_reason varchar(16) not null check (start_reason in ('FOUNDED', 'REVIVED')),
    ended_at     timestamptz,
    end_reason   text,
    primary key (club_id, era_number)
);

create table club.revival_requests
(
    id           uuid primary key,
    club_id      uuid        not null references club.clubs (id),
    requester_id uuid        not null,
    motivation   text        not null,
    status       varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by  uuid,
    review_note  text,
    reviewed_at  timestamptz,
    created_at   timestamptz not null,
    version      bigint      not null default 0
);
create unique index ux_revival_pending_per_requester
    on club.revival_requests (club_id, requester_id) where status = 'PENDING';
create index ix_revival_status_created on club.revival_requests (status, created_at);