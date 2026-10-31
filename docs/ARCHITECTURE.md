# MaturiMate Architecture

This document describes the architecture of the code in this repository. It was last checked against the code on 2026-10-28, after Milestone 1 (core and database), which is merged into `main`.

For the current status of each part, see the "Current status" section of the [README](../README.md).

> **2026-10-22: change of plan.** The first version uses only content written for this project (short lessons and practice tasks); the official exam papers moved to a later version (see the [README](../README.md)). The exam-centred database of the earlier plan (unmerged `database` branch) is not used; its drivers and Gradle setup were reused.

---

## 1. Technology Stack

| Component | Version (from `../gradle/libs.versions.toml`) | Notes |
|---|---|---|
| Kotlin Multiplatform | Kotlin 2.2.20 | Android target and desktop `jvm()` target |
| Compose Multiplatform | 1.9.1 | Shared UI for Android and desktop |
| Navigation3 for Compose Multiplatform | 1.0.0-alpha06 | Alpha dependency |
| AndroidX Lifecycle (ViewModel) | 2.10.0-alpha07 | Alpha dependency |
| Koin | 4.1.1 | `koin-core`, `koin-compose`, `koin-compose-viewmodel`, plus Android artifacts |
| DataStore Preferences | 1.2.0 | Stores user preferences on all targets |
| kotlinx.serialization | 1.9.0 | Polymorphic navigation routes; `content-manifest.json` (`:database`); content files (`../tools/content-pipeline`) |
| kotlinx.datetime | 0.7.1 | Used for `LocalDate` and time zone handling |
| kotlinx.coroutines | 1.10.2 | `StateFlow`, `MutableSharedFlow`, and the Swing dispatcher on desktop |
| SQLDelight | 2.2.1 | Content and user databases (`:database` module) |
| PostgreSQL JDBC driver | 42.7.4 | `../tools/content-pipeline` only (server copy of the content) |
| PostgreSQL | 17 (Supabase), 16 (Docker) | Server database on Supabase; a local copy in Docker (`../dbServer/docker-compose.yml`) for development and the importer |
| Supabase CLI | 2.122.0 | Schema migrations (`../supabase/migrations`) |

Android targets use `minSdk` 24, `compileSdk` 36, and `targetSdk` 36. The Android target uses `jvmTarget` `JVM_11`. Desktop packaging is configured for DMG, MSI, and DEB formats, but has not been built as part of this document.

---

## 2. Module Layout

Gradle modules:

| Module | Kind | Contents |
|---|---|---|
| `:composeApp` | Kotlin Multiplatform app (Android, desktop) | UI, features, Koin wiring |
| `:database` | Kotlin Multiplatform library (Android, desktop) | SQLDelight schemas of the content and user databases, platform drivers, content installer |
| `:tools:content-pipeline` | Kotlin/JVM command-line tool | Validates the content files; exports `content.db`; imports the server copy into PostgreSQL |

Other folders: `supabase/` (Supabase project settings `config.toml` and the schema in `migrations/`, 27 tables), `dbServer/` (local PostgreSQL in Docker with the same schema and a stand-in for Supabase Auth; see [dbServer/README.md](../dbServer/README.md)), `content/` (lesson and task files: a separate private repository cloned here, gitignored), `docs/` (vision, roadmap and this document).

The app source tree, relative to `../composeApp/src`:

```
commonMain/kotlin/com/madovygabor/maturimate/
├── App.kt
├── core/
│   ├── database/           # ContentDatabaseProvider (installs and opens the content database)
│   ├── di/                 # Koin modules (AppModul.kt, initKoin.kt)
│   ├── domain/error/       # Error.kt, OperationResult.kt
│   └── presentation/       # MainFrame.kt, navigation/, theme/, uiComponents/, utils/
└── features/
    ├── appOnboarding/      # data/, domain/, presentation/
    ├── examTaking/         # ExamWriteScreen.kt (placeholder)
    ├── learning/           # data/, domain/ (no presentation yet)
    ├── profileDetails/     # presentation/ only
    └── subjects/           # presentation/ only
commonMain/composeResources/files/   # bundled content.db + content-manifest.json (generated, gitignored)
androidMain/kotlin/...      # Android actuals (AppModul.android.kt, ...)
jvmMain/kotlin/...          # Desktop actuals (AppModul.jvm.kt, ...)
commonTest/kotlin/...       # One placeholder test (ComposeAppCommonTest.kt)
jvmTest/kotlin/...          # Content provider and repository tests
```

