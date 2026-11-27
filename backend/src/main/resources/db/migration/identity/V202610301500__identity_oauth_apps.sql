-- OAuth apps (E20-S04): apps people register so that others can sign in to them with Virtual Clubroom.
-- Spring Authorization Server's own table, from its reference schema with PostgreSQL types (timestamptz), in public:
-- its JDBC classes name it without a schema.
create table public.oauth2_registered_client
(
    id                            varchar(100)  not null primary key,
    client_id                     varchar(100)  not null,
    client_id_issued_at           timestamptz   not null default current_timestamp,
    client_secret                 varchar(200),
    client_secret_expires_at      timestamptz,
    client_name                   varchar(200)  not null,
    client_authentication_methods varchar(1000) not null,
    authorization_grant_types     varchar(1000) not null,
    redirect_uris                 varchar(1000),
    post_logout_redirect_uris     varchar(1000),
    scopes                        varchar(1000) not null,
    client_settings               varchar(2000) not null,
    token_settings                varchar(2000) not null
);
create unique index ux_oauth2_registered_client_client_id on public.oauth2_registered_client (client_id);

-- What the authorization server doesn't keep: whose app it is, and what it is for.
create table identity.oauth_apps
(
    id           varchar(100) primary key references public.oauth2_registered_client (id) on delete cascade,
    owner_id     uuid         not null references identity.users (id) on delete cascade,
    description  varchar(500),
    homepage_url varchar(300),
    created_at   timestamptz  not null
);
create index ix_oauth_apps_owner on identity.oauth_apps (owner_id);
