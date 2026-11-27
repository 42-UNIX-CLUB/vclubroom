-- identity.personal_access_tokens (E20): tokens people create to call the API from their own scripts and apps.
-- Only a hash of each token is kept: it is shown once, when created.
create table identity.personal_access_tokens
(
    id           uuid primary key,
    user_id      uuid         not null references identity.users (id) on delete cascade,
    name         varchar(80)  not null,
    token_hash   varchar(64)  not null unique,      -- SHA-256 of the token, in hex
    last_four    varchar(4)   not null,             -- shown as vcr_pat_…abcd
    scopes       varchar(400) not null,             -- space-separated, e.g. 'forum:read forum:write'
    created_at   timestamptz  not null,
    expires_at   timestamptz,                       -- null: never expires
    last_used_at timestamptz,
    reminded_at  timestamptz,                       -- when the week-before-expiry reminder went out
    revoked_at   timestamptz
);
create index ix_personal_access_tokens_user on identity.personal_access_tokens (user_id) where revoked_at is null;
