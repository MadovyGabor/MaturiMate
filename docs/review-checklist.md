# Review checklist: what to check and what good looks like

Used in the steps of [review-procedure.md](review-procedure.md): the owner's code reading (step 4), the AI review (step 2) and the final review (step 8) of every slice in [review-slices.md](review-slices.md). Take the groups that fit the slice; a screen slice (version A) uses groups 1 to 12 and 14, the backend slices (version B) use groups 1 to 4, 6 to 9, 13 and 14. Group 15 (CI) is for the closing round and for any slice that changes a workflow. Part B at the end is the security list of this app; every slice uses its rows.

Each point says **what to look at**, and in *italics* **how to check it** or what a good result is.

---

## 1. Layers and structure

* **Screens talk only to ViewModels, ViewModels only to `domain/` interfaces.** *Good: no `data/` import and no SQLDelight class in a screen or a ViewModel.*
* **Generated SQLDelight classes stay in `data/`.** *Check: search for the generated package in `presentation/` and `domain/`; there should be none.*
* **`commonMain` has no Android or JVM import.** Platform code goes through `expect`/`actual` or an interface bound in the platform's Koin module, and stays small.
* **Every class is in the right place** (`presentation`, `domain`, `data`; reusable components in `core/presentation/uiComponents/`, named `App…`). A feature does not reach into another feature's `data/`.
* **"Who may import whom" is a test** where it can be (a short architecture test), so the rule outlives the review.
* **Koin modules:** every binding is used, no class builds its own dependency.

## 2. Code smells

* **Size:** a function over about 40 lines, a class over about 300, a screen file that holds several screens (`TestScreen.kt`, `SettingsScreen.kt`). *Good: split by meaning, not by line count.*
* **Duplication:** the same logic in two places (a date calculation, a mapping, a rule such as "round passed"). One place, tested.
* **Magic numbers and strings** (a limit, a delay, a column name, a route). *Good: a named constant next to the rule that owns it.*
* **Long parameter lists, boolean flags that change behaviour, `Any` or `String` where a type fits.**
* **Names say what and why;** no `data`, `info`, `manager`, `helper`; comments explain *why*, not *what*.
* **Needless abstraction:** an interface with one implementation and no test double, a layer that only forwards. Remove it, or say why it stays.
* **Dead code:** unused functions, parameters, strings, resources, routes, commented-out code. *Check: the IDE's unused warnings, and a search for each resource key.*

## 3. Correctness

* **The rule is written once and says what the docs say** (streak, round, pass mark, review due date, merge rules). Compare with `content/DECISIONS.md` and the roadmap when in doubt.
* **No `!!`,** no unchecked cast, no `first()` on a list that may be empty, no index access without a check.
* **Edge cases:** empty lists, zero tasks, a topic without lessons, the first and last item, a very long name, a leap day, midnight, a change of time zone, a clock set back.
* **Ids never change and content rows are never deleted,** only switched off. *Check: nothing in the app or the pipeline deletes a content row.*
* **Time:** one time zone for the day logic; times stored as UTC epoch milliseconds; no `Clock.System.now()` hidden deep in a function that a test cannot control.
* **Every state of a screen has an owner:** loading, content, empty, error, offline. No state that can only be reached by a bug.

## 4. Errors and offline

* **Every call that can fail returns a result type** (`OperationResult`), and the screen shows the failure in words the user understands, in both languages.
* **Offline first:** the screen works with no network; a network call never blocks it. *Try: airplane mode on every screen of the slice.*
* **Expired session, wrong code, a refused password, a server error, a timeout:** each one has a path and a message.
* **No swallowed exception** (`catch (e: Exception) {}`), and `CancellationException` is always rethrown.
* **Retry is safe:** repeating an action (a sync, a download, a sign-up step) cannot duplicate or corrupt data.
* **A failure leaves the old state intact** (a failed download keeps the current content file; a failed sync keeps the local rows).

## 5. Concurrency and state

