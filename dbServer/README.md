# Local PostgreSQL

A plain PostgreSQL in Docker with the same schema as Supabase, for development and for the content importer (`tools/content-pipeline`). The app never connects to it.

The schema lives in [`supabase/migrations`](../supabase/migrations) and is the same one that runs on Supabase. Supabase Auth provides the `auth.users` table there; here `postgres/00_auth_stub.sql` creates a minimal stand-in for it.

```
dbServer/
├── docker-compose.yml        PostgreSQL 16 in Docker
├── .env.example              settings; copy to .env (gitignored)
└── postgres/
    ├── 00_auth_stub.sql      a stand-in for Supabase: auth.users, auth.uid(), the anon and authenticated roles
    └── 10_migrations.sh      applies supabase/migrations in name order
```

## Start

```bash
cp dbServer/.env.example dbServer/.env              # then change the password
docker compose -f dbServer/docker-compose.yml up -d
```

The schema is created once, when the data volume is created. `POSTGRES_PORT` sets the host port; use another one (for example 5433) if 5432 is taken, and change `DATABASE_URL` to match.

## Stop or rebuild

```bash
docker compose -f dbServer/docker-compose.yml down        # stop, keep the data
docker compose -f dbServer/docker-compose.yml down -v     # stop and delete the data; the schema is created again on the next start
```

A database created before the Supabase migration (with the old `01_ddl.sql`) still has the old schema: rebuild it with `down -v`.

## Used by

- The importer reads `DATABASE_URL` from the environment or from `dbServer/.env`.
- `PostgresWriterTest` creates a throwaway database with the auth stand-in and the migrations; it is skipped when PostgreSQL is not running.
- `supabase/tests/rls_test.sql` checks the row level security policies, and `supabase/tests/sync_test.sql` the sync, the download permission, the export and the clean-up (CI runs both after the migrations):

  ```bash
  for f in dbServer/postgres/00_auth_stub.sql supabase/migrations/*.sql supabase/tests/rls_test.sql supabase/tests/sync_test.sql; do
    psql -v ON_ERROR_STOP=1 -q -f "$f"
  done
  ```
- `pg_cron` is not in this image: the schedule of `clean_up_logs()` (migration `20261125120000_account_data.sql`) is skipped here; on Supabase enable the extension once (Database → Extensions) and apply the migration again or schedule it by hand.
- `PublisherTest` (the `publish` command) uses a throwaway database like `PostgresWriterTest`.
