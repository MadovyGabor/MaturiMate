# Runbook: the daily backup

A GitHub Actions workflow (`.github/workflows/backup.yml`) backs up the user data every day at 02:17 UTC, and on demand
(Actions → Backup → Run workflow). Decided on 2026-11-11 (`content/DECISIONS.md`).

## What it does

1. `pg_dump --format=custom` of the **user tables** (profile, settings, subjects, roles, consents, the consent archive, rounds,
   answers, unit progress, streaks, daily activity, the download log, content reports, exam dates, content releases) into
   `user-<date>.dump`, and of `auth.users` and `auth.identities` into `auth-<date>.dump`. The content tables are left out:
   the private content repository and the importer restore them.
2. Encrypts both files with `age` for the public key in the secret `AGE_PUBLIC_KEY`. The plain files are deleted at once.
3. Uploads `user-<date>.dump.age` and `auth-<date>.dump.age` to `maturimate-backup/<date>/` in Cloudflare R2 (EU).
4. A lifecycle rule on the bucket deletes objects after 30 days (see [cloudflare.md](cloudflare.md)).

A failed step fails the run, and GitHub e-mails the owner (the default for scheduled workflows). The dump is also a database
connection, so it counts as activity for the free plan's pause rule.

## Set-up (once)

1. Create the bucket `maturimate-backup` (EU jurisdiction, private) and the lifecycle rule (30 days).
2. Create an R2 API token limited to that bucket (Object Read & Write is simplest; write only is enough for the workflow).
3. Make an `age` key pair on the owner's computer: `age-keygen -o maturimate-backup.agekey`. The **public** key (the line
   `age1…`) goes into the secret `AGE_PUBLIC_KEY`. The file with the **private** key goes into the password manager and
   nowhere else: without it the backups cannot be read, and with it anyone can read them.
4. GitHub → Settings → Secrets and variables → Actions → repository secrets:

   | Secret | Value |
   | :-- | :-- |
   | `SUPABASE_DB_URL` | the **session pooler** connection string with the database password: `postgresql://postgres.<ref>:<password>@aws-0-eu-west-1.pooler.supabase.com:5432/postgres` (IPv4, and `pg_dump` needs session mode) |
   | `AGE_PUBLIC_KEY` | the `age1…` public key |
   | `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` | the R2 account, the token of step 2, `maturimate-backup` |

5. Run the workflow once by hand and decrypt one file on the owner's computer to check it works (below).

## Secrets that cannot be rebuilt (password manager)

* The `age` private key.
* The Vault secret `consent_hmac_key` of the live Supabase project (step 12.6): without it the e-mail addresses in the consent
  archive cannot be matched again.

## Rotating the age key

Make a new pair, replace `AGE_PUBLIC_KEY`, keep the old private key as long as backups made with it exist (30 days).

## Reading a backup

```
aws s3 cp s3://maturimate-backup/<date>/user-<date>.dump.age . --endpoint-url https://<account>.r2.cloudflarestorage.com
age -d -i maturimate-backup.agekey -o user.dump user-<date>.dump.age
pg_restore --list user.dump
```

The restore itself, and its test, are in [restore.md](restore.md).

## Note for the privacy notice

Deleted accounts stay in the backups for up to 30 days, and the dump of `auth.users` holds e-mail addresses and password
hashes: the files are encrypted and the key is only with the owner (step 15.3).
