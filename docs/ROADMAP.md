# MaturiMate Roadmap

> **Updated 2026-11-06 (staged release):** the plan from Milestone 10 on is redrawn: Hungarian content first, then the groundwork (11), the sync and download (12), code review (13) and UI refinement (14), all before the alpha (15); then the closed beta (16), the other subjects as downloadable packs (17), the open beta (18) and the release in September 2027 (19). Steps of finished milestones keep their old numbers; each new step says "Was …".
>
> **Updated 2026-11-03 (reordered):** the milestones after 7 are redrawn: 8 home screen and streak, 9 navigation, profile and settings, 9a practice, 10 Hungarian content, 11 sync and content download, 12 beta, polish and public release. The exam-style practice milestone is dropped. Old step numbers are noted in each step as "Was …".
>
> **Updated 2026-11-03 (later):** Milestone 7 is done with steps 7.0 to 7.5. The content path with the starter pack (was 7.6) and the required sign-in after the first lesson (was 7.8) moved to Milestone 11 (11.4 and 11.5), because they only matter before the release. The reading passage view (was 7.7) is dropped for now: there are no reading comprehension tasks in the first material; it comes back with an extension of the content model when such tasks are added.
>
> **Updated 2026-11-03:** Milestone 7 is redrawn after the prototype of the learning screens: it starts with Hungarian only, works from sample content that goes through the real importer, and has more, smaller steps (7.1 to 7.5). Slovak, English and Mathematics follow in Milestone 17.
>
> **Updated 2026-11-01:** the toolchain update (AGP 9) becomes Milestone 6, before the unit engine; the milestones after it move up by one.
>
> **Updated 2026-10-30:** the design system comes first, and the onboarding comes together with accounts (Supabase). Milestones are split into small steps.
>
> **Updated 2026-10-22: change of plan.** The first version (V1) is a micro-learning app with content written for this project (short lessons and practice tasks). Digitising the official exam papers moved to V2 and depends on permission from the exam authority (NIVAM). The reasons are in the [README](../README.md). The milestones of the earlier plan are summarised at the end.

Legend: `[x]` = done; `[/]` = in progress; `[ ]` = planned, not started.

The order below is the working order: core first, then the design system, then onboarding with accounts, then the toolchain update, then the unit engine, the app around the lessons, the Hungarian content, the groundwork, the sync, the code review and UI refinement, and only then the staged release (alpha, closed beta, open beta, release). Each milestone runs on its own branch, and each step is reviewed before the next one starts. Subjects follow one after another: Hungarian first, then Slovak, English and Mathematics.

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
* **Accounts arrive with the onboarding (Milestone 5)** through Supabase Auth. Before signing up, a guest's data stays on the phone only (local guest). Sync of user data arrives with Milestone 12, before the alpha.
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
* [x] **1.5. Review of the `database` branch:** its drivers and Gradle setup were reused; its exam-centred schema was replaced. The branch (it contained a digitised official paper) has been deleted.
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

Branch: `chore/toolchain-agp9`. Moved here from the polish milestone on 2026-11-01, so the unit engine is built on the new module layout and versions. The plan, the versions and what changes: [toolchain-agp9.md](../llm/toolchain-agp9.md). The build tooling changes; the app's behaviour does not. Every tool and library goes to its newest stable version (no alpha, beta or release candidate).

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

## Milestone 7: Unit engine with one small unit *(done on 2026-11-03)*

Goal: lessons and tests that work end to end, for Hungarian first: subjects, parts, topics, the path, lesson pages, the seven task formats, tests and results. After that, new units are mostly new data. The screens follow the prototype of 7.0; every step also covers the tablet and desktop layout. Until the real lessons arrive, the screens run on the sample content of 7.0a ("[PRÓBA]" titles, ids from 900000), which goes through the real exporter and never to Supabase.