* **Coroutines have an owner and a lifecycle** (`viewModelScope`, an injected scope); no `GlobalScope`; the dispatcher is injected, not written in the code.
* **A double tap or a fast second action cannot start the work twice.** *Try: tap a button twice quickly.*
* **No race between a read and a write** (read then write outside one transaction). *Good: the check and the write are one SQL statement or one transaction.*
* **`StateFlow` and `SharedFlow` used on purpose:** state is a `StateFlow`; one-time events (a snackbar, a navigation) are not replayed after a rotation.
* **State survives:** rotating the phone, resizing the desktop window, the app sent to the background and the process stopped, the back button, a language change in the middle of a flow.
* **One user database call at a time** where the code relies on it (see 6a.1); no call from a screen that bypasses the repository.

## 6. Security

The places where this app can be attacked are in Part B at the end of this file. The general rules:

* **No secret in the repository,** in a log, in a test or in a screenshot: connection strings, secret keys, tokens. The app holds only public values (`core/config/`).
* **The server does not trust the phone.** Every rule that matters (who owns a row, how many downloads, what a sync may write) is checked on the server, by row level security or in the function. *Ask at every endpoint: what can a modified client send?*
* **Inputs are checked** at the edge: lengths, ranges, formats, the shape of a JSON.
* **No personal data in logs,** crash reports or analytics (name, e-mail, the exam date with a name).
* **Account data never goes through the sync** (password, e-mail, identities); sign-in tokens are stored only where the platform keeps secrets.
* **No official exam content** (NIVAM papers, answer keys, audio) anywhere in the repository or the app.
* **Dependencies:** no known vulnerability, no abandoned library (checked in the closing round).

## 7. Privacy (GDPR)

* **What leaves the phone** is only what the sync and the account need; the guest's data stays on the phone.
* **Export and deletion cover the slice's data:** a new table or column is in `export_my_data()` and removed by `delete_my_account()`.
* **Consent:** what the user agreed to is recorded with the version of the text, and withdrawal works.
* **Backups and archives** keep what the privacy notice says, for as long as it says.

## 8. Database (phone and server)

* **Migrations:** a change to a synced table is a migration pair (a phone `.sqm` and a Supabase migration); an applied migration is never edited. *Check: `SchemaMigrationTest` and `UpdatedAtGuardTest` are green.*
* **Every UPDATE of a synced table sets `updated_at`.**
* **Queries:** no query in a loop (N+1), an index for every lookup and foreign key, a transaction around a group of writes, no `SELECT *` of columns the screen does not use (lesson text in a list).
* **Server row level security:** on for every table; each policy tested in `supabase/tests/rls_test.sql` for the allowed and the forbidden case.
* **Server functions:** `SECURITY DEFINER` only where needed, with a fixed `search_path`; they check `auth.uid()`; not callable by roles that should not call them. *Check: the Supabase advisors are clean.*
* **The server schema and the phone schema use the same names;** a type difference is on purpose and written down.

## 9. Performance

* **Measure first.** A change is justified by a number: recomposition counts (Layout Inspector), the profiler, the time to the first screen, the size of a payload.
* **Compose:** no heavy work in a composable; `remember` and `derivedStateOf` where a value is computed from state; stable parameters (no needless `List` that changes identity); keys in lazy lists; state read as low in the tree as possible; `collectAsStateWithLifecycle` on Android.
* **Lists and images** are lazy; a long list is not built in one go.
* **Database calls per screen:** the home screen takes one call for the whole screen (`HomeOverview`); look for screens that make many small calls.
* **Memory:** no leaked scope or listener; no large object held after leaving a screen.
* **Start-up and a weak phone:** time to the first usable screen; scrolling and animations on an old, cheap device, not only the owner's.

## 10. UI and the design system

* **Theme values only:** `MaterialTheme.colorScheme`, `typography`, `shapes`, `extendedColors`, `extendedTypography`, `spacing`. *Check: a search for `Color(0xFF`, `.sp`, `.dp` literals for corners and spacing in screens finds nothing.*
* **A new colour or style is in `tokens.json` first;** a reusable component is an `App…` component with a page in `docs/design-system/components/`.
* **All states are designed:** loading, empty, error, offline, success; disabled and pressed states of controls.
* **Layouts:** phone, tablet and desktop (breakpoints from the tokens), portrait and landscape, light and dark.
* **Motion:** animations have a purpose, are short, and respect the system setting to reduce motion.
* **Consistency:** the same thing looks and behaves the same everywhere (a button, a card, a dialog, a snackbar).

## 11. Accessibility

