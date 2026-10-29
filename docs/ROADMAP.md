# MaturiMate Roadmap

> **Updated 2026-10-22: change of plan.** The first version (V1) is a micro-learning app with content written for this project (short lessons and practice tasks). Digitising the official exam papers moved to V2 and depends on permission from the exam authority (NIVAM). The reasons are in the [README](../README.md). The milestones of the earlier plan are summarised at the end.

Legend: `[x]` = done (Milestone 1 is on the branch `feat/database-core` until it is merged into `main`); `[/]` = in progress; `[ ]` = planned, not started.

The order below is the working order: core first, then onboarding, then the unit engine, then content, then release. Subjects follow one after another: Hungarian first, then Slovak, English and Mathematics.

---

## 0. Baseline on `main`

* [x] **Project scaffolding:** Kotlin 2.2.20, Compose Multiplatform 1.9.1, Koin 4.1.1, Material 3.
* [x] **Adaptive frame (`MainFrame`):** bottom `NavigationBar` below 600 dp and side `NavigationRail` at 600 dp and above.
* [x] **User onboarding:** welcome screen, language selector (HU/SK), English level selector (B1/B2), date picker, and a VPUS/SNI toggle.
* [x] **Profile screen:** graduation countdown, settings saved in DataStore. The subject statistics on this screen are mock data.
* [x] **Initial shells:** subject tab list and multi-stack back stack navigation (static data).
* [ ] **Decision:** the VPUS/SNI time extension is dropped. The toggle and its stored value are removed during the onboarding rework (Milestone 2).

---

## Data architecture (offline-first)

The app works fully offline. There are three databases with clear roles, and a content update never touches the user's progress. Details: [ARCHITECTURE](ARCHITECTURE.md#9-data-architecture).

| Database | Where | Content | Written by |
| :--- | :--- | :--- | :--- |
| Content database (SQLite) | Phone, read-only | Subjects, topics, lessons, tasks | Built from the content files; installed as a whole file |
| User database (SQLite) | Phone | Attempts, progress, streak, settings | The app only |
| Server database (PostgreSQL) | Server (local Docker until then) | Accounts and synced progress, plus a copy of the content | Backend; the content importer |

How it works:

* **The content files are the source of truth.** Lessons and tasks are written as files (Markdown and JSON) in a private repository, checked by a validator, and exported into the content database file. The same files are imported into PostgreSQL, because the user tables there reference content rows.
* **Content reaches the phone as a whole file.** A prebuilt file ships inside the app, so the first start works without a network. Later the app downloads newer files (checked by version, size and hash) from private storage through signed links, without an app release.
* **Ids never change, rows are never deleted.** All ids are written in the content files. Tasks, lessons and topics are switched off instead of removed, so old attempts never point to nothing. There is no foreign key between the two files on the phone.
* **Until accounts exist, the user database is local only.** Nothing is sent anywhere. Login and sync arrive with Milestone 7, before the public Hungarian release.
* **One design, two dialects.** The PostgreSQL schema (`../dbServer/postgres/01_ddl.sql`) is final. The SQLite schemas (SQLDelight `.sq` files) repeat it with the same table and column names, only with SQLite types.
* **Passwords** are stored hashed, and the app never talks to the server database directly. The backend holds the credentials.
* **Hosting (later):** a free PostgreSQL tier to start (Neon is the current choice; check pricing and limits before signing up), with occasional `pg_dump` backups. The connection string stays in `DATABASE_URL`, outside the repository. Media (images, later audio) is stored separately in object storage and downloaded and cached by the app.

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

## Milestone 2: Onboarding rework

* [ ] **2.1.** Decide which screens and choices the new onboarding needs (for example language, English level, graduation date) and remove what no longer fits.
* [ ] **2.2.** Remove the VPUS/SNI toggle and its stored value.
* [ ] **2.3.** No account step yet. The profile is local.

---

## Milestone 3: Unit engine with one small unit

Goal: one complete lesson that works end to end. After that, new units are mostly new data.

* [ ] **3.1. Lesson screen:** short lesson text in the app's own text format, estimated reading time.
* [ ] **3.2. Practice tasks:** multiple choice and short answer, instant feedback, accepted answer variants.
* [ ] **3.3. Reading passage view:** a text shown together with its tasks.
* [ ] **3.4.** Loading the first unit through the importer and the content database.

---

## Milestone 4: Exam-style practice with own content

* [ ] **4.1.** A practice mode that combines a passage and its tasks, with a plain time limit (no special time extension).
* [ ] **4.2.** Grading, result screen and task-by-task review.
* [ ] **4.3.** Local history of finished attempts.

---

## Milestone 5: Streak, progress and the Hungarian content

* [ ] **5.1.** Daily streak and per-topic progress, stored locally.
* [ ] **5.2.** Hungarian units, written together with the engine. *Open question: the planned "30 main topics". It is not decided yet whether this means 30 topics or 30 lessons.*
* [ ] **5.3.** Review of missed tasks (error vault) as a later addition.

---

## Milestone 6: UI polish and beta

* [ ] **6.1.** Animations, reading view polish, accessibility checks.
* [ ] **6.2.** Review of the user database tables before the first testers install the app.
* [ ] **6.3.** Closed beta of the Hungarian subject on Android, local data only.
* [ ] **6.4.** Localization of the UI strings (Slovak and Hungarian).

---

## Milestone 7: Accounts and sync (before the public Hungarian release)

* [ ] **7.1.** Backend (REST/JSON) in front of the PostgreSQL database.
* [ ] **7.2.** Registration and login with hashed passwords.
* [ ] **7.3.** Sync of user data and the content update flow described under "Data architecture".
* [ ] **7.4.** Public release of the Hungarian subject.

---

## Milestone 8: More subjects and platforms

* [ ] **8.1. Slovak:** new content and the grammar task types.
* [ ] **8.2. English (B1/B2):** level filtering, cloze tasks. Listening tasks with own recordings or transcripts.
* [ ] **8.3. Mathematics:** formula rendering, numeric answers with comma and dot normalisation.
* [ ] **8.4. Platforms:** desktop (a target already exists), then iOS, then web.

---

## Milestone 9 (V2): Official exam content

Only after NIVAM gives permission. The request is planned for when the app works and there are real usage numbers to show. If it is refused, the app continues on its own content.

* [ ] **9.1.** Written permission covering papers, answer keys, audio, offline caching, attribution and free or paid use.
* [ ] **9.2.** Official papers and listening tasks with audio, loaded as new rows. The exam table returns as a database migration.

---

## Milestones of the earlier plan

The earlier plan digitised the official papers first: database core with a JSON seeder, vertical slices per exam subject (`HU_MJL`, `SK_SJL`, `AJ_B1`, `AJ_B2`, mathematics), an exam runner with timer, grading and exam history. The work for the first step exists on the unmerged `database` branch. These milestones are not part of V1; what survives is reused above (the subject order, the exam-style practice mode, grading and history).
