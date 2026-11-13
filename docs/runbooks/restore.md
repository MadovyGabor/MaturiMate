# Runbook: restoring a backup (and testing it)

Do the real test **once before the alpha and then every six months**, and write the date and the result at the bottom.
A backup nobody has restored is only a hope.

## The real test: into a second free Supabase project

The free plan allows two projects (the account has one today, so there is room). The second one is in the EU like the live one.
It will hold real personal data during the test, so it is **emptied afterwards**.

1. Download and decrypt the two files of a day (see [backup.md](backup.md), "Reading a backup").
2. Create the second project. Apply the schema: `supabase link --project-ref <second>` then `supabase db push`.
3. Fill the content: run the importer against the second project (`DATABASE_URL` = its pooler string) with the content files:
   `./gradlew :tools:content-pipeline:run --args="import content"`. The user tables refer to content rows by foreign key, so the
   content comes first.
4. Restore the user data: `pg_restore --data-only --disable-triggers -d "$DATABASE_URL" user.dump`, then the Auth data:
   `pg_restore --data-only --disable-triggers -d "$DATABASE_URL" auth.dump` (the tables `auth.users` and `auth.identities`
   exist in a Supabase project already).
5. Check: sign in with a test account on a development build pointed at that project (`SupabaseConfig` is a constant: change it
   for the test, do not commit that), look at the profile, a round, the streak, and run a sync.
6. **Empty the project** (delete the data, or the project) and let it pause.

## The quick check on the local Docker database

`dbServer` (the local PostgreSQL): apply `dbServer/postgres/00_auth_stub.sql` and the migrations, import the content, then
`pg_restore --data-only --disable-triggers` the user dump. The auth dump can be restored whole (it carries its table
definitions) after dropping the stand-in `auth.users` of the stub. This catches a broken dump or a schema that changed since;
it does not replace the real test.

## Later idea (not now)

The second project could become a cold standby: the same migrations pushed to both, the same Auth, OAuth, SMTP, Vault and cron
settings by a checklist, kept awake by the backup workflow (a free project paused for 90 days cannot be woken up), the data
restored from the latest backup on a switch, an app update with the new address, a runbook. See the roadmap's later ideas.

## Log

| Date | Which backup | Result |
| :-- | :-- | :-- |
| _not done yet_ | | |
