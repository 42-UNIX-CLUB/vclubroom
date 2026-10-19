-- space schema (docs/04-database.md §4.5): spaces with a read rule and a write rule, and their channels.
create table space.spaces
(
    id              uuid primary key,
    club_id         uuid        not null,
    name            varchar(60) not null,
    slug            varchar(60) not null,
    description     text,
    position        integer     not null default 0,
    read_level      varchar(16) not null check (read_level in ('PUBLIC', 'CLUB_MEMBERS', 'GROUPS')),
    read_group_ids  uuid[]      not null default '{}',
    write_level     varchar(16) not null check (write_level in ('PUBLIC', 'CLUB_MEMBERS', 'GROUPS')),
    write_group_ids uuid[]      not null default '{}',
    created_at      timestamptz not null,
    archived_at     timestamptz,
    version         bigint      not null default 0
);
create unique index ux_spaces_club_slug on space.spaces (club_id, slug);
create index ix_spaces_club_position on space.spaces (club_id, position);

create table space.channels
(
    id          uuid primary key,
    space_id    uuid        not null references space.spaces (id),
    type        varchar(8)  not null check (type in ('FORUM', 'CHAT')),
    name        varchar(60) not null,
    slug        varchar(60) not null,
    description text,
    position    integer     not null default 0,
    created_at  timestamptz not null,
    archived_at timestamptz
);
create unique index ux_channels_space_slug on space.channels (space_id, slug);