---

## 3. Layering

`features/appOnboarding` has the full `data/`, `domain/`, and `presentation/` split. `features/learning` has `data/` and `domain/` (section 9.3); its screens come in Milestone 6. The other features contain only presentation code:

* `features/profileDetails` contains only `presentation/`.
* `features/subjects` contains only `presentation/`.
* `features/examTaking` contains a single placeholder screen, `ExamWriteScreen.kt`.

`appOnboarding` domain contains `UserPreferences`, `UserPreferencesRepository`, `MaturitaValidator`, and `DataError`. The data layer contains `UserPreferencesRepositoryImpl`, which uses DataStore. The presentation layer contains the onboarding ViewModel and screens.

The subject screens do not use the database yet. Their `Subject` model is in the presentation layer, and there are two such classes (the domain model `features/learning/domain/model/Subject` is a third, separate class):

* `core/presentation/utils/StatickUiSettings.kt:20` defines `data class Subject(...)` with the UI fields and a `dbIdMapping` field. A static map of subjects is defined in the same file.
* `features/subjects/presentation/SubjectListTabScreen.kt:86` defines a second `data class Subject(...)`.

The subject list in `SubjectListTabScreen.kt` is a static `listOf(...)` (line 40) with the subjects MATH, ENGLISH, HUNGARIAN, and SLOVAK.

---

## 4. Presentation Pattern

Presentation follows MVVM with `StateFlow`. A ViewModel holds a private `MutableStateFlow` and exposes it as a read-only `StateFlow` through `asStateFlow()`. The state is an immutable `data class` that is updated with `copy()`. Example: `ProfileViewModel.kt:40` declares `private val _profileUiState = MutableStateFlow(ProfileUiState())` and `ProfileViewModel.kt:43` exposes `val profileUiState = _profileUiState.asStateFlow()`.

The app does not use intents, a reducer, or a single-state MVI loop. Screens call ViewModel methods directly.

One-shot messages (for example, snackbars) go through a singleton `SnackbarController` (`core/presentation/utils/snackbar/SnackbarController.kt:11`), which uses a `MutableSharedFlow` (line 14).

Profile statistics are mock data. `ProfileViewModel.kt:47` calls `loadMockSubjectStats()`, and the values are hard-coded in lines 164-171.

---

## 5. Navigation

Navigation uses JetBrains Navigation3 with `@Serializable` routes. `Route` is a `sealed interface Route : NavKey` (`core/presentation/navigation/Route.kt:8`). Routes include `Route.ExamWrite(val examId: String)` (`Route.kt:21`). The back stack is held with `rememberNavBackStack`.

`NavigationRoot.kt` registers the polymorphic subclasses for `Main`, `AppOnboarding`, and `ExamWrite` (lines 28-32). `ExamWrite` renders `ExamWriteScreen()` (lines 64-66). `ExamWriteScreen` currently displays the text "Exam Write Screen" (`features/examTaking/ExamWriteScreen.kt:25`). The `History` tab shows a placeholder text (`core/presentation/navigation/MainFrame/MainFrameNavigation.kt:61`).

Tapping a subject in `SubjectNavigation.kt` pushes `Route.Main.Subject.ExamList(it)` to the back stack (lines 33-34). `ExamSelectionScreen.kt` reads the subject from the static `SubjectStaticData` (line 44) and has a `// TODO: Use Koin to inject the ViewModel` comment (line 39). The exam list is not connected to a data source, and tapping an exam does not open `ExamWriteScreen` with an exam ID.

---

## 6. Responsive Layout

