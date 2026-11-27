-- Development work a ticket is linked to (E18-S03). The team makes the support issue depend on development issues;
-- TrackerSyncJob keeps a copy of each one's title, progress and release, which the reporter sees on the ticket.
create table support.ticket_links
(
    ticket_id  uuid         not null references support.tickets (id),
    issue      integer      not null,                                  -- in the development repository
    title      varchar(255) not null,
    state      varchar(20)  not null,                                  -- PLANNED | IN_PROGRESS | DONE
    milestone  varchar(100),                                           -- its release, e.g. v1.2.0
    released   boolean      not null default false,                    -- that release is out (milestone closed)
    updated_at timestamptz  not null,
    primary key (ticket_id, issue)
);
create index ix_ticket_links_issue on support.ticket_links (issue);
