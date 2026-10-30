-- The conversation on a ticket (E17-S03): the team's answers (tracker comments that start with /reply) and the
-- reporter's replies, which become tracker comments through the same outbox as tickets.
create table support.ticket_messages
(
    id                      uuid primary key,
    ticket_id               uuid        not null references support.tickets (id),
    author                  varchar(10) not null,                             -- REPORTER | TEAM
    body                    text        not null,
    tracker_comment_id      bigint unique,                                    -- set once the tracker has it
    tracker_attempts        integer     not null default 0,
    tracker_next_attempt_at timestamptz,
    tracker_last_error      text,
    created_at              timestamptz not null
);
create index ix_ticket_messages_ticket on support.ticket_messages (ticket_id, created_at);
create index ix_ticket_messages_unsent on support.ticket_messages (tracker_next_attempt_at, created_at)
    where tracker_comment_id is null;

-- How far TrackerSyncJob has read the tracker: one row, locked by the instance that reads.
create table support.tracker_cursor
(
    id         smallint primary key check (id = 1),
    read_until timestamptz not null,
    last_error text
);
insert into support.tracker_cursor (id, read_until) values (1, now());
