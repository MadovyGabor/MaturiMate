# MaturiMate Roadmap

> **Updated 2026-10-30:** the design system comes first, and the onboarding comes together with accounts (Supabase). Milestones are split into small steps. The reasons are in [DECISIONS](DECISIONS.md).
>
> **Updated 2026-10-22: change of plan.** The first version (V1) is a micro-learning app with content written for this project (short lessons and practice tasks). Digitising the official exam papers moved to V2 and depends on permission from the exam authority (NIVAM). The reasons are in the [README](../README.md). The milestones of the earlier plan are summarised at the end.

Legend: `[x]` = done; `[/]` = in progress; `[ ]` = planned, not started.

The order below is the working order: core first, then the design system, then onboarding with accounts, then the unit engine, then content, then release. Each milestone runs on its own branch, and each step is reviewed before the next one starts. Subjects follow one after another: Hungarian first, then Slovak, English and Mathematics.

---

## 0. Baseline on `main`

* [x] **Project scaffolding:** Kotlin 2.2.20, Compose Multiplatform 1.9.1, Koin 4.1.1, Material 3.
* [x] **Adaptive frame (`MainFrame`):** bottom `NavigationBar` below 600 dp and side `NavigationRail` at 600 dp and above.
* [x] **User onboarding:** welcome screen, language selector (HU/SK), English level selector (B1/B2), date picker, and a VPUS/SNI toggle.
* [x] **Profile screen:** graduation countdown, settings saved in DataStore. The subject statistics on this screen are mock data.
* [x] **Initial shells:** subject tab list and multi-stack back stack navigation (static data).
* [ ] **Decision:** the VPUS/SNI time extension is dropped. The toggle and its stored value are removed during the onboarding rework (Milestone 5).

---

## Data architecture (offline-first)

The app works fully offline. There are three databases with clear roles, and a content update never touches the user's progress. Details: [ARCHITECTURE](ARCHITECTURE.md#9-data-architecture).

| Database | Where | Content | Written by |
| :--- | :--- | :--- | :--- |
| Content database (SQLite) | Phone, read-only | Subjects, topics, lessons, tasks | Built from the content files; installed as a whole file |
| User database (SQLite) | Phone | Attempts, progress, streak, settings | The app only |
| Server database (PostgreSQL on Supabase) | Server (local Docker until then) | Accounts and synced progress, plus a copy of the content | Backend; the content importer |

How it works:

* **The content files are the source of truth.** Lessons and tasks are written as files (Markdown and JSON) in a private repository, checked by a validator, and exported into the content database file. The same files are imported into PostgreSQL, because the user tables there reference content rows.
* **Content reaches the phone as a whole file.** A prebuilt file ships inside the app, so the first start works without a network. Later the app downloads newer files (checked by version, size and hash) from private storage through signed links, without an app release.
* **Ids never change, rows are never deleted.** All ids are written in the content files. Tasks, lessons and topics are switched off instead of removed, so old attempts never point to nothing. There is no foreign key between the two files on the phone.
* **Accounts arrive with the onboarding (Milestone 5)** through Supabase Auth, starting with an anonymous account. Sync of user data arrives with Milestone 10, before the public Hungarian release.
* **One design, two dialects.** The PostgreSQL schema (`../dbServer/postgres/01_ddl.sql`) is final. The SQLite schemas (SQLDelight `.sq` files) repeat it with the same table and column names, only with SQLite types.
* **Passwords** are handled by Supabase Auth. The app holds only the public key; row level security decides which rows a user may read or write.
* **Hosting:** Supabase (PostgreSQL, Auth and Storage in one; check pricing and limits before signing up), with occasional `pg_dump` backups. The connection string stays in `DATABASE_URL`, outside the repository. Media (images, later audio) is stored separately in object storage and downloaded and cached by the app.

The schema is allowed to change freely until the first tester installs the app. From then on, changes to the user database need a migration.

---

## Milestone 1: Core and database

* [x] **1.1. Database design:** the final PostgreSQL schema with 30 tables (`../dbServer/postgres/01_ddl.sql`) and a local PostgreSQL in Docker.
* [x] **1.2. Local databases in the app:** SQLDelight content database (13 tables) and user database (8 tables) in a shared `:database` module, with drivers for Android and desktop.
* [x] **1.3. Content pipeline:** validator, exporter to the content database file, and importer into PostgreSQL (`../tools/content-pipeline`). The app installs the bundled content file on first start.
* [x] **1.4. Provenance on tasks:** every task and lesson records its source in the content files (own, teacher, AI reviewed by a human, public domain); the validator requires it.
* [x] **1.5. Review of the `database` branch:** its drivers and Gradle setup were reused; its exam-centred schema was replaced. Deleting the branch (it contains a digitised official paper) waits for the owner's decision.
* [x] **1.6. Repositories:** domain models and repository interfaces for content and progress, with SQLDelight implementations in the data layer.

---

## Milestone 2: Design system in the code

Branch: `feat/theme`. The source is [docs/design-system](design-system/README.md); the Compose theme follows `tokens.json`.

* [ ] **2.1.** `Color.kt` and `Theme.kt`: the new light and dark palette on the Material 3 roles; the old teal and the unused contrast variants are removed.
* [ ] **2.2.** Extended colors (`brand-gold`, the `success` family, `focus-ring`) in an `ExtendedColors` class provided through a `CompositionLocal`.
* [ ] **2.3.** Fonts: Literata and Manrope bundled as resources; `Type.kt` with the type scale and the `reading` style.
* [ ] **2.4.** `Shapes` with the radius scale.
* [ ] **2.5.** Components the onboarding needs: button variants, choice chip, progress bar, text field.
* [ ] **2.6.** A component catalogue screen (desktop) to check every component in both themes.

