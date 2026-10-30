# Decisions

What was decided and why. Newest first. A decision that changes is not deleted: it is marked *Replaced by* with the date of the newer entry.

## 2026-10-30

* **Accounts come with the onboarding rework, not in Milestone 7.** Building the onboarding without them would mean rebuilding it later, and every local row would need a new `user_id`. Supersedes the 2026-10-28 entry "Login and registration come before the Hungarian release" in timing.
* **Sign-in flow:** the onboarding ends with a sign-in screen (Google and e-mail) and a small "Körülnézek" (look around) button. That button signs the user in anonymously, so progress is saved from the start. After the first lesson, signing in is required: there is no "later" button, and the anonymous account is linked to the real one, so no progress is lost.
* **Supabase instead of Neon.** One service for PostgreSQL, authentication (including anonymous sign-in) and file storage with signed links, so no own API server is needed. Access is protected with row level security policies. Consequence: `user_id` becomes a UUID on the server and on the phone, and `auth.users` replaces the own user and password tables. Check pricing and free-tier limits before creating the project.
* **Onboarding asks:** teaching language (HU/SK), exact graduation date, English level (B1/B2) and daily goal in minutes. Settings move from DataStore to the user database; DataStore is removed.
* **Design system first.** A warm gold, brown and graphite palette on the Material 3 roles, light and dark (the old teal is dropped), Literata and Manrope. [docs/design-system](design-system/README.md) is the source; the Compose theme follows `tokens.json`.
* **One instruction file for all AI tools.** Rules live in `AGENTS.md`; `CLAUDE.md` only imports it. Local notes and AI outputs stay in the gitignored `llm/` folder; lasting decisions go into this file.
* **Small steps.** The roadmap is split into small steps, one change per step, and work is planned and approved before code is written.

## 2026-10-28

* **Content lives in a private repository** (`maturimate-content`), cloned into `content/` inside this repository and gitignored (`/content/`). This repository stays public with code and test fixtures only. Reason: the lessons and tasks are the owner's work and must not be downloadable from the public repository. CI of the public repository never sees real content; the private repository's CI runs the exporter.
* **Content protection, in order:** private content repository now; with login, downloads from private storage through short-lived signed links (optionally Play Integrity), and content split into packs per subject; terms of use and a copyright notice before release; SQLCipher only if copying is seen. Anything on the phone can be extracted by a determined person; the aim is to stop easy downloading.
* **Clean Architecture for the database:** generated SQLDelight classes stay in the data layer; features get domain models, repository interfaces and mappers. The UI never calls the database directly.
* **Content delivery: bundled file plus downloadable updates.** The app ships a prebuilt `content.db`; later it downloads newer versions from private storage (signed links) by `content-manifest.json`. No app release for new content. Reason: offline-first, cheap, no backend needed for content.
* **The content files in Git are the source of truth.** The exporter builds `content.db` directly from the files. The server PostgreSQL holds accounts, progress and a copy of the content, filled by the importer from the same files, because the user tables reference content rows by foreign key. *Neon replaced by Supabase: 2026-10-30.*
* **Content rows are never removed from the files**, only switched off (`is_active` / `is_published = false`). The exporter checks against the previous release.
* **The tools stay in Kotlin**, so the exporter shares the generated database code with the app. The `.sq` files move into a shared `:database` module.
* **The database design is final:** no table or column is added, removed or changed; needs the design does not store are solved in files or in app code. *Partly replaced: 2026-10-30 (UUID user ids and `auth.users` for Supabase).*
* **Login and registration come before the Hungarian release.** `user_profile` (with the required name and date of birth) is written at registration. *Timing replaced: 2026-10-30.*
* **No DataStore work in this phase.** *Replaced: 2026-10-30 (DataStore is removed in the onboarding rework).*
* **The onboarding is redesigned separately.** Its design decides how `user_profile`, `user_settings` and `user_subject` are filled. *Done: 2026-10-30.*
* **Work does not continue on the `database` branch.** A new branch from `main` is used. Reasons: the exam-centred schema is replaced; the branch conflicts with `main` in five files; its history contains `HU_MJL_2025.json` (a digitised official paper). Reused by hand: the SQLDelight Gradle setup and the `DatabaseDriverFactory` files.

## 2026-10-22

* V1 uses only content written for this project. Official NIVAM papers move to V2 and need written permission.
* VPUS/SNI extended time is dropped entirely.
* Teacher mode is dropped.
* Exam-style practice stays in V1, with own tasks and a plain time limit.
* Subject order: Hungarian, Slovak, English (B1/B2), Mathematics.
* The schema may change freely until the first tester installs the app; after that the user database needs migrations.
* Offline-first: server PostgreSQL, read-only content SQLite on the phone, user SQLite on the phone.