* **Screen reader:** every control has a meaningful label or content description; reading order follows the layout; decorative images are hidden.
* **Font scaling** to the largest system size: nothing cut off or overlapping.
* **Contrast** meets the design system's targets in light and dark; meaning is never carried by colour alone (right and wrong answers also have an icon or a word).
* **Touch targets** at least 48 dp; focus order and keyboard use on the desktop; visible focus ring.

## 12. Texts and languages

* **Every key exists in both languages:** Slovak in `values` (the default), Hungarian in `values-hu`. *Check: the two files have the same keys.*
* **No text written in the code,** including tab labels, error messages and the reminder notification.
* **Texts read well:** plurals and numbers correct in both languages, the length fits a button in the longer language, the same word for the same thing everywhere.

## 13. Tests and documents

* **A new or fixed rule has a test;** a bug fix starts with a test that fails. *Check: `./gradlew :database:jvmTest :tools:content-pipeline:test :shared:jvmTest`.*
* **Tests are not tied to the implementation:** they check behaviour, use test doubles at the borders, and do not depend on the real clock or the network.
* **Coverage stays at 80 %** (Kover in CI).
* **Do not bundle the fixtures into the app's content** when running tests (see the note on `bundleContent`).
* **Documents match the code** after the slice: `docs/ARCHITECTURE.md`, the design-system pages, the runbooks, the roadmap checkbox and `content/DECISIONS.md` for anything decided on the way.
* **Commits** follow Conventional Commits, one change per commit, on the slice's own branch, never on `main`.

## 14. File structure and test layout

Measured on 2026-11-13: 644 tests in 90 test files; the largest files are `ContentValidatorTest` (35 tests), `TestScreenTest` (26), `PracticeScreensTest` and `TestSessionViewModelTest` (20 each). In the app code `core/presentation/uiComponents/` holds 29 files in one folder, `features/learning/presentation/` 12 and `features/learning/domain/` 11, mixing several topics each.

**Folders and files**

* **One folder, one idea.** A folder with more than about 8 files is grouped into subfolders by meaning; a folder with a single file is merged into its parent. *Candidates seen on 2026-11-13, to be decided in the slice that owns them:*
  * `uiComponents/` (29 files, flat): group by kind, for example inputs and buttons, choices and cards, progress and rings, feedback and dialogs, the parts of the task screens, settings, activity and statistics. The names stay `App…`.
  * `features/learning/presentation/`: the home screen lives here but is its own feature (`home`); the rest splits into `subjects`, `topic`, `lesson` and `test`.
  * `features/learning/domain/`: group into the course (`LearningCourse`, the models), the rounds (`RoundSource`, `RoundPlanner`, `TestRecorder`, `AnswerChecker`) and the tracking of study (`StudyTracker`, `StudyClock`, `RecentActivities`, `HomeOverview`).
  * `features/profileDetails/`: the settings and the profile are two features (`settings`, `profile` with its history).
  * `features/auth/presentation/`: separate the account (sign-up) and the sign-in files.
* **A feature folder holds one feature,** with the same inner layers everywhere (`presentation`, `domain`, `data`). Presentation depends on domain, data implements domain; no cycle between packages.
* **One main class per file,** the file named after it; a file with several unrelated classes is split. A file over about 300 lines is looked at (`SettingsScreen.kt` 605, `TestScreen.kt` 572, `HomeScreen.kt` 427, `ProfileOverviewScreen.kt` 412): split by meaning, not by line count.
* **How to move:** with the IDE's move refactoring (it fixes packages and imports), in **one commit that changes no logic** (`refactor: move … into …`), after the owner's reading (step 4) and before optimization and polish, so the later steps work on the final places. The tests move with the code.
* **After a move:** `docs/ARCHITECTURE.md` shows the new tree; resource keys and stored ids are not renamed; the build and all tests are green before the commit.

**Tests**

