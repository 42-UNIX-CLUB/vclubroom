-- support schema (E17): tickets people send from the app. The tracker_* columns are an outbox: TrackerSyncJob creates
-- the tracker issue (Forgejo), retrying with backoff until it works. Older databases get the schema here (see shared).
create schema if not exists support;

create sequence support.ticket_number_seq;

create table support.tickets
(
    id                      uuid primary key,
    number                  bigint       not null unique,                     -- shown as T-<number>
    reporter_id             uuid         not null,
    type                    varchar(20)  not null,
    title                   varchar(120) not null,
    description             text         not null,
    page_url                varchar(500),
    user_agent              varchar(300),
    status                  varchar(30)  not null,
    tracker_issue           integer unique,                                   -- the issue number, once created
    tracker_attempts        integer      not null default 0,
    tracker_next_attempt_at timestamptz,
    tracker_last_error      text,
    created_at              timestamptz  not null,
    updated_at              timestamptz  not null
);
create index ix_tickets_reporter on support.tickets (reporter_id, created_at desc);
create index ix_tickets_tracker_pending on support.tickets (tracker_next_attempt_at, created_at) where tracker_issue is null;