* [x] **7.0. Design of the learning screens:** a clickable prototype ([docs/learning/prototype.html](learning/prototype.html)): subjects, big parts, topic list, the path with the line that fills, lesson pages with a table of contents (a side list on tablet and desktop), the seven task formats with feedback, the result. Reviewed by the owner on 2026-11-03.
* [x] **7.0a. Content model for the lessons:** pages and boxes in the lesson file (`unit_page`), `practice_kind` (`MINI`, `TOPIC`, `FINAL`; `FINAL` on a parent topic), `task_option.explanation` (`why`); the loader, validator, exporter and importer, and a Supabase migration. The sample content (private repository, `content/`) is rewritten like the prototype: Nyelvtan with Szóképek, Alakzatok, Szófajok and a final test, Irodalom with Az ember tragédiája. *Done on 2026-11-03; the Supabase migration `20261103120000_lesson_pages.sql` and `PostgresWriterTest` are checked by CI (no local Docker), the migration is not applied to the live project yet.* Format: [content-pipeline README](../tools/content-pipeline/README.md#lesson-files-pages-and-boxes), decisions of 2026-11-03.
* [x] **7.1. Navigation and the path:** the "Tantárgyak" tab shows the user's subjects; then the big parts (Nyelvtan, Irodalom), the topic list with progress rings, and the path of a topic with the line that fills (progress from `user_unit_progress`). The app text format (`**bold**`, `*italic*`, `[[n]]`) is rendered by one shared component. *Done on 2026-11-03 except the shared text component, which the first lesson and task screens (7.2, 7.3) need and build: `LearningCourse`, two ViewModels, `AppPathStep` and `AppProgressRing`; a tap on a step says "coming soon". The old static subject and exam list screens are removed. To see the sample content in the app: `./gradlew :tools:content-pipeline:bundleContent -PcontentDir=content -PcontentVersion=2026110301`.*
* [x] **7.2. Lesson screen:** pages with titles, paragraphs and boxes (the labels are app strings), one page per screen, "Tovább" and swipe, the table of contents (a sheet on the phone, a side list on tablet and desktop), estimated reading time. The first Hungarian text only; the bilingual text model comes with Mathematics (12.3). *Done on 2026-11-03: `LessonViewModel`, `LessonScreen`, `AppLessonBox`, `AppSegmentedProgress` and `AppRichText`; finishing a lesson saves it as done, so the path shows the progress. The side list of the table of contents starts at 840 dp (`breakpoint-expanded`); a tablet in portrait uses the sheet.*
* [x] **7.3. Tasks, first formats:** multiple choice, true or false and short answer; `AnswerChecker` (tested per format, accepted variants), instant feedback with the explanation and the `why` of the chosen option, the hint of the short answer. *Done on 2026-11-03: `AnswerChecker` works for all seven formats (tested), the screens for three; `TestSessionViewModel` and `TestScreen` run a test (no result and no saved progress yet, that is 7.5); the other formats are skipped in a round until 7.4. New components: `AppAnswerOption`, `AppTrueFalseCard`, `AppFeedbackSheet`.*
* [x] **7.4. The other formats:** matching, ordering, gaps from a word list and typed gaps. *Done on 2026-11-03: the answer is kept per slot; new components `AppMatchPair`, `AppGapSlot` and `AppGapField`; the round no longer skips any format.*
* [x] **7.5. Tests and results:** the `MINI`, `TOPIC` and `FINAL` tests (rounds of `round_size` tasks), the result screen, review of the wrong answers, saving the attempt and the unit progress in the user database, the next step of the path opens. *Done on 2026-11-03, with changes agreed on the way: a round is the required tasks plus random ones to fill `round_size`, in random order; a test is done, and the next step opens, only when a round is passed (every task right; otherwise the result offers another try); the steps of a path are walked in order (the ones ahead are locked, the final test of a parent topic too); a lesson goes on to the test after it; and every answer is recorded in the new `attempt_answer` table (phone migration `2.sqm`, Supabase migration `20261103130000_attempt_answer.sql`, not applied to the live project yet) for the mistake practice (9.3) and the statistics (9.4).*

---

## Milestone 8: Home screen and motivation

Reordered on 2026-11-03: the app around the lessons (home, streak, profile, settings, sync) comes before the Hungarian content, which is written last. The exam-style practice milestone (passage mode, points, exam result, history) is dropped: the result, the review and the progress of 7.5 are enough for now.

**The four tabs**: the home screen says what to do today, the subjects are the lessons, the practice tab says what to practise, the profile says where the student stands. Each screen is designed as a clickable prototype first, without code, like the learning screens (7.0), and only approved screens go into the app.

* [x] **8.1. Daily goal and streak (data):** the streak and the progress of the daily goal, stored locally (the unit progress itself is saved from 7.5); a "last activities" query (the last lessons and tests, three of them on the home screen). *Was 9.1.* *Done on 2026-11-04: `StudyTracker` (study seconds per day against the goal in minutes, the streak, no grace day), `StudyClock` (time between moves, a gap counts at most two minutes) and `RecentActivities`; lessons and test answers feed the tracker. The column `activity_log.minutes_studied` became `seconds_studied` (phone migration `3.sqm`, Supabase migration `20261104120000_activity_seconds.sql`, not applied to the live project yet). The "continue" target of the home screen comes with the prototype (8.3).*
* [x] **8.2. Reminder:** the daily reminder notification at the time saved in the onboarding (5.10), switched off for a day that is already done. *Done on 2026-11-04 for Android: `ReminderSync` keeps one inexact alarm (`AlarmManager`) in line with the profile; `ReminderReceiver` shows the notification in the app language, with the streak when there is one, unless the day's goal is reached, and plans the next day; planned again after a restart. No reminder on the desktop (no background service): `NoReminderScheduler`.*
* [x] **8.3. Home screen design:** a clickable prototype of the new first tab: days until the exam, the streak and the daily goal, a big "continue" button (the last lesson or test), a suggestion for today, the last three activities, a short motivating line. *Done on 2026-11-04: [docs/home/prototype.html](home/prototype.html), approved by the owner: greeting with the first name and a motto that follows the situation, the streak and the days until the exam side by side, the daily goal ring (with the days of the week on tablet and desktop), a big "Folytatás" card, a suggestion card (from 9a), the last three activities. The streak is shown as in danger all day until the goal is reached.*
* [x] **8.4. Home screen:** the screen from the approved prototype. *With 8.3's prototype, the "Főoldal" tab name of the subject list becomes "Tantárgyak".* *Done on 2026-11-04: `HomeOverview` (one call for the whole screen), `HomeViewModel`, `HomeScreen`, new components `AppGoalRing`, `AppWeekStrip` and `AppInfoChip`; a new first tab "Kezdőlap", the old "Főoldal" tab is now "Tantárgyak" (the tab labels are still written in the code, in Hungarian only: both languages come with 9.5). "Folytatás" opens the subjects tab with the way to the step in its back stack and starts nothing; the suggestion card (mistake practice) is there with a "Hamarosan" tag until 9a. The first name comes from the profile.*

---

## Milestone 9: Navigation, profile and settings *(done on 2026-11-04)*

The four tabs are built here; the practice tab is only a "Hamarosan" screen until Milestone 9a.

* [x] **9.1. Navigation design:** the four tabs (Kezdőlap, Tantárgyak, Gyakorlás, Profil) and their icons, as a prototype; the old "Előzmények" tab goes away, its content moves to the profile. The labels follow the app language (they are Hungarian in both languages now). *Done on 2026-11-04: [docs/navigation/prototype.html](navigation/prototype.html), approved by the owner: Slovak Domov, Predmety, Cvičenie, Profil; a light bulb for the practice tab; the side rail centred on a tablet and at the top on the desktop.*
* [x] **9.2. Profile, design:** a prototype: name, a short description of the student, the progress in every subject (percent of lessons and tests done), the streak and the total learning so far, the full history of finished attempts, and a gear icon for the settings. The profile shows the facts; the practice tab shows what to work on, so the same numbers are not shown twice. *Done on 2026-11-04: [docs/profile/prototype.html](profile/prototype.html), approved by the owner: a small initial and the name instead of a description; one card with the streak, the study time and the solved tasks; the progress per subject opens into its big parts; the last three activities, and the full history grouped by day with a filter. Open for 9.3: what the full history shows for activities older than a week.*
* [x] **9.3. Profile:** the screen from the approved prototype, with real data. It replaces the hard-coded subject statistics. *Was 9.4 and 8.3.* *Done on 2026-11-04: `ProfileOverview` (streak, total study time and tasks, lessons and tests per subject and per big part, the last three activities), `ActivityHistory` with the day groups (Ma, Tegnap, 2 to 6 days, 1 to 3 weeks, then the month), `ProfileOverviewScreen`, `HistoryScreen` with the filter, and the profile tab's own navigation. New components `AppActivityCard` (also used by the home screen now), `AppStatCard` and `AppSubjectProgressCard`. Until 9.4 the gear opens the onboarding answers (the old profile screen). A guest sees a "Hozz létre fiókot" card right under the name, with "Fiók létrehozása" and "Már van fiókom": the account and sign-in screens of the onboarding open inside the profile tab (the account screen without its steps and "Körülnézek", `inApp`), and go back to the profile when done. The guest's rows move to the account (`adoptAccount`, since 5.12); a sign-up of a guest no longer overwrites the day the onboarding ended.*
* [x] **9.4a. Settings, the screen:** *Done on 2026-11-04 from the approved prototype ([docs/settings/prototype.html](settings/prototype.html)): `SettingsScreen` (it replaces the list of answers behind the gear), the groups Fiók, Tanulás, Értesítések, App, Adatok and the red rows; the school language and the app language are set apart (`saveSchoolLanguage`, `saveUiLanguage`; a new school language asks for the subjects again); the reminder switch; a guest's account rows. The rows of later steps show "Hamarosan": the password change, the reset and the sign-out (9.4b), the sync (11.2), the content update (11.3), the account deletion (11.6). New components `AppSettingsGroup` and `AppSettingsRow`.*
* [x] **9.4b. Settings, the actions:** the password change (not for a Google account), the sign-out (a warning, then the local data go and the welcome screen comes), the reset of the progress (the finished lessons and tests, the answers and the mistakes; the streak, the study time and the settings stay), and editing the name (it needs a call to the server). *Done on 2026-11-04: `AccountSettings` (the name on the server first, then on the phone; the old password checked by a sign-in with it; the sign-out removes every local row and a new guest starts, `removeUser`), `ProgressRepository.resetProgress`, and the dialogs of the settings (`SettingsDialogs.kt`). A Google account is recognised by its identities (`AccountUser.hasPassword`). The family name is now part of the local profile. No migration: only queries changed.*
* *9.4 as first planned (split on 2026-11-04 into 9.4a and 9.4b):* a separate screen opened from the profile (not a tab): the name and the account, "Fiók létrehozása" for guests, the language, the reminder, the daily goal, the exam date, change and delete of the password, sign out, and a reset that starts over. Account deletion itself is 10.6. *Was 10.5.*
* [x] **9.5. Navigation:** the four tabs built with the labels in both languages; the practice tab shows "Hamarosan" until 9a. *Done on 2026-11-04: the labels are app strings (Kezdőlap, Tantárgyak, Gyakorlás, Profil; Domov, Predmety, Cvičenie, Profil), the practice tab has a light bulb and `PracticeSoonScreen`, the "Előzmények" tab and its route are gone (its content is the profile's history), and the rail starts at the top from 840 dp and stays centred below.*

---

## Milestone 9a: Practice *(done on 2026-11-05)*

Added on 2026-11-03, split from Milestone 9. It comes before the sync (Milestone 12), because the "last seen" column of 9a.5 had to be in the user database before it is reviewed (11.1). *(9a.5 needed no column in the end: "last seen" is derived from what is already stored.)* The detailed analytics may become paid later; the screen is laid out so they can be locked without a redesign, and no paywall is built now.

*Updated 2026-11-05: nothing is locked in the beta; plain practice (9a.2) is new and stays free for good. The old 9a.2 to 9a.5 are now 9a.3 to 9a.6.*

* [x] **9a.1. Practice tab, design:** a prototype (`docs/practice/prototype.html`) of: the plain practice (pick finished units, number of questions), the weakest and the strongest subjects and topics, the mistake practice, the review of what was not seen for a long time. No lock is shown in the beta.
* [x] **9a.2. Plain practice:** the student picks from the **finished** units (a subject, a big part, a topic or several linked topics) and the number of questions; random tasks from them, no statistics. Answers are recorded as in every round. *Done on 2026-11-05: the attempt types `FREE_PRACTICE` and `MISTAKE_PRACTICE` (the phone's database migrated, the Supabase migration written for the sync), `PracticeCatalog` and `PracticePlanner`, one round engine for tests and practice (`RoundSource`), the practice tab with the free practice card, the picker (finished topics, tri-state ticks, 10 / 20 / 50 / custom length), the practice result, and the practice rounds in the history, the home screen and the profile with a "Gyakorlás" filter.*
* [x] **9a.3. Mistake practice:** a separate feature in which the student practices the tasks they got wrong: all of them, those of one subject or those of one topic. It is built on the `attempt_answer` rows that every test round records from step 7.5 on and on the `REVIEW` attempts (a subject level may need a small addition to the attempt scope). *Replaces the earlier "error vault" line (2026-11-03).* *Done on 2026-11-05: a mistake is a task whose latest answer is wrong (read from the task progress); the "Hibagyakorlás" card of the practice tab ("Összes hiba", at most 20 at once, and "Tantárgy vagy téma", the picker in mistake mode), mistakes of unfinished topics included, "Nincs több hibád" when none is left, and the home card, which opens the practice tab.*
* [x] **9a.4. Weak and strong topics:** from `attempt_answer`, which subjects and topics go best and which worst, and a button that starts practice on a weak topic. *Was 9.4 (statistics).* *Done on 2026-11-05: the "Erős és gyenge témák" card of the practice tab, topics only (each card names its subject), at most three weak (below 80%) and three strong; a topic gets a score from 5 tasks seen (for now, see 12.7); "Gyakorlás" on a weak topic asks all its mistakes and fills the round with its other tasks, up to 30.*
* [x] **9a.5. Review of old material:** lessons and topics that the student has not looked at for a long time, with a short review test; the oldest first, per subject. It needs a "last seen" date on the unit progress (a small migration in the user database). *Done on 2026-11-05 with no migration: "last seen" is the newest of what is already stored (a unit finished, a lesson read to the end again, any answer to the topic's tasks); the "Régen látott anyag" card (the oldest first, at most 5, each with its subject) and "Átismétlés", a round of 5 tasks of the topic recorded as `REVIEW`; due after 1 day for the beta (see 12.7).*
* [x] **9a.6. Practice tab:** the screen from the approved prototype with 9a.2 to 9a.5 in it, replacing the "Hamarosan" screen. *Done on 2026-11-05: the four cards (free practice, mistakes, weak and strong topics, old material); on a tablet and a desktop in two columns as in the prototype (the free practice and the mistakes on the left, the scores and the old material on the right); the prototype's notes brought in line with what was decided during 9a.*

---

## Milestone 10: Hungarian content

The lessons and tests of the Hungarian subject, written in the private content repository on the finished engine. It is written in parallel with Milestones 11 to 16. Only Hungarian first: the other subjects follow in Milestone 17, once the app is stable.

* [x] **10.1.** Decide what the planned "30 main topics" means: 30 topics or 30 lessons. *Was 10.1 (open question).* *Done on 2026-11-06 (the details are in the private content repository).*
* [x] **10.2. Content viewer** *(done on 2026-11-06)*: a small read-only tool (Go, `tools/content-viewer`) that shows the lessons, tasks and tests of `content/` in the browser, so a written topic can be reviewed quickly. It reads the files on every page load and is not a validator (the pipeline's export is).
  * [x] **10.2a. The viewer:** the topic tree, lessons (pages and boxes), all seven task formats with the right answers, the tests, the report (mix of formats against the target, missing explanations, long pages, tests that do not fit) and the overview of all topics.
  * [x] **10.2b. Documentation and workflow:** the viewer's README, the link in `AGENTS.md`, and the viewer step in the content guide.
* [ ] **10.3.** Hungarian units, written in the private content repository, as parts, topics (a work, an author, a language area), lessons and tests. If reading tasks need a passage, the passage model and its view are added here. *Was 10.2 (and 10.1 before).*
* [ ] **10.4.** Enough content for the alpha: the testers should be able to study for a few weeks. The owner decides how many topics that is.

---

## Milestone 11: Groundwork for the alpha

Everything that has to be settled before the first testers install the app. After 11.1 every change to the user database is a migration.

* [ ] **11.1.** Review of the user database tables; then the schema is frozen. *Was 11.1.*
* [ ] **11.2. Open items before testers:**
  * Bug fixes from phone tests *(was 12.1)*: after a change of the teaching language the subjects are not asked again; Google sign-in on desktop is not tried yet (it works on Android); sometimes the interface comes up in Slovak although the app language is Hungarian (seen on 2026-11-05, not every time).
* [ ] **11.3. Release key and Play Console:** an own upload key instead of the debug key (6a.4) and Play App Signing; the SHA-1 fingerprints of both keys added to the Google OAuth client, so Google sign-in works in builds from the Play Store; find out what Google asks for the testing tracks (number of testers and days, privacy policy address, data safety form).
* [ ] **11.4. Crash reports:** Sentry (Android and desktop) and a "report a problem" button in the settings. No personal data in the reports. No separate analytics tool: the learning data comes from the sync (12.3).
* [ ] **11.5. Drafts of the terms of use and the privacy notice,** covering accounts, Supabase, Resend, Google sign-in and crash reports, published on `maturimate.madovygabor.com` and linked from the consent text on the account step. The legal review comes in 16.1. *Was part of 12.6.*
* [ ] **11.6. Supabase and e-mail operations:** check the plan before the alpha (whether an inactive free project is paused, backups, `pg_dump` routine) and the e-mail limits (Resend and Supabase Auth) for the number of testers.

---

## Milestone 12: Sync and content download

All of it before the alpha, so the testers use the real flow: sign-up, sync, download.

* [ ] **12.1. Sync design (plan first, no code before approval):** which tables travel and in which direction, conflicts when one account is used on several devices, checks on the server instead of trusting the phone, the time zone and the streak day, how a guest's progress merges with an existing account (see 12.5).
* [ ] **12.2. Open Supabase migrations:** apply the written migrations (7.0a lesson pages, 7.5 `attempt_answer`, 8.1 activity seconds, 9a.2 attempt types) on a Supabase branch first, then on the live project.
* [ ] **12.3.** Sync of user data between the phone and Supabase. With it: the reset of the progress (9.4b) removes the same rows on the server, the sign-out no longer has to remove the local data without a copy, the settings' "Szinkronizálás" row comes alive, and the name edited in the settings goes to the server. *Was 11.2.*
* [ ] **12.4. Content path and download:** the exporter builds the starter pack (the first lesson of each subject, with the topics above it) bundled in the app and the full content, one pack per subject, downloaded after sign-up and for later updates: a private Storage bucket readable only by signed-in users, signed links, download to a temporary file, hash check, then swap in one step; on failure the current file stays. The manifest gets a `kind` (`FULL` or `STARTER`) and an explicit schema number; a full file of the same version replaces a starter, never the other way round. Large downloads ask before using mobile data. `bundleContent -PcontentKind=full|starter` (development builds use the full file). *Was 11.3 and 11.4.*
* [ ] **12.5. Required sign-in after the first lesson:** no "later" button. The local move of the guest's rows exists since 5.12 (`LocalUserProvider.adoptAccount`). Signing in to an existing account keeps that account's data and adds the guest's progress where the account has none. *Was 11.5.*
* [ ] **12.6. Account deletion** (required by GDPR and the app stores), with a web link for the stores. *Was 11.6.*
* [ ] **12.7. Exam dates:** the app computes the date of the written exams from the rule of the second Monday of March (`ExamDates.secondWeekWeekday`); here it takes the official date when it is published, through the same sync as the content and the settings. *Was 11.7.*

---

## Milestone 13: Code review and optimization

Before the alpha, on the finished sync code.

* [ ] **13.1.** A manual and an AI review (`/code-review`) of the code module by module, with the bugs found fixed.
* [ ] **13.2.** Optimization: database calls, recomposition, memory, start-up time.
* [ ] **13.3. Tests:** Android device tests in CI (waiting for an emulator since 6a.5), a test that the phone database upgrades from the version of the alpha, the shared profile flow in the coverage.
* [ ] **13.4.** Dead code and documentation check.

---

## Milestone 14: UI refinement and animations

Before the alpha. The screens were built quickly and work; here they get polished.

* [ ] **14.1. Screen checklist:** `docs/screen-checklist.md`, one row per screen: works, empty state, error state, phone and tablet and desktop, dark mode, accessibility, animation.
* [ ] **14.2.** Refinement screen by screen against the design system: spacing, type, states.
* [ ] **14.3.** Animations. *Was 12.2.*
* [ ] **14.4.** Accessibility checks. *Was 12.2.*
* [ ] **14.5.** Review of the Slovak and Hungarian texts. *Was 12.3.*

---

## Milestone 15: Alpha

About 20 acquaintances, several devices, the Hungarian subject, with sync and download, through a Play Console testing track. Target: early spring 2027 (the content of 10.3 decides).

* [ ] **15.1.** Alpha release. Test the reminder on phones of several makers (battery savers often stop alarms).
* [ ] **15.2.** Crash reports and feedback, fixes during the alpha.
* [ ] **15.3. Exit to the closed beta:** every row of the screen checklist done, no open severe bug, about 99% crash-free sessions, the feedback processed.

---

## Milestone 16: Closed beta

* [ ] **16.1.** Legal review of the terms of use and the privacy notice, with real document versions instead of "1.0". *Was part of 12.6.*
* [ ] **16.2.** Closed beta of the Hungarian subject: more people, the full flow. *Was 12.4.*
* [ ] **16.3.** Fixes from the beta and a second manual pass.

---

## Milestone 17: More subjects

Each subject is a downloadable pack (12.4), so adding one changes the content, not the app. The two exceptions need engine work. Runs in parallel with Milestone 18.

* [ ] **17.1. Slovak:** new content and the grammar task types. *Was 13.1.*
* [ ] **17.2. English (B1/B2):** level filtering, cloze tasks; listening tasks with own recordings or transcripts (engine work). B1 and B2 are two subjects (`ANJ_B1`, `ANJ_B2`). *Was 13.2.*
* [ ] **17.3. Mathematics:** formula rendering, the bilingual text model (one id, a Hungarian and a Slovak text), numeric answers with comma and dot normalisation (engine work). *Was 13.3 and 12.3 of the older plan.*

---

## Milestone 18: Open beta

* [ ] **18.1. Abuse protection for sign-up:** invisible CAPTCHA (hCaptcha or Cloudflare Turnstile) on sign-up and password reset, a review of the auth rate limits, a cleanup of unconfirmed e-mail sign-ups in Supabase Auth. Synced progress is checked on the server, not trusted as sent. Optional, if abuse is seen: Play Integrity before a content download. *Was 12.5 and 11.3a.*
* [ ] **18.2.** Gradual opening: by invitation first, then open; the finished subject packs join as they are ready.

---

## Milestone 19: Release

Target: September 2027, for the school year of the next graduates. (The students of the school year 2026/27 are not a target: the app should not be released half-ready.)

* [ ] **19.1. Pre-release checklist:** set the days before a finished topic is offered for review back to 14 (`ReviewList.DUE_AFTER_DAYS`, 1 for the beta so it can be tried the next day) and check the length of a review round (`ReviewList.ROUND`); raise the number of tasks seen before a topic gets a score (`TopicScores.MIN_SEEN`, 5 for the beta; the plan was 20) to fit the real size of the topics; decide whether the extra analytics are paid. The beta values move behind a build flag so they cannot be forgotten. *Was 12.7.*
* [ ] **19.2.** Public release. *Was 12.7.*

---

## Milestone 20: Other platforms

* [ ] **20.1. Platforms:** desktop (a target already exists), then iOS, then web. *Was 13.4.*

---

## Milestone 21 (V2): Official exam content

Only after NIVAM gives permission. The request is planned for when the app works and there are real usage numbers to show. If it is refused, the app continues on its own content.

* [ ] **21.1.** Written permission covering papers, answer keys, audio, offline caching, attribution and free or paid use. *Was 14.1.*
* [ ] **21.2.** Official papers and listening tasks with audio, loaded as new rows. The exam table returns as a database migration. *Was 14.2.*

---

## Milestones of the earlier plan

The earlier plan digitised the official papers first: database core with a JSON seeder, vertical slices per exam subject (`HU_MJL`, `SK_SJL`, `AJ_B1`, `AJ_B2`, mathematics), an exam runner with timer, grading and exam history. The work for the first step was on the `database` branch, now deleted. These milestones are not part of V1; what survives is reused above (the subject order, the exam-style practice mode, grading and history).
