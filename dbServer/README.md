# Local PostgreSQL

Everything for the server database in one place. The app never connects to it; it is used for development and by the content importer (`tools/content-pipeline`).

```
dbServer/
├── docker-compose.yml   PostgreSQL 16 in Docker
├── .env.example         settings; copy to .env (gitignored)
└── postgres/
    └── 01_ddl.sql       the schema (30 tables); the design is final
```

## Start

```bash
cp dbServer/.env.example dbServer/.env              # then change the password
docker compose -f dbServer/docker-compose.yml up -d
```

The DDL runs once, when the data volume is created. `POSTGRES_PORT` sets the host port; use another one (for example 5433) if 5432 is taken, and change `DATABASE_URL` to match.

## Stop or rebuild

```bash
docker compose -f dbServer/docker-compose.yml down        # stop, keep the data
docker compose -f dbServer/docker-compose.yml down -v     # stop and delete the data; the DDL runs again on the next start
```

## Used by

- The importer reads `DATABASE_URL` from the environment or from `/.env`.
- `PostgresWriterTest` creates a throwaway database with the DDL; it is skipped when PostgreSQL is not running.
