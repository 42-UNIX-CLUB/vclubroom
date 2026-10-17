-- One schema per module (docs/04-database.md §3.1); no cross-schema foreign keys.
-- spring.flyway.create-schemas also creates them, but only on an empty database: databases that were
-- already migrated before the schema list existed get them here. IF NOT EXISTS keeps both paths idempotent.
-- The event_publication table of §3.1 is created by V202610171200__modulith_event_publication.sql.
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS club;
CREATE SCHEMA IF NOT EXISTS membership;
CREATE SCHEMA IF NOT EXISTS groups;
CREATE SCHEMA IF NOT EXISTS space;
CREATE SCHEMA IF NOT EXISTS media;
CREATE SCHEMA IF NOT EXISTS forum;
CREATE SCHEMA IF NOT EXISTS chat;
CREATE SCHEMA IF NOT EXISTS calendar;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS discovery;
CREATE SCHEMA IF NOT EXISTS history;

-- Trigram indexes for fuzzy name search (club directory, user search).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
