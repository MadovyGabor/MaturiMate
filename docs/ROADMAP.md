# MaturiMate Roadmap

> **Updated 2026-11-03:** Milestone 7 is redrawn after the prototype of the learning screens: it starts with Hungarian only, works from sample content that goes through the real importer, and has more, smaller steps (7.1 to 7.8). Slovak, English and Mathematics follow in Milestone 12. The reasons are in [DECISIONS](DECISIONS.md).
>
> **Updated 2026-11-01:** the toolchain update (AGP 9) becomes Milestone 6, before the unit engine; the milestones after it move up by one. The reasons are in [DECISIONS](DECISIONS.md).
>
> **Updated 2026-10-30:** the design system comes first, and the onboarding comes together with accounts (Supabase). Milestones are split into small steps. The reasons are in [DECISIONS](DECISIONS.md).
>
> **Updated 2026-10-22: change of plan.** The first version (V1) is a micro-learning app with content written for this project (short lessons and practice tasks). Digitising the official exam papers moved to V2 and depends on permission from the exam authority (NIVAM). The reasons are in the [README](../README.md). The milestones of the earlier plan are summarised at the end.

Legend: `[x]` = done; `[/]` = in progress; `[ ]` = planned, not started.

The order below is the working order: core first, then the design system, then onboarding with accounts, then the toolchain update, then the unit engine, then content, then release. Each milestone runs on its own branch, and each step is reviewed before the next one starts. Subjects follow one after another: Hungarian first, then Slovak, English and Mathematics.

---

## 0. Baseline on `main`

* [x] **Project scaffolding:** Kotlin 2.2.20, Compose Multiplatform 1.9.1, Koin 4.1.1, Material 3.
* [x] **Adaptive frame (`MainFrame`):** bottom `NavigationBar` below 600 dp and side `NavigationRail` at 600 dp and above.
* [x] **User onboarding:** welcome screen, language selector (HU/SK), English level selector (B1/B2), date picker, and a VPUS/SNI toggle.
* [x] **Profile screen:** graduation countdown, settings saved in DataStore. The subject statistics on this screen are mock data.
* [x] **Initial shells:** subject tab list and multi-stack back stack navigation (static data).
* [x] **Decision:** the VPUS/SNI time extension is dropped. The toggle and its stored value are removed during the onboarding rework (Milestone 5, step 5.1–5.2).

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
* **Content reaches the phone as a whole file, in two stages.** The app ships only a small starter pack (the first lesson), so the first start and the guest work without a network. After sign-up the app downloads the full content file from private Supabase Storage (served through its CDN) with a short-lived signed link, checks it by version, size and hash, and installs it in place of the starter pack. Newer files arrive the same way, without an app release.
* **Ids never change, rows are never deleted.** All ids are written in the content files. Tasks, lessons and topics are switched off instead of removed, so old attempts never point to nothing. There is no foreign key between the two files on the phone.
* **Accounts arrive with the onboarding (Milestone 5)** through Supabase Auth. Before signing up, a guest's data stays on the phone only (local guest, see [DECISIONS](DECISIONS.md) 2026-10-31). Sync of user data arrives with Milestone 11, before the public Hungarian release.
* **One design, two dialects.** The PostgreSQL schema lives in `../supabase/migrations`; changes are new migrations. The SQLite schemas (SQLDelight `.sq` files) repeat it with the same table and column names, only with SQLite types.
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

* [x] **2.1.** `Color.kt` and `Theme.kt`: the new light and dark palette on the Material 3 roles; the old teal and the unused contrast variants are removed.
* [x] **2.2.** Extended colors (`brand-gold`, the `success` family, `focus-ring`) in an `ExtendedColors` class provided through a `CompositionLocal`.
* [x] **2.3.** Fonts: Literata and Manrope bundled as resources; `Type.kt` with the type scale and the `reading` style.
* [x] **2.4.** `Shapes` with the radius scale (adds `radius-xs`) and the spacing steps (`MaterialTheme.spacing`). A test checks the theme against `tokens.json`.

Components are not built in advance. Each screen builds the components it needs in `core/presentation/uiComponents/` from theme values, documented in `docs/design-system/components/`, so later screens reuse them. A component catalogue screen comes later, once there are enough components.

---

## Milestone 3: Onboarding design (prototype, no code)

Branch: `feat/onboarding-rework` (Milestones 3 to 5).

* [x] **3.1.** Inspiration: onboarding and sign-up screens of well-known apps, summarised in a few points.
* [x] **3.2.** The order of the screens and where a step can be skipped.
* [x] **3.3.** A clickable prototype in the design system.
* [x] **3.4.** Final Hungarian and Slovak texts.

