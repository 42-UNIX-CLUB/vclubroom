-- membership schema (docs/04-database.md §4.3): roles, memberships and their roles, join requests, ownership invitations.
create table membership.club_roles
(
    id          uuid primary key,
    club_id     uuid        not null,
    name        varchar(40) not null,
    color       varchar(16),
    system_role varchar(16) check (system_role in ('OWNER', 'MEMBER')),
    permissions text[]      not null default '{}',
    position    integer     not null default 0,
    created_at  timestamptz not null,
    version     bigint      not null default 0
);
create unique index ux_club_roles_name on membership.club_roles (club_id, lower(name));
create unique index ux_club_roles_system on membership.club_roles (club_id, system_role) where system_role is not null;

create table membership.memberships
(
    id        uuid primary key,
    club_id   uuid        not null,
    user_id   uuid        not null,
    status    varchar(16) not null check (status in ('ACTIVE', 'LEFT', 'REMOVED')),
    joined_at timestamptz not null,
    ended_at  timestamptz,
    ended_by  uuid,
    version   bigint      not null default 0
);
create unique index ux_memberships_active on membership.memberships (club_id, user_id) where status = 'ACTIVE'; -- BR-07
create index ix_memberships_user_active on membership.memberships (user_id) where status = 'ACTIVE';
create index ix_memberships_club_active on membership.memberships (club_id, joined_at) where status = 'ACTIVE';

create table membership.membership_roles
(
    membership_id uuid not null references membership.memberships (id) on delete cascade,
    role_id       uuid not null references membership.club_roles (id) on delete cascade,
    primary key (membership_id, role_id)
);
create index ix_membership_roles_role on membership.membership_roles (role_id);

create table membership.join_requests
(
    id         uuid primary key,
    club_id    uuid        not null,
    user_id    uuid        not null,
    message    varchar(500),
    status     varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    decided_by uuid,
    decided_at timestamptz,
    created_at timestamptz not null,
    version    bigint      not null default 0
);
create unique index ux_join_requests_pending on membership.join_requests (club_id, user_id) where status = 'PENDING'; -- BR-07
create index ix_join_requests_club_status on membership.join_requests (club_id, status, created_at);

create table membership.ownership_invitations
(
    id                  uuid primary key,
    club_id             uuid        not null,
    invitee_id          uuid        not null,
    invited_by          uuid        not null,
    step_down_on_accept boolean     not null default false,
    status              varchar(16) not null check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    created_at          timestamptz not null,
    expires_at          timestamptz not null,
    responded_at        timestamptz,
    version             bigint      not null default 0
);
create unique index ux_invitations_pending on membership.ownership_invitations (club_id, invitee_id) where status = 'PENDING'; -- BR-06
create index ix_invitations_expiry on membership.ownership_invitations (expires_at) where status = 'PENDING';
