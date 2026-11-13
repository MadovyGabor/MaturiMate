# Runbook: Cloudflare (content packs and backups)

What lives in the Cloudflare account, and how to set it up or change it. Decided on 2026-11-12 (see `content/DECISIONS.md`).

## What is there

| Thing | Name | Purpose |
| :-- | :-- | :-- |
| R2 bucket (EU jurisdiction, **private**) | `maturimate-content` | the content packs, one per subject and version |
| R2 bucket (EU jurisdiction, **private**) | `maturimate-backup` | the daily encrypted backups of the user data ([backup.md](backup.md)) |
| Worker | `maturimate-content-link` (`workers/content-link/`) | verifies the user's Supabase token, asks the SQL function `register_content_download`, returns a 10-minute presigned link to a pack |

No public access, no `r2.dev` address and no custom domain on either bucket. The only way to a pack is the link of the Worker.

## R2 API tokens (Cloudflare dashboard → R2 → Manage API tokens)

Three tokens, each limited to one bucket and to what it needs:

| Token | Bucket | Permission | Where it lives |
| :-- | :-- | :-- | :-- |
| content write | `maturimate-content` | Object Read & Write | `dbServer/.env` of the owner: `R2_CONTENT_ACCESS_KEY_ID`, `R2_CONTENT_SECRET_ACCESS_KEY` (used by `publish`) |
| content read | `maturimate-content` | Object Read | Worker secrets `R2_READ_ACCESS_KEY_ID`, `R2_READ_SECRET_ACCESS_KEY` |
| backup write | `maturimate-backup` | Object Write | GitHub secrets (see [backup.md](backup.md)) |

`dbServer/.env` also holds `R2_ACCOUNT_ID` (and optionally `R2_CONTENT_BUCKET`, default `maturimate-content`). None of these ever goes into the repository.

## Publishing a content release

1. `./gradlew :tools:content-pipeline:run --args="import content"` (writes the content to the server, which the sync's checks need).
2. `./gradlew :tools:content-pipeline:run --args="publish content --version=yyyyMMddNN"`: exports the packs, uploads them to `packs/<schema>/<subject>/<version>/content.db` and writes the `content_release` rows (the older release of the same subject and schema stops being current). It refuses to run before the import.
3. The apps see the new release at their next opening (the sync response carries the list) and download it.

Objects are never overwritten: the version is in the key.

## The Worker

```
cd workers/content-link
npm ci
npm test
npx wrangler login
npx wrangler secret put R2_READ_ACCESS_KEY_ID
npx wrangler secret put R2_READ_SECRET_ACCESS_KEY
npx wrangler deploy
```

Before the first deploy replace `R2_ACCOUNT_ID` in `wrangler.toml` with the account id, and put the address the deploy prints with `/link` added (`https://maturimate-content-link.<subdomain>.workers.dev/link`; the Worker answers only that path, everything else is 404) into `core/config/ContentConfig.kt` of the app. If the Supabase project still signs its tokens with the old shared secret (Supabase → Settings → JWT keys), also `npx wrangler secret put SUPABASE_JWT_SECRET`; with the newer asymmetric keys nothing more is needed (the Worker reads the public keys).

Tested in CI (`worker` job, Vitest). The deploy is manual.

Check by hand after a deploy, with the access token of a test account:

```
curl -s -X POST https://<worker>/link -H "authorization: Bearer <token>" -H "content-type: application/json" -d '{"subject_id":1,"schema_version":7}'
```

A link comes back; the 4th call of the day for one subject is 429; a wrong token is 401.

## Limits to keep an eye on (free plan, from memory, check on Cloudflare's pricing page)

* Workers: 100 000 requests a day (one request per download).
* R2: 10 GB storage, 1 million writes and 10 million reads a month, **egress free**.

## The 30-day clean-up of the backups

A lifecycle rule on `maturimate-backup`: delete objects after 30 days (R2 → the bucket → Settings → Object lifecycle rules). Set once; see [backup.md](backup.md).