---

## Milestone 4: Supabase setup

See [DECISIONS](DECISIONS.md) (2026-10-30) for why Supabase.

* [x] **4.1.** Check pricing and free-tier limits; the owner creates the project.
* [x] **4.2a.** Server schema as a Supabase migration (`supabase/migrations`), replacing `dbServer/postgres/01_ddl.sql`: the changes below, row level security enabled on every table (no policies yet), the role catalogue seeded.
* [x] **4.2b.** The same changes in the phone databases (SQLDelight): `user_id` as text, `user_role`, optional birth date, names optional on the phone for guests, and the phone-only `local_user` with the guest id (`LocalUserProvider`). Roles have fixed ids (1 STUDENT … 5 ADMIN) on both sides.
* **4.2 in short.** Schema change: `user_id` becomes a UUID, user tables reference `auth.users`, the own user and password tables are removed (PostgreSQL DDL and SQLDelight). `user_consent` (already in the design) records which terms and privacy version was accepted and when, and the marketing consent; it gets an `AGE_16` type for the age confirmation. `date_of_birth` becomes nullable; the user role is added to the phone database.
* [x] **4.3.** Apply the schema to Supabase; keep the local Docker database in line. Applied through the Supabase connector; the migration file names match the versions Supabase recorded. A second migration fixes the database advisors' findings (fixed `search_path` on the trigger functions, `rls_auto_enable()` not callable through the API, indexes on five foreign keys).
* [x] **4.4.** Row level security policies, with tests (`supabase/tests/rls_test.sql`, run in CI). Signed-in users read and write only their own rows; they can give themselves only the student, learner or teacher role; consents can be added and withdrawn, not rewritten; content reports are resolved by curators and admins; the leaderboard is read-only. Content tables have no policy, so the API does not expose them. Not signed in: no access.
* [x] **4.5.** Enable e-mail and Google sign-in. Anonymous sign-ins stay off. Settings: [supabase-setup.md](../llm/supabase-setup.md).
* [x] **4.7.** E-mail sending through Resend with the `maturimate.madovygabor.com` subdomain (custom SMTP); then "Confirm email" back on with templates that carry the 6-digit code (sign-up confirmation, password reset). Needed before the account screen of Milestone 5 can be tested.
* [x] **4.6.** Key handling: only the public key in the app, secrets outside the repository. `core/config/SupabaseConfig.kt` holds the project URL, the publishable key and the Google web client id; secrets stay in Supabase, Resend, Google Cloud and `.env` files (gitignored).

---

## Milestone 5: Onboarding rework

Each screen step also builds the components it needs (buttons, chips, progress bar, text field), as described under Milestone 2.

* [x] **5.0.** Schema: `user_subject.level` (English level) and `user_profile.onboarding_completed_at`, in a new Supabase migration and in the local user database.
* [x] **5.1–5.2.** Profile and settings move to the user database; DataStore is removed, and with it the VPUS/SNI toggle and its stored value. *Merged into one step on 2026-10-31: the old onboarding is rebuilt anyway, so the toggle is not removed from it separately.*
* [x] **5.3.** `supabase-kt` in the app for sign-up and sign-in; a local guest profile with a temporary local id until the user signs up.
* [x] **5.4.** Welcome screen: "Kezdjük" and "Már van fiókom", with the shared onboarding frame (`OnboardingScaffold`) and `AppButton`. "Már van fiókom" opens the sign-in since 5.12.
* [x] **5.5.** Teaching language (HU/SK); also sets the app language. With `AppChoiceCard`, `AppProgressBar`, the step header and the step list.
* [x] **5.6.** Role: student, learner or teacher, saved in `user_role`; the number of steps follows the role (8, 7 or 6). *For now learners and teachers finish the onboarding after the role, and students go on to the old setup screen; steps 5.7 to 5.12 fill the gap.*
* [x] **5.7.** Subjects: compulsory ones preselected by the teaching language, the English level (B1 / B2 / "Még nem tudom") in `user_subject.level`. With `AppChoiceChip`; the next step now follows the role (`nextRoute`), and the onboarding ends after the last step built so far.
* [x] **5.8.** Graduation date (students only): the next two spring exams as cards and "Más dátum" with a calendar limited to the next three years (`AppDatePickerDialog`). The old setup screen and its `OnboardingViewModel` are removed.
* [x] **5.9.** Daily goal (students and learners): 5, 10, 15 or 20 minutes as chips, 10 preselected, with the hours in a year. The goal stays empty in the database until it is answered.
* [x] **5.10.** Reminder: time picker, then the notification permission; "Most nem" skips it. Only the setting is saved (`reminder_enabled`, `reminder_time`); sending the daily notification comes with the streak (9.1).
* [x] **5.11.** "A te terved" summary: days until the exam with the hours of practice (students), the daily goal and its hours in a year (learners), the subjects with the English level, and the reminder.
* [x] **5.12.** Account screen: Google and e-mail (with the 6-digit code), plus a small "Körülnézek" button that continues as a local guest. The same step adds the sign-in screen with the forgotten password, and shows the "Már van fiókom" button on the welcome screen. After sign-up the guest's rows become the account's and the profile and consents go to Supabase; after sign-in the account's profile comes to the phone.
* [x] **5.13.** The start screen is chosen from the user database, and an unfinished onboarding resumes where it stopped: `App` picks the start screen and the language from the profile; `onboardingBackStack` rebuilds the steps up to the first unanswered one (the reminder, the plan and the account are always shown again). An end-to-end test (`OnboardingFlowTest`) goes from the welcome screen to the home screen.
* [x] **5.14.** Profile screen update and tests.
* [x] **5.15. Fixes after the first phone test:** tab navigation, fewer labels and larger text on the onboarding, Google sign-in at the end of the onboarding (Google first, then the name and the consents in the app, and only then the rows on the server), the sign-up e-mail with the 6-digit code instead of a link, and the exam subjects on the live server, with `HU_SJSL` for Hungarian schools, English as `ANJ_B1` and `ANJ_B2`, and every subject for teachers.

