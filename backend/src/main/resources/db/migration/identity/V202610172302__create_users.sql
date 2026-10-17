-- identity.users: 42 users (auto-provisioned at login) and admin-created local users (docs/04-database.md §4.1).
create table identity.users
(
    id            uuid primary key,
    auth_provider varchar(16)  not null check (auth_provider in ('FORTY_TWO', 'LOCAL')),
    ft_id         bigint unique,
    login         varchar(64)  not null,
    email         varchar(320),
    display_name  varchar(128) not null,
    avatar_url    text,
    password_hash varchar(255),
    platform_role varchar(16)  not null default 'USER' check (platform_role in ('USER', 'ADMIN')),
    status        varchar(16)  not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED')),
    ft_pool_year  integer,
    ft_pool_month varchar(16),
    ft_alumni     boolean      not null default false,
    ft_staff      boolean      not null default false,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    last_login_at timestamptz,
    version       bigint       not null default 0,
    constraint ck_users_provider_fields check (
        (auth_provider = 'FORTY_TWO' and ft_id is not null and password_hash is null) or
        (auth_provider = 'LOCAL' and ft_id is null and password_hash is not null))
);
create unique index ux_users_login on identity.users (lower(login));                       -- BR-23
create index ix_users_admins on identity.users (platform_role) where platform_role = 'ADMIN';
create index ix_users_display_name_trgm on identity.users using gin (lower(display_name) gin_trgm_ops);