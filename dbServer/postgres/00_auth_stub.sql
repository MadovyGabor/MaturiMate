-- Local stand-in for Supabase Auth on a plain PostgreSQL: only what the schema
-- references. Supabase itself provides the real auth.users table.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users (id uuid PRIMARY KEY);
