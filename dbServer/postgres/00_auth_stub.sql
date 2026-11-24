-- Local stand-in for Supabase on a plain PostgreSQL: only what the schema and
-- its row level security use. Supabase itself provides the real versions.

-- auth.users: the accounts
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users (id uuid PRIMARY KEY, email text);
-- the times the clean-up of empty users reads (Supabase has them; added here for an older local database too)
ALTER TABLE auth.users ADD COLUMN IF NOT EXISTS created_at timestamptz DEFAULT now();
ALTER TABLE auth.users ADD COLUMN IF NOT EXISTS last_sign_in_at timestamptz;

-- auth.sessions: the columns the clean-up of empty users reads (refreshed_at is a timestamp without time zone in GoTrue)
CREATE TABLE IF NOT EXISTS auth.sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    refreshed_at timestamp
);

-- Supabase keeps pgcrypto in the schema `extensions` and secrets in the Vault (vault.decrypted_secrets)
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
CREATE SCHEMA IF NOT EXISTS vault;
CREATE TABLE IF NOT EXISTS vault.decrypted_secrets (name text PRIMARY KEY, decrypted_secret text);

-- auth.uid(): the signed-in user, read from the JWT claims of the request
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid
LANGUAGE sql STABLE AS $$
    SELECT nullif(
        coalesce(
            current_setting('request.jwt.claim.sub', true),
            nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'),
        '')::uuid
$$;

-- auth.jwt(): all the claims of the request's JWT (Supabase's definition); the deletion reads its `amr`
CREATE OR REPLACE FUNCTION auth.jwt() RETURNS jsonb
LANGUAGE sql STABLE AS $$
    SELECT coalesce(
        nullif(current_setting('request.jwt.claim', true), ''),
        nullif(current_setting('request.jwt.claims', true), ''))::jsonb
$$;

-- The API roles, with the table privileges Supabase grants by default
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
END;
$$;
GRANT USAGE ON SCHEMA public, auth TO anon, authenticated;
GRANT EXECUTE ON FUNCTION auth.uid(), auth.jwt() TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated;
-- Supabase also grants EXECUTE on every new function to the API roles: a function is closed only by its own REVOKE
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO anon, authenticated;