`MainFrame.kt` wraps its content in `BoxWithConstraints` (line 38) and sets `isDesktop = maxWidth > LayoutConstants.DesktopThreshold` (line 40). `LayoutConstants.DesktopThreshold` is `600.dp` (`StatickUiSettings.kt:33`). Below the threshold, the frame uses a `NavigationBar` (line 84). At or above the threshold, it uses a `NavigationRail` (line 46).

`ProfileScreen.kt:136` contains its own check, `val isDesktop = 600.dp < this.maxWidth`, which duplicates the threshold instead of using the constant.

---

## 7. Dependency Injection and Platform Code

Koin modules are in `core/di/`. The platform module is declared as an `expect` in `AppModul.kt:25`, with actuals in `AppModul.android.kt:11` and `AppModul.jvm.kt:12`. The platform modules provide `DatabaseDriverFactory` (from `:database`); the shared module provides the user database, `ContentDatabaseProvider` (bound as `ContentDatabaseSource`), `ContentRepository` and `ProgressRepository`.

`Modifier.systemPadding` is declared as an `expect` in `core/presentation/androidSystemPadding.kt:5`, with actuals in the Android and JVM source sets (`androidSystemPadding.android.kt:6`, `androidSystemPadding.jvm.kt:5`).

On desktop, the app data directory is `System.getProperty("user.home")` plus `.maturimate` (`AppModul.jvm.kt:14` and `:18`); the databases and the DataStore file are stored there. On Android, the databases are in the app's `databases/` folder.

---

## 8. Preferences Storage

`UserPreferencesRepositoryImpl.kt` stores preferences in DataStore. The keys are defined in a private `Keys` object (line 35): `language`, `eng_level`, `is_vpu_enabled`, `exam_date` (stored as epoch days in a `long`), and `setup_completed`. `saveUserPreferences` writes all values in a single `dataStore.edit` transaction (lines 73-80).

The VPUS/SNI toggle is stored and shown in the onboarding and profile screens. On `main`, the toggle has no effect on any timer, because no exam timer exists. Under the current plan the VPUS/SNI time extension is dropped, so the toggle and its stored value (`is_vpu_enabled`) will be removed during the onboarding rework (see the [ROADMAP](ROADMAP.md)).

---

## 9. Data Architecture

Offline-first, with three databases:

| Database | Where | Contents | Written by |
|---|---|---|---|
| Content (SQLite, `content.db`) | Phone, read-only | 13 content tables: subjects, topic tree, learning units, tasks with options, keys and accepted answers, tags | The exporter; installed as a whole file |
| User (SQLite, `maturimate_user.db`) | Phone | 8 user tables: profile, settings, subjects, attempts, unit and task progress, streak, daily activity | The app |
| Server (PostgreSQL) | Supabase; a local copy in Docker | The 27 tables of `../supabase/migrations`: synced progress, plus a copy of the content. Accounts are Supabase Auth users (`auth.users`) | The importer (content); the app through Supabase, protected by row level security (accounts, progress) |

The PostgreSQL schema in `supabase/migrations` comes from the original 30-table design: the design's own sign-in tables (`app_user`, `user_session`, `password_reset_token`) are replaced by Supabase Auth, every `user_id` is a `uuid` referencing `auth.users`, `user_profile.date_of_birth` is optional, and `user_consent` also records the age confirmation (`AGE_16`). Schema changes are new migration files; an applied migration is never edited. The SQLite schemas repeat it table by table with the same table and column names; only the types and the constraints SQLite cannot express differ (timestamps as epoch milliseconds, dates as `YYYY-MM-DD` text, booleans as `0/1`, no PL/pgSQL triggers, no foreign keys from user tables to content tables or to `auth.users`).

### 9.1 Content pipeline

```
              ┌─→ export → content.db + content-manifest.json → bundled in the app (later: downloaded)
Files (Git) ──┤
              └─→ import → PostgreSQL (server copy; the user tables there reference content by foreign key)
```

