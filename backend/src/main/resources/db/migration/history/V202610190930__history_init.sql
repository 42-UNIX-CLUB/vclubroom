-- history schema (docs/04-database.md §4.12): the club timeline, a read model built from domain events.
create table history.timeline_entries
(
    id          uuid primary key,
    club_id     uuid         not null,
    era         integer      not null,
    type        varchar(32)  not null,
    actor_id    uuid,
    subject_id  uuid,
    data        jsonb        not null default '{}',
    occurred_at timestamptz  not null,
    dedup_key   varchar(200) not null
);
create unique index ux_timeline_dedup on history.timeline_entries (club_id, dedup_key);   -- redelivery is a no-op
create index ix_timeline_club_occurred on history.timeline_entries (club_id, occurred_at desc, id desc);
