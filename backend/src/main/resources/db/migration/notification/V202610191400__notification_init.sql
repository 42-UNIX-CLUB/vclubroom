-- notification schema (docs/04-database.md §4.10): notifications (with the email outbox columns E12-S03 uses) and preferences.
create table notification.notifications
(
    id                    uuid primary key,
    recipient_id          uuid         not null,
    type                  varchar(40)  not null,
    title                 varchar(200) not null,
    body                  text,
    link                  text,
    data                  jsonb        not null default '{}',
    dedup_key             varchar(200) not null,
    in_app                boolean      not null,
    email_requested       boolean      not null,
    email_sent_at         timestamptz,
    email_attempts        integer      not null default 0,
    email_next_attempt_at timestamptz,                                                 -- backoff (R-27)
    email_last_error      text,
    read_at               timestamptz,
    created_at            timestamptz  not null
);
create unique index ux_notifications_dedup on notification.notifications (recipient_id, dedup_key);   -- redelivery is a no-op
create index ix_notifications_inbox on notification.notifications (recipient_id, created_at desc, id desc) where in_app;
create index ix_notifications_unread on notification.notifications (recipient_id) where in_app and read_at is null;
create index ix_notifications_email_pending on notification.notifications (email_next_attempt_at, created_at)
    where email_requested and email_sent_at is null;

create table notification.preferences
(
    user_id uuid        not null,
    type    varchar(40) not null,
    in_app  boolean     not null,
    email   boolean     not null,
    primary key (user_id, type)
);
