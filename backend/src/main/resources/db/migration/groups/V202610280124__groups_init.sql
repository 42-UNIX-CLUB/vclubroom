-- groups schema (docs/04-database.md §4.4): club groups now, campus groups (E13-S04, E15-S03) in the same tables.
create table groups.groups
(
    id          uuid primary key,
    scope       varchar(8)  not null check (scope in ('CLUB', 'CAMPUS')),
    club_id     uuid,
    name        varchar(80) not null,
    slug        varchar(80) not null,
    description text,
    source      varchar(8)  not null check (source in ('MANUAL', 'SYNCED')),
    created_by  uuid,
    created_at  timestamptz not null,
    version     bigint      not null default 0,
    constraint ck_groups_scope check (
        (scope = 'CLUB' and club_id is not null and source = 'MANUAL') or
        (scope = 'CAMPUS' and club_id is null))
);
create unique index ux_groups_campus_slug on groups.groups (slug) where scope = 'CAMPUS';
create unique index ux_groups_club_slug on groups.groups (club_id, slug) where scope = 'CLUB';

create table groups.group_members
(
    group_id uuid        not null references groups.groups (id) on delete cascade,
    user_id  uuid        not null,
    added_by uuid,
    added_at timestamptz not null,
    primary key (group_id, user_id)
);
create index ix_group_members_user on groups.group_members (user_id);