-- Signing in to apps (E20-S05): Spring Authorization Server's authorizations and consents, from its reference schema
-- with PostgreSQL types (text for its blobs, timestamptz), in public like oauth2_registered_client. They go with their
-- app: deleting an app ends everyone's access through it.
create table public.oauth2_authorization
(
    id                            varchar(100) not null primary key,
    registered_client_id          varchar(100) not null references public.oauth2_registered_client (id) on delete cascade,
    principal_name                varchar(200) not null,             -- the user's id
    authorization_grant_type      varchar(100) not null,
    authorized_scopes             varchar(1000),
    attributes                    text,
    state                         varchar(500),
    authorization_code_value      text,
    authorization_code_issued_at  timestamptz,
    authorization_code_expires_at timestamptz,
    authorization_code_metadata   text,
    access_token_value            text,
    access_token_issued_at        timestamptz,
    access_token_expires_at       timestamptz,
    access_token_metadata         text,
    access_token_type             varchar(100),
    access_token_scopes           varchar(1000),
    oidc_id_token_value           text,
    oidc_id_token_issued_at       timestamptz,
    oidc_id_token_expires_at      timestamptz,
    oidc_id_token_metadata        text,
    refresh_token_value           text,
    refresh_token_issued_at       timestamptz,
    refresh_token_expires_at      timestamptz,
    refresh_token_metadata        text,
    user_code_value               text,
    user_code_issued_at           timestamptz,
    user_code_expires_at          timestamptz,
    user_code_metadata            text,
    device_code_value             text,
    device_code_issued_at         timestamptz,
    device_code_expires_at        timestamptz,
    device_code_metadata          text
);
create index ix_oauth2_authorization_access_token on public.oauth2_authorization (access_token_value);   -- every app request
create index ix_oauth2_authorization_refresh_token on public.oauth2_authorization (refresh_token_value);
create index ix_oauth2_authorization_code on public.oauth2_authorization (authorization_code_value);
create index ix_oauth2_authorization_state on public.oauth2_authorization (state);
create index ix_oauth2_authorization_principal on public.oauth2_authorization (principal_name, registered_client_id);

create table public.oauth2_authorization_consent
(
    registered_client_id varchar(100)  not null references public.oauth2_registered_client (id) on delete cascade,
    principal_name       varchar(200)  not null,
    authorities          varchar(1000) not null,                     -- SCOPE_forum:read,SCOPE_profile:read
    primary key (registered_client_id, principal_name)
);
