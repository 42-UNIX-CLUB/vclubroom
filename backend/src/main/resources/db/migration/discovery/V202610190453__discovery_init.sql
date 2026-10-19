-- discovery schema (docs/04-database.md §4.11): the public read model of clubs, kept up to date by domain events.
create table discovery.club_cards
(
    club_id          uuid primary key,
    slug             varchar(40) not null,
    name             varchar(60) not null,
    tagline          varchar(140),
    description      text,
    category         varchar(32),
    tags             text[]      not null default '{}',
    avatar_url       text,
    banner_url       text,
    status           varchar(20) not null,
    join_policy      varchar(16),
    current_era      integer     not null default 1,
    member_count     integer     not null default 0,
    founded_at       timestamptz,
    last_activity_at timestamptz,
    search_document  text,
    search           tsvector generated always as (to_tsvector('simple', coalesce(search_document, ''))) stored,
    updated_at       timestamptz not null
);
-- Not unique on purpose: slugs are unique in club.clubs already, and a second unique index would make two concurrent
-- upserts of the same club (ClubApproved + ClubProfileUpdated) fail, because ON CONFLICT only arbitrates on club_id.
create index ix_club_cards_slug on discovery.club_cards (slug);
create index ix_club_cards_search on discovery.club_cards using gin (search);
create index ix_club_cards_name_trgm on discovery.club_cards using gin (name gin_trgm_ops);
create index ix_club_cards_tags on discovery.club_cards using gin (tags);
create index ix_club_cards_status_activity on discovery.club_cards (status, last_activity_at desc);
create index ix_club_cards_category on discovery.club_cards (category, status);
