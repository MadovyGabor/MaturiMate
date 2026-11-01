#!/bin/sh
# Applies the Supabase migrations (mounted at /migrations) in name order.
set -e
for f in /migrations/*.sql; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
done