*Steps 5.4 to 5.14 renumbered on 2026-10-31 to follow the nine screens of [the onboarding flow](onboarding/README.md#5-flow-32); role, subjects, reminder and plan were missing.*

---

## Milestone 6: Toolchain update (AGP 9)

Branch: `chore/toolchain-agp9`. Moved here from the polish milestone on 2026-11-01, so the unit engine is built on the new module layout and versions (see [DECISIONS](DECISIONS.md) 2026-11-01). The plan, the versions and what changes: [toolchain-agp9.md](../llm/toolchain-agp9.md). The build tooling changes; the app's behaviour does not. Every tool and library goes to its newest stable version (no alpha, beta or release candidate).

* [x] **6.1.** Plan: target versions, what AGP 9 changes, the new module layout (`shared`, `androidApp`, `desktopApp`), the order of the steps.
* [x] **6.2.** `:database` moves to the Android-KMP library plugin (`com.android.kotlin.multiplatform.library`), still on AGP 8.13.
* [x] **6.3.** `composeApp` is renamed to `shared` (paths, Compose resources imports, CI, docs); nothing else changes.
* [x] **6.4.** New `:androidApp` module (activity, application class, manifest, launcher icons, splash); `:shared` becomes a Kotlin Multiplatform library with the Android-KMP library plugin. The application id stays `com.madovygabor.maturimate`.
* [x] **6.5.** New `:desktopApp` module (`main.kt`, window, desktop packaging, hot reload).
* [x] **6.6.** AGP 9.5 and Gradle 9.8, compileSdk and targetSdk 37, built-in Kotlin; obsolete Gradle properties removed.
* [x] **6.7.** Compose Multiplatform 1.12, Navigation3 1.2, Lifecycle 2.11, Ktor 3.6; the deprecated `compose.*` dependency aliases, `uiTooling` and the old `@Preview` import are replaced; unused catalog entries removed.
* [x] **6.8.** CI (new action versions, JDK 21), run commands and documentation follow the new layout; test on a phone (update over the installed app, Google sign-in, content, reminder) and on desktop. *Tested by the owner on 2026-11-01 (Android with Google sign-in, desktop with hot reload, Android Studio). The first CI run on the new setup comes with the next push.*

---

## Milestone 6a: Code review fixes

Branch: `chore/toolchain-agp9`, one commit per step. The bugs, their causes and the fixes: [review-2026-11-01.md](../llm/review-2026-11-01.md). *Added and done on 2026-11-01; the phone test of the backup and the first CI runs are done (2026-11-03).*

* [x] **6a.1.** Error handling: crash on unexpected server data, the profile read error that ends in the onboarding (and the flaky `AppViewModelTest`), the onboarding that went back to the welcome screen after the language, dropped errors and cancellations, one user database call at a time.
* [x] **6a.2.** Account flow: password reset after a refused password, resuming the onboarding after sign-in, two accounts on one phone, the snackbar that blocks buttons.
* [x] **6a.3.** Server consistency: the end of a sign-up in one transaction (`save_sign_up`), consents once. *Applied to the live project on 2026-11-01 (version 20261101214731).*
* [x] **6a.4.** Android release: backup without the session, R8 shrinking (APK from 28.5 MB to 4.9 MB). *Tested on a phone by the owner (2026-11-03).*
* [x] **6a.5.** Tests and coverage: Kover with an 80 % minimum in CI (`shared` 89.8 %, `database` 86.4 %), the missing tests. *The shared profile flow is left out (see the review notes, 5.2); Android device tests wait for an emulator in CI.*
* [x] **6a.6.** Cleanup of dead code.
* [x] **6a.7.** Faster CI: one run per commit (no separate `pull_request` run), no run for documentation-only changes, the Android build as its own job beside the tests, parallel Gradle builds and parallel test JVMs. *Added on 2026-11-01; running since the merge of the toolchain branch (PR #12).*

---

## Milestone 7: Unit engine with one small unit

Goal: lessons and tests that work end to end, for Hungarian first: subjects, parts, topics, the path, lesson pages, the seven task formats, tests and results. After that, new units are mostly new data. The screens follow the prototype of 7.0; every step also covers the tablet and desktop layout. Until the real lessons arrive, the screens run on the sample content of 7.0a ("[PRÓBA]" titles, ids from 900000), which goes through the real exporter and never to Supabase.

* [x] **7.0. Design of the learning screens:** a clickable prototype ([docs/learning/prototype.html](learning/prototype.html)): subjects, big parts, topic list, the path with the line that fills, lesson pages with a table of contents (a side list on tablet and desktop), the seven task formats with feedback, the result. Reviewed by the owner on 2026-11-03.
* [x] **7.0a. Content model for the lessons:** pages and boxes in the lesson file (`unit_page`), `practice_kind` (`MINI`, `TOPIC`, `FINAL`; `FINAL` on a parent topic), `task_option.explanation` (`why`); the loader, validator, exporter and importer, and a Supabase migration. The sample content (private repository, `content/`) is rewritten like the prototype: Nyelvtan with Szóképek, Alakzatok, Szófajok and a final test, Irodalom with Az ember tragédiája. *Done on 2026-11-03; the Supabase migration `20261103120000_lesson_pages.sql` and `PostgresWriterTest` are checked by CI (no local Docker), the migration is not applied to the live project yet.* Format: [content-pipeline README](../tools/content-pipeline/README.md#lesson-files-pages-and-boxes), decisions of 2026-11-03.
* [ ] **7.1. Navigation and the path:** the "Tantárgyak" tab shows the user's subjects; then the big parts (Nyelvtan, Irodalom), the topic list with progress rings, and the path of a topic with the line that fills (progress from `user_unit_progress`). The app text format (`**bold**`, `*italic*`, `[[n]]`) is rendered by one shared component.
* [ ] **7.2. Lesson screen:** pages with titles, paragraphs and boxes (the labels are app strings), one page per screen, "Tovább" and swipe, the table of contents (a sheet on the phone, a side list on tablet and desktop), estimated reading time. The first Hungarian text only; the bilingual text model comes with Mathematics (12.3).
* [ ] **7.3. Tasks, first formats:** multiple choice, true or false and short answer; `AnswerChecker` (tested per format, accepted variants), instant feedback with the explanation and the `why` of the chosen option, the hint of the short answer.
* [ ] **7.4. The other formats:** matching, ordering, gaps from a word list and typed gaps.
* [ ] **7.5. Tests and results:** the `MINI`, `TOPIC` and `FINAL` tests (rounds of `round_size` tasks), the result screen, review of the wrong answers, saving the attempt and the unit progress in the user database, the next step of the path opens.
* [ ] **7.6. Real content path:** the sample content is bundled through `bundleContent`; the exporter builds two files, the starter pack bundled in the app (first lesson only) and the full content file. The real lessons replace the sample ones.
* [ ] **7.7. Reading passage view:** a text shown together with its tasks (the connected texts of the design).
* [ ] **7.8. Required sign-in after the first lesson:** *The local move of the guest's rows exists since 5.12 (`LocalUserProvider.adoptAccount`).* no "later" button. The guest's local rows are moved to the new account id in one transaction, so no progress is lost. Signing in to an existing account keeps that account's data and adds the guest's progress where the account has none. Without an account every other lesson is locked.

---

## Milestone 8: Exam-style practice with own content

* [ ] **8.1.** A practice mode that combines a passage and its tasks, with a plain time limit (no special time extension).
* [ ] **8.2.** Grading with points, the exam result screen and task-by-task review (the plain result of a test exists since 7.5).
* [ ] **8.3.** Local history of finished attempts.

---

## Milestone 9: Streak, progress and the Hungarian content

* [ ] **9.1.** Daily streak and the progress summary, stored locally (the unit progress itself is saved from 7.5); the daily reminder notification at the time saved in the onboarding (5.10).
* [ ] **9.2.** Hungarian units, written together with the engine, as parts, topics (a work, an author, a language area), lessons and tests. *Open question: the planned "30 main topics". It is not decided yet whether this means 30 topics or 30 lessons.*
* [ ] **9.3.** Review of missed tasks (error vault) as a later addition.

---

## Milestone 10: UI polish and beta

*Android is the target platform; desktop is for development. Small bugs, polish, animations and platform tuning are collected during the earlier milestones and done here, once the Hungarian subject is ready and before the release (owner's choice, 2026-11-01).*

* [ ] **10.0. Bug fixes:** the bugs found while testing on a phone since the onboarding (5.15), collected here as they are found:
  * The labels of the bottom navigation bar are Hungarian in both languages.
  * After a change of the teaching language the subjects are not asked again (a Hungarian school's subjects stay when switching to Slovak).
  * The "Tantárgyak" tab still shows the old hard-coded list, not the user's subjects (rebuilt in 7.1).
  * Google sign-in on desktop is not tried yet (it works on Android).
  * Unconfirmed e-mail sign-ups stay in Supabase Auth; a cleanup belongs to 11.3a.
* [ ] **10.1.** Animations, reading view polish, accessibility checks.
* [ ] **10.2.** Review of the user database tables before the first testers install the app.
* [ ] **10.3.** Closed beta of the Hungarian subject on Android.
* [ ] **10.4.** Localization of the UI strings (Slovak and Hungarian).
* [ ] **10.5. Profile settings:** the name and the account in the profile, "Fiók létrehozása" for guests, and a reset that starts over. *Added on 2026-11-01; not part of the onboarding.*
* [/] **10.6. Exam dates:** the written exams start on the Monday of the second week of March; the app computes the date from this rule (done on 2026-11-01, `ExamDates.secondWeekWeekday`) and takes the official date when it is published. *How the official date reaches the app is open.*

---

## Milestone 11: Sync and the public Hungarian release

* [ ] **11.1.** Sync of user data between the phone and Supabase.
* [ ] **11.2.** Content download after sign-up and later updates, as described under "Data architecture": a private Storage bucket readable only by signed-in users, signed links, download to a temporary file, hash check, then swap in one step; on failure the current file stays. Large downloads ask before using mobile data.
* [ ] **11.3.** Account deletion and the privacy notice (required by GDPR and the app stores).
* [ ] **11.3a.** Abuse protection for sign-up: invisible CAPTCHA (hCaptcha or Cloudflare Turnstile) on sign-up and password reset, and a review of the auth rate limits. Synced progress is checked on the server, not trusted as sent. Optional, if abuse is seen: Play Integrity before a content download.
* [ ] **11.3b. Consent documents:** the terms of use and the privacy notice written and published (`maturimate.madovygabor.com`), linked from the consent text on the account step, with real document versions instead of "1.0". *Added on 2026-11-01.*
* [ ] **11.4.** Public release of the Hungarian subject.

---

## Milestone 12: More subjects and platforms

* [ ] **12.1. Slovak:** new content and the grammar task types.
* [ ] **12.2. English (B1/B2):** level filtering, cloze tasks. Listening tasks with own recordings or transcripts. B1 and B2 are two subjects (`ANJ_B1`, `ANJ_B2`, DECISIONS 2026-11-01).
* [ ] **12.3. Mathematics:** formula rendering, numeric answers with comma and dot normalisation.
* [ ] **12.4. Platforms:** desktop (a target already exists), then iOS, then web.

---

## Milestone 13 (V2): Official exam content

Only after NIVAM gives permission. The request is planned for when the app works and there are real usage numbers to show. If it is refused, the app continues on its own content.

* [ ] **13.1.** Written permission covering papers, answer keys, audio, offline caching, attribution and free or paid use.
* [ ] **13.2.** Official papers and listening tasks with audio, loaded as new rows. The exam table returns as a database migration.

---

## Milestones of the earlier plan

The earlier plan digitised the official papers first: database core with a JSON seeder, vertical slices per exam subject (`HU_MJL`, `SK_SJL`, `AJ_B1`, `AJ_B2`, mathematics), an exam runner with timer, grading and exam history. The work for the first step exists on the unmerged `database` branch. These milestones are not part of V1; what survives is reused above (the subject order, the exam-style practice mode, grading and history).