* **Tests mirror the code:** the same package, the name `<Class>Test` for `<Class>`, moved together with it. Kinds are told apart by the suffix and the package: `…Test` (rules and domain), `…RepositoryImplTest` (against an in-memory database), `…ViewModelTest`, `…ScreenTest` (Compose UI), schema and migration tests, pipeline tests.
* **Group by behaviour, not by accident.** A test file has about 5 to 25 tests. Over about 25, split by behaviour (for `TestScreenTest`: a file per task format) or by nested groups where the framework allows; under 3, merge into the neighbouring test of the same class. The aim is neither one huge class per module nor one file per test.
* **Names say what is checked** (`does X when Y`, in one style across the project); no `test1`, no names that repeat the class name.
* **Shared helpers live once:** fakes, builders and fixtures in one `testutil` package per module; a fake copied into several test files is moved there.
* **Quick and slow apart:** Compose UI and database tests are recognisable by name or package, so the quick check can run without them.
* **One thread for an in-memory database in a test.** Repositories get `Dispatchers.IO.limitedParallelism(1)` (or a shared test helper), never `Dispatchers.Unconfined`: a ViewModel works on the main dispatcher while the test thread polls, and two threads on one JDBC connection time out on a slow CI run. About 26 test files still use `Unconfined`; a test file that fails twice on CI with no reason is moved at once, the rest in the closing round (13.16).
* **Independent tests:** no order dependence, no real clock, no network, no shared mutable state.
* **The count does not fall without a reason** after a move or a split (644 on 2026-11-13); coverage stays at 80 %.

## 15. CI: where to find what

The aim: a red mark in GitHub tells at once **which part** broke, and the same part can be run on the owner's machine with one command. Read from `.github/workflows/ci.yml` (181 lines, five jobs) on 2026-11-13.

**What it looks like now**

| Job (name in GitHub) | What it runs | Local command |
| :--- | :--- | :--- |
| Database (SQLDelight) | the database module's tests, coverage (80 %) | `./gradlew :database:jvmTest` |
| Content pipeline (validator, exporter, PostgreSQL importer) | **the server SQL tests too** (all migrations, `rls_test.sql`, `sync_test.sql`), then the pipeline's tests, on a throwaway PostgreSQL 16 | `psql` over `supabase/` files; `./gradlew :tools:content-pipeline:test` |
| Worker (content link) | `npm ci`, typecheck, tests of `workers/content-link` | `npm run typecheck`, `npm test` in that folder |
| App: Frame, Onboarding, Auth, Settings, Reminder, Profile, Other (seven parallel jobs) | bundles made-up test content, then the app's tests of that group (the table `testGroups` in `shared/build.gradle.kts`) | `./gradlew :shared:jvmTest -PtestGroup=<group>` |
| App: Coverage | bundles made-up test content, the whole suite once, coverage (80 %); its test count is compared with the sum of the groups | `./gradlew :shared:koverVerify` |
| App: Desktop build | the desktop jar | `./gradlew :desktopApp:jar` |
| Android build | the debug APK | `./gradlew :androidApp:assembleDebug` |
| CI result | green only when every job above passed and the groups ran exactly the tests of the whole suite; the single check meant to be required | none (reads the other jobs) |

The test results of the first two and of the App group jobs are published as separate checks (*Database tests*, *Content pipeline tests*, *App: <group> tests*; the report check carries "tests" so it does not share a name with its job) and as tables in the run summary; coverage lines go to the summary. `backup.yml` is separate.

**Do not make "CI result" a required check yet.** Pushes that change only documentation start no run (paths-ignore in `ci.yml`), so a required check would wait forever on them. Solve that case first (for example an always-running tiny workflow, or path filtering inside the jobs); not solved yet.

**What to check and tidy**

* **One job, one area, a name that says it.** *Found:* the server SQL tests (migrations, row level security, sync) are a step inside the job named "Content pipeline", so a red RLS test shows up under the wrong name. Give them their own job and check, for example *Server (migrations, RLS, sync)*, on the same kind of PostgreSQL service.
* **A naming scheme for jobs and checks:** `<Area>: <what>`, in the order the parts are met: *Server*, *Database*, *Pipeline*, *Worker*, *App*, *Android*. The job name, the check name (`check_name`) and the report name agree, so the name in the failed list is the name in this table.
* **The App job does several things** (bundle the test content, the tests, coverage, the desktop build). Decide whether the desktop build is its own job, so a failed build and a failed test are not the same red mark.
* **The test report is readable:** with 644 tests the table lists every passed test (`include_passed: true`); check whether the report action can group by suite or class, so a failure is found by feature (matching the folders of group 14), and whether passed tests should be left out of the table and kept only in the artifact.
* **Every job has a timeout, a cache and `if: always()` on its report step,** so a failed run still shows what failed.
* **Which job watches which path:** a change only in `workers/` need not build the app, and a change only in `supabase/` need not run the Android build. Path filters per job if the run time or the cost grows; not before.
* **What is missing and could be added** (decide in the closing round): Android device tests (waiting for an emulator since 6a.5), a secret scan, a check of dependencies for known vulnerabilities, a check that every string key exists in both languages, an architecture test run (group 1), and a check that the roadmap and the docs links are not broken.
* **A map for a red mark:** this table (or a short `docs/ci.md` made from it) is kept up to date when a job changes, so "red" leads to the folder and the command without searching.
* **Secrets and permissions:** `permissions` stays minimal (`contents: read`, `checks: write`); actions are pinned (tag or SHA); `backup.yml` uses only its own secrets and cannot run from a fork.
* **Run time and cost:** the slowest job decides how long a push waits; look at the times once per slice and keep the Gradle and npm caches working.