* The lesson and task files are the source of truth: Markdown for `SUMMARY` lessons, JSON for tasks and `PRACTICE` units, one folder per leaf topic. All ids are written in the files and never change. Format and rules: [tools/content-pipeline/README.md](../tools/content-pipeline/README.md).
* Both commands use the same loader and `ContentValidator` (format rules of the design, text format, leaf topics, provenance). Nothing is written if a check fails.
* The exporter creates the file with the generated `ContentDatabase.Schema` from `:database`, so the file matches what the app reads. It sets `PRAGMA user_version` to the schema version and writes a manifest with `content_version`, size and SHA-256. The same files give a byte-identical file. With `--previous`, it fails if a row of the previous release is missing: rows are switched off (`is_active`, `is_published`), never removed.
* The importer upserts by id in one transaction, replaces the rows a task owns, deactivates rows missing from the files, and moves the identity sequences past the imported ids.

### 9.2 Content in the app

* `bundleContent` (Gradle task of `:tools:content-pipeline`) exports into `../composeApp/src/commonMain/composeResources/files`. These files are gitignored; a build without them runs, but has no content.
* `ContentInstaller` (`:database`) installs a content file only if its schema version equals the app's, its size and SHA-256 match the manifest, and its `content_version` is higher than the installed one. It writes a temporary file and renames it over the old one, manifest last. The user database is never touched.
* `ContentDatabaseProvider` (`core/database`) installs the bundled file on first use and opens the installed one; it returns null when there is no content. Checked on the Android emulator (API 37): installed on first start, "up to date" after a restart.
* Not built yet: downloading newer content files. They will go through the same installer; the open content driver must be closed before a swap.

### 9.3 Repositories

`features/learning/domain` holds the domain models (`Subject`, `Topic`, `LearningUnit`, `Task`, `Attempt`, `UnitProgress`, `TaskProgress`, `Streak`, `DailyActivity`), the `ContentRepository` and `ProgressRepository` interfaces, and `LearningError` (`NO_CONTENT`, `NOT_FOUND`, `DATABASE_ERROR`). Results use `OperationResult`.

`features/learning/data` holds `ContentRepositoryImpl` (reads through `ContentDatabaseSource`), `ProgressRepositoryImpl` and the mappers. The generated SQLDelight classes do not leave the data layer. In the domain, task keys reference options by label. The phone has one local user; its rows use `LOCAL_USER_ID = 1` until accounts exist. The repositories hold no rules: grading, unlocking and repetition come as use cases with the screens.

---

## 10. Not built yet

* Screens that use the content and progress (Milestone 6). The current subject and exam screens still show static data (sections 3 and 5).
* Content updates by download, accounts, a backend and sync (see the [ROADMAP](ROADMAP.md)).
* Migrations of the user database: the schema may change until the first tester installs the app.

---

## 11. Tests

| Module | Tests | What they cover |
|---|---|---|
| `:database` (`jvmTest`) | 12 | Schema constraints and queries on in-memory SQLite; the content installer |
| `:tools:content-pipeline` | 40 | Validator rules; PostgreSQL writer against a throwaway database with the real DDL (skipped when no local PostgreSQL); exporter |
| `:composeApp` (`jvmTest`, `commonTest`) | 18 | Content provider with the bundled file (skipped without it); repositories; placeholder test |

Run all with `./gradlew :database:jvmTest :tools:content-pipeline:test :composeApp:jvmTest`.

CI (`.github/workflows/ci.yml`) runs on every push and pull request, with one job per module so a failure points at the part that broke:

| Job | Runs | Notes |
|---|---|---|
| Database (SQLDelight) | `:database:jvmTest` | |
| Content pipeline | `:tools:content-pipeline:test` | PostgreSQL 16 service; `REQUIRE_POSTGRES=true`, so the importer tests fail instead of being skipped |
| App | `:composeApp:jvmTest`, then the Android APK and the desktop jar | Bundles the test fixture as content, so the content provider test runs too |

Each job publishes its JUnit results as its own check and as a table (every test, passed or failed) in the run summary.