---

## Milestone 3: Onboarding design (prototype, no code)

Branch: `feat/onboarding-rework` (Milestones 3 to 5).

* [ ] **3.1.** Inspiration: onboarding and sign-up screens of well-known apps, summarised in a few points.
* [ ] **3.2.** The order of the screens and where a step can be skipped.
* [ ] **3.3.** A clickable prototype in the design system.
* [ ] **3.4.** Final Hungarian and Slovak texts.

---

## Milestone 4: Supabase setup

See [DECISIONS](DECISIONS.md) (2026-10-30) for why Supabase.

* [ ] **4.1.** Check pricing and free-tier limits; the owner creates the project.
* [ ] **4.2.** Schema change: `user_id` becomes a UUID, user tables reference `auth.users`, the own user and password tables are removed (PostgreSQL DDL and SQLDelight).
* [ ] **4.3.** Apply the schema to Supabase; keep the local Docker database in line.
* [ ] **4.4.** Row level security policies on every user table, with tests.
* [ ] **4.5.** Enable anonymous, e-mail and Google sign-in.
* [ ] **4.6.** Key handling: only the public key in the app, secrets outside the repository.

---

## Milestone 5: Onboarding rework

* [ ] **5.1.** Remove the VPUS/SNI toggle and its stored value.
* [ ] **5.2.** Profile and settings move to the user database; DataStore is removed.
* [ ] **5.3.** `supabase-kt` in the app; anonymous sign-in on first start (later if offline).
* [ ] **5.4.** Welcome screen.
* [ ] **5.5.** Teaching language (HU/SK).
* [ ] **5.6.** Graduation date.
* [ ] **5.7.** English level (B1/B2).
* [ ] **5.8.** Daily goal.
* [ ] **5.9.** Sign-in screen: Google and e-mail, plus a small "Körülnézek" button that keeps the anonymous account.
* [ ] **5.10.** The start screen is chosen from the user database (onboarding or home).
* [ ] **5.11.** Profile screen update and tests.

---

## Milestone 6: Unit engine with one small unit

Goal: one complete lesson that works end to end. After that, new units are mostly new data.

* [ ] **6.1. Lesson screen:** short lesson text in the app's own text format, estimated reading time.
* [ ] **6.2. Practice tasks:** multiple choice and short answer, instant feedback, accepted answer variants.
* [ ] **6.3. Reading passage view:** a text shown together with its tasks.
* [ ] **6.4.** Loading the first unit through the importer and the content database.
* [ ] **6.5. Required sign-in after the first lesson:** no "later" button; the anonymous account is linked to the real one, so no progress is lost.

---

## Milestone 7: Exam-style practice with own content

* [ ] **7.1.** A practice mode that combines a passage and its tasks, with a plain time limit (no special time extension).
* [ ] **7.2.** Grading, result screen and task-by-task review.
* [ ] **7.3.** Local history of finished attempts.

---

## Milestone 8: Streak, progress and the Hungarian content

* [ ] **8.1.** Daily streak and per-topic progress, stored locally.
* [ ] **8.2.** Hungarian units, written together with the engine. *Open question: the planned "30 main topics". It is not decided yet whether this means 30 topics or 30 lessons.*
* [ ] **8.3.** Review of missed tasks (error vault) as a later addition.

---

## Milestone 9: UI polish and beta

* [ ] **9.1.** Animations, reading view polish, accessibility checks.
* [ ] **9.2.** Review of the user database tables before the first testers install the app.
* [ ] **9.3.** Closed beta of the Hungarian subject on Android.
* [ ] **9.4.** Localization of the UI strings (Slovak and Hungarian).

---

## Milestone 10: Sync and the public Hungarian release

* [ ] **10.1.** Sync of user data between the phone and Supabase.
* [ ] **10.2.** The content update flow described under "Data architecture" (signed links from Supabase Storage).
* [ ] **10.3.** Account deletion and the privacy notice (required by GDPR and the app stores).
* [ ] **10.4.** Public release of the Hungarian subject.

---

## Milestone 11: More subjects and platforms

* [ ] **11.1. Slovak:** new content and the grammar task types.
* [ ] **11.2. English (B1/B2):** level filtering, cloze tasks. Listening tasks with own recordings or transcripts.
* [ ] **11.3. Mathematics:** formula rendering, numeric answers with comma and dot normalisation.
* [ ] **11.4. Platforms:** desktop (a target already exists), then iOS, then web.

---

## Milestone 12 (V2): Official exam content

Only after NIVAM gives permission. The request is planned for when the app works and there are real usage numbers to show. If it is refused, the app continues on its own content.

* [ ] **12.1.** Written permission covering papers, answer keys, audio, offline caching, attribution and free or paid use.
* [ ] **12.2.** Official papers and listening tasks with audio, loaded as new rows. The exam table returns as a database migration.

---

## Milestones of the earlier plan

The earlier plan digitised the official papers first: database core with a JSON seeder, vertical slices per exam subject (`HU_MJL`, `SK_SJL`, `AJ_B1`, `AJ_B2`, mathematics), an exam runner with timer, grading and exam history. The work for the first step exists on the unmerged `database` branch. These milestones are not part of V1; what survives is reused above (the subject order, the exam-style practice mode, grading and history).