---

# Part B: Security, where this app can be attacked

Used in every slice, with the rows marked for it (the last column of each table); the general rules are in group 6 above. Written on 2026-11-13 from reading the manifest, the Worker and the content download, and from the design notes.

**These are candidates to check, not found holes.** Each row says where to look and what a good answer is. When a check is done, write the result in the slice's notes file and, if a decision follows, in `content/DECISIONS.md`.

The threat model in one sentence: the likely attacker is a **student or a script with a modified client** who wants other users' data, free or unlimited content, or to break the service; a lost or shared phone and a leaked secret come next. The code is not a secret from a determined person (see "Accepted, on purpose").



## B1. The server trusts the phone

The app holds only public keys, so everything the user can do is decided by **row level security (RLS)** and the server functions. A hole here exposes every user.

| # | Risk | Where | What to check | Slice |
| :-- | :--- | :--- | :--- | :-- |
| 1.1 | A table without RLS, or a policy that is too wide (`using (true)`) | `supabase/migrations/`, `rls_test.sql` | RLS on for every table; each policy has a test for the allowed and for the forbidden case, including a **second user** reading or writing the first user's rows. | S15 |
| 1.2 | A user gives themselves a role (curator, admin) | `user_role`, policies | Only roles 1 to 3 can be set by the user (a preference, never a permission); 4 and 5 only by the server. Try it with a modified client. | S15 |
| 1.3 | A `SECURITY DEFINER` function that does too much | `sync_user_data`, `reset_progress`, `delete_my_account`, `export_my_data`, `register_content_download`, `save_sign_up` | Fixed `search_path`; checks `auth.uid()` and works only on that user's rows; not callable by roles that should not call it (`revoke` from `public`, `anon`); no SQL built from strings (`format`/`execute` with user input). The Supabase advisors are clean. | S14, S15 |
| 1.4 | The sync accepts invalid or hostile data | `sync_user_data` | Bad rounds rejected whole; future times corrected; ids and foreign keys checked; **a limit on the size and number of rows** of one call (a huge payload under the advisory lock can block a user's sync, or load the database). | S14 |
| 1.5 | A user writes progress of content they never reached (inflated stats) | sync merge rules | Rules "furthest wins" and "answers added" are checked against what the content allows; not a leak, but it breaks the statistics. Decide how much to trust. | S14 |
| 1.6 | The deletion or the export of the wrong account | `delete_my_account`, `export_my_data` | They act on `auth.uid()` only, take no user id as input; the deletion refuses without the Vault secret `consent_hmac_key`; the export returns only the caller's rows. | S15 |
| 1.7 | The consent archive is readable or writable by the app | `consent_archive` | Closed to the API (no policy, no grant); tested. | S15 |

## B2. Accounts and sign-in

| # | Risk | Where | What to check | Slice |
| :-- | :--- | :--- | :--- | :-- |
| 2.1 | Guessing the 6-digit code of a sign-up or a password reset | Supabase Auth, `AccountViewModel`, `SignInViewModel` | The attempt and sending limits of Supabase Auth are set low; the code expires; a wrong code never reveals more than "wrong". | S3 |
| 2.2 | Finding out whether an e-mail has an account | sign-up, sign-in and reset messages | The same answer for a known and an unknown address where the service allows it. | S3 |
| 2.3 | Weak or leaked passwords | Supabase Auth setting | The leaked-password protection is off since 11.8 (15.4 of the roadmap): switch it on and set a minimum length before testers come. | S3 (and 15.4) |
| 2.4 | The session on the phone: where it is stored | `SupabaseClientFactory.kt`, `Auth` install | The session (refresh token) must not be in a plain file readable by other apps or backed up to the cloud (6a.4 excluded it from the backup; check that this still holds). On desktop: where the file is, who can read it. *Checked 2026-11-14:* on Android the session is in the shared preferences (private, excluded from backup and transfer); the token of an offline sign-out is in the same store. On desktop supabase-kt keeps it in Java Preferences (on Windows the registry, plain text, readable by the same user): accepted on purpose, DECISIONS 2026-11-14. | S1, S3 |
| 2.5 | Sign-out leaves a live refresh token | `PendingRevocation`, offline sign-out | The old token is revoked when online again; a signed-out phone keeps no session and no other account's rows (two accounts on one phone, 6a.2). | S3, S5 |
| 2.6 | Google sign-in redirect intercepted (desktop) | `SupabaseClientFactory.jvm.kt` (fixed local port) | The code flow uses PKCE; the local callback accepts one answer and closes; the redirect URL list in Supabase is exact. Android uses the system Credential Manager. | S3 |
| 2.7 | A guest's data merged into the wrong account | `adoptAccount`, the guest-merge sync | The merge happens only after a successful sign-in, never overwrites newer account data, and a second account on the phone cannot take over the first's rows. | S3, S14 |

## B3. Content and downloads

| # | Risk | Where | What to check | Slice |
| :-- | :--- | :--- | :--- | :-- |
| 3.1 | Downloading content without an account, or without the subject | `workers/content-link/src/index.ts`, `register_content_download` | The Worker verifies signature, expiry, issuer and audience of the token; the SQL function checks that the user studies the subject and the daily limit (3 per subject per day) under RLS. A test for a missing, expired and foreign token exists. | S13 |
| 3.2 | A link that is shared or kept | presigned R2 link | 10 minutes only; read-only key; the bucket is private and has no public URL; the object path comes from the database, never from the request. | S13 |
| 3.3 | A tampered or half-downloaded content file installed | `ContentUpdater`, `InstalledContentPacks`, `WorkerPackSource` | The installer checks **size and SHA-256 against the release the server announced in the sync**, not against the link answer; the new file is swapped in one step and the old one stays on any failure; a file of a lower version never replaces a higher one. The whole pack is read into memory (`ByteArray`): check the size limit and the behaviour on a weak phone. | S13 |
| 3.4 | Plain HTTP or a wrong host | `ContentConfig.LINK_URL`, the Ktor client | Only `https`; no "trust all" certificate code (none found on 2026-11-13); the Worker address is fixed in the config. | S13 |
| 3.5 | Malicious content text (markup, huge text) | the app text format, `AppRichText`, the lesson screen | The renderer handles only its own format (`**bold**`, `*italic*`, `[[n]]`), no HTML, no links that open from content; a broken text does not crash the screen. | S7 |
| 3.6 | Official exam content or copyrighted material ends up in the app | `content/`, packs | Nothing from NIVAM in the repository or the packs; provenance on every task (validator). | S13, S16 |

## B4. The phone and the desktop

| # | Risk | Where | What to check | Slice |
| :-- | :--- | :--- | :--- | :-- |
| 4.1 | An exported receiver run by another app | `AndroidManifest.xml`: `BootReceiver` has `exported="true"` | Only the system should start it. It must check `intent.action` is `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` and do nothing else (a hostile app can send an explicit intent). `ReminderReceiver` is `exported="false"`: keep it so. | S4 |
| 4.2 | Backups copy data off the phone | `allowBackup="true"`, `backup_rules.xml`, `data_extraction_rules.xml` | Read on 2026-11-13: the shared preferences (where the session is kept) and `content.db` are excluded from cloud backup and device transfer; the user database stays in the backup. Check that nothing else secret lands in a backed-up file, and decide on purpose that the progress may be backed up (it also goes into the privacy notice). *Decided 2026-11-14:* the user database stays in the cloud backup (DECISIONS 2026-11-14); the privacy notice says so (note in `runbooks/backup.md`, step 15.3). | S1 |
| 4.3 | The copy of the user's data stays on the phone | `FileProvider` (`export_paths.xml`), the export in the settings | The file is in the cache, shared only through the share sheet with a one-time grant, and deleted after use or on the next start; the provider is not exported (it is `false`). | S5 |
| 4.4 | Debug key, debug build or test code in a release | `build.gradle`, signing | The release is signed with the upload key (15.1), not the debug key; R8 is on (4.9 MB since 6a.4); no debuggable flag; no test endpoint. | S16 |
| 4.5 | Logs that carry personal data | `println` in `ContentDatabaseProvider.kt` (the swap error message) | No name, e-mail, token or exam date in logs; the error message there can hold a file path; Sentry (15.2) scrubs personal data. | S1, S13 |
| 4.6 | The local databases are plain files | `content.db`, the user database | Accepted: the phone's content and progress are not secret (see "Accepted"); the session is the only secret and is handled in 2.4. | S1 |
| 4.7 | The reminder shows the streak on the lock screen | `ReminderMessage` | Minor: it shows no name or exam date; decide whether the text is fine on a lock screen. | S4 |
| 4.8 | An intent or a deep link opens a screen it should not | manifest, `Route` | There are no deep links today (only the launcher); any added later must not skip the sign-in or the onboarding. | S1 |

## B5. The pipeline: secrets and supply chain

| # | Risk | Where | What to check | Slice |
| :-- | :--- | :--- | :--- | :-- |
| 5.1 | A secret in the repository or in a log | `.env`, `local.properties`, CI logs, test fixtures | Nothing secret in Git history (a scan once); only the public key and the project URL in `core/config/`; `package-lock.json` and tool folders are not carrying tokens. | S16 |
| 5.2 | The backup workflow's secrets | `.github/workflows/backup.yml` | The `age` private key never in GitHub; only the public key; the R2 write key limited to the backup bucket; the database connection string a GitHub Secret; the workflow cannot be triggered from a fork. | S15 |
| 5.3 | The backup holds personal data | R2 backups, 30 days | Encrypted with `age`; the privacy notice says "up to 30 days after a deletion" (15.3); the restore test has been done. | S15 |
| 5.4 | Third-party actions and libraries | `.github/workflows/`, Gradle, `workers/content-link/package.json` | Action versions pinned (tag or SHA); dependencies updated and checked for known vulnerabilities; no abandoned library. | S16 |
| 5.5 | The private content repository leaks | `content/` (gitignored) | The public repository has no real lessons; CI never sees them; access to the private repository is limited. | S16 |
| 5.6 | The Worker's secrets | Cloudflare | Read-only R2 key for links; the `SUPABASE_JWT_SECRET` is set only if the project still uses the old signing key; no secret in `wrangler` config in Git. | S13 |

## B6. Abuse and availability

| # | Risk | Where | What to check | Slice |
| :-- | :--- | :--- | :--- | :-- |
| 6.1 | Mass sign-ups and e-mail abuse | Supabase Auth, the e-mail service | Rate limits set; invisible CAPTCHA before the open beta (18.1); the e-mail service limits fit the number of testers (15.4). | 15.4, 18.1 |
| 6.2 | Download and sync floods | Worker, `sync_user_data` | The daily limit and the Worker's limits; a limit on the size of a sync call (1.4); Cloudflare's free limits and Supabase's connection limits checked for the testers. | S13, S14 |
| 6.3 | A clock set far ahead or back | streak, sync times | The server caps future times; one device's clock cannot make another user's data older or newer. | S14 |

---

## Accepted, on purpose

* **The content on the phone can be extracted** by a determined person. The aim is to stop easy downloading, not to make copying impossible (DECISIONS 2026-10-28). The protection is the private repository, the sign-in, signed links and the daily limit.
* **The publishable key and the project URL are in the app.** That is how Supabase works; all the protection is RLS (section 1), which is why that section comes first.
* **The user's own progress is not encrypted on the phone.** It is not secret from the owner of the phone.

## How to use Part B in a review

1. In step 2 (AI review) ask for the rows of the slice, and for the question "what can a modified client send?" at every endpoint.
2. In step 4 (the owner reads) read the rows marked for the slice next to the code, and tick them off.
3. Anything found is a normal fix (step 3) with a test; a hole on the server is a new migration and a test in `supabase/tests/`.
4. A row that turns out fine is marked "checked" with the date in the slice's notes, so the next review does not repeat it.
