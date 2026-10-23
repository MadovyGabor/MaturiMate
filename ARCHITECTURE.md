# MaturiMate Architecture

This document describes the architecture of the `main` branch and, separately, the database work that exists only on the unmerged `database` branch. Statements about `main` were checked against the code on `main`. Anything marked "on the unmerged `database` branch" is not present on `main`.

For the current status of each part, see the "Current status" section of the [README](README.md).

> **Updated 2026-10-22: change of plan.** The first version now uses only content written for this project (short lessons and practice tasks), and the official exam papers moved to a later version (see the [README](README.md)). Sections 1 to 8 describe the code on `main` and are not affected. Sections 9 and 10 describe the database work of the earlier, exam-centred plan. The planned data architecture is in section 11.

---

## 1. Technology Stack

| Component | Version (from `gradle/libs.versions.toml`) | Notes |
|---|---|---|
| Kotlin Multiplatform | Kotlin 2.2.20 | Android target and desktop `jvm()` target |
| Compose Multiplatform | 1.9.1 | Shared UI for Android and desktop |
| Navigation3 for Compose Multiplatform | 1.0.0-alpha06 | Alpha dependency |
| AndroidX Lifecycle (ViewModel) | 2.10.0-alpha07 | Alpha dependency |
| Koin | 4.1.1 | `koin-core`, `koin-compose`, `koin-compose-viewmodel`, plus Android artifacts |
| DataStore Preferences | 1.2.0 | Stores user preferences on all targets |
| kotlinx.serialization | 1.9.0 | On `main`, used for polymorphic navigation routes. No `Json` instance is used on `main`. |
| kotlinx.datetime | 0.7.1 | Used for `LocalDate` and time zone handling |
| kotlinx.coroutines | 1.10.2 | `StateFlow`, `MutableSharedFlow`, and the Swing dispatcher on desktop |
| SQLDelight | 2.2.1 | On the unmerged `database` branch only |

Android targets use `minSdk` 24, `compileSdk` 36, and `targetSdk` 36. The Android target uses `jvmTarget` `JVM_11`. Desktop packaging is configured for DMG, MSI, and DEB formats, but has not been built as part of this document.

---

## 2. Module Layout

The source tree on `main` follows the package layout below. Paths are relative to `composeApp/src/`.

```
commonMain/kotlin/com/madovygabor/maturimate/
├── App.kt
├── core/
│   ├── di/                 # Koin modules (AppModul.kt, DbClient.kt, initKoin.kt)
│   ├── domain/error/       # Error.kt, OperationResult.kt
│   └── presentation/       # MainFrame.kt, navigation/, theme/, uiComponents/, utils/
└── features/
    ├── appOnboarding/      # data/, domain/, presentation/
    ├── examTaking/         # ExamWriteScreen.kt (placeholder)
    ├── profileDetails/     # presentation/ only
    └── subjects/           # presentation/ only
androidMain/kotlin/...      # Android actuals (AppModul.android.kt, DbClient.android.kt, ...)
jvmMain/kotlin/...          # Desktop actuals (AppModul.jvm.kt, DbClient.jvm.kt, ...)
commonTest/kotlin/...       # One placeholder test (ComposeAppCommonTest.kt)
```

On `main`, the `core/database`, `features/subjectsAndExams`, and `sqldelight` directories do not exist. The `composeResources/files/` directory is also absent on `main`. These are part of the unmerged `database` branch.

---

## 3. Layering

Only `features/appOnboarding` has the full `data/`, `domain/`, and `presentation/` split on `main`. The other features contain only presentation code:

* `features/profileDetails` contains only `presentation/`.
* `features/subjects` contains only `presentation/`.
* `features/examTaking` contains a single placeholder screen, `ExamWriteScreen.kt`.

`appOnboarding` domain contains `UserPreferences`, `UserPreferencesRepository`, `MaturitaValidator`, and `DataError`. The data layer contains `UserPreferencesRepositoryImpl`, which uses DataStore. The presentation layer contains the onboarding ViewModel and screens.

The `Subject` model is in the presentation layer, not in the domain layer. There are two classes named `Subject` on `main`:

* `core/presentation/utils/StatickUiSettings.kt:20` defines `data class Subject(...)` with the UI fields and a `dbIdMapping` field. A static map of subjects is defined in the same file.
* `features/subjects/presentation/SubjectListTabScreen.kt:86` defines a second `data class Subject(...)`.

The subject list in `SubjectListTabScreen.kt` is a static `listOf(...)` (line 40) with the subjects MATH, ENGLISH, HUNGARIAN, and SLOVAK.

---

## 4. Presentation Pattern

Presentation on `main` follows MVVM with `StateFlow`. A ViewModel holds a private `MutableStateFlow` and exposes it as a read-only `StateFlow` through `asStateFlow()`. The state is an immutable `data class` that is updated with `copy()`. Example: `ProfileViewModel.kt:40` declares `private val _profileUiState = MutableStateFlow(ProfileUiState())` and `ProfileViewModel.kt:43` exposes `val profileUiState = _profileUiState.asStateFlow()`.

`main` does not use intents, a reducer, or a single-state MVI loop. Screens call ViewModel methods directly.

One-shot messages (for example, snackbars) go through a singleton `SnackbarController` (`core/presentation/utils/snackbar/SnackbarController.kt:11`), which uses a `MutableSharedFlow` (line 14).

Profile statistics are mock data on `main`. `ProfileViewModel.kt:47` calls `loadMockSubjectStats()`, and the values are hard-coded in lines 164-171.

---

## 5. Navigation

Navigation uses JetBrains Navigation3 with `@Serializable` routes. `Route` is a `sealed interface Route : NavKey` (`core/presentation/navigation/Route.kt:8`). Routes include `Route.ExamWrite(val examId: String)` (`Route.kt:21`). The back stack is held with `rememberNavBackStack`.

`NavigationRoot.kt` registers the polymorphic subclasses for `Main`, `AppOnboarding`, and `ExamWrite` (lines 28-32). `ExamWrite` renders `ExamWriteScreen()` (lines 64-66). `ExamWriteScreen` currently displays the text "Exam Write Screen" (`features/examTaking/ExamWriteScreen.kt:25`). The `History` tab shows a placeholder text (`core/presentation/navigation/MainFrame/MainFrameNavigation.kt:61`).

Tapping a subject in `SubjectNavigation.kt` pushes `Route.Main.Subject.ExamList(it)` to the back stack (lines 33-34). `ExamSelectionScreen.kt` reads the subject from the static `SubjectStaticData` (line 44) and has a `// TODO: Use Koin to inject the ViewModel` comment (line 39). The exam list is not connected to a data source on `main`, and tapping an exam does not open `ExamWriteScreen` with an exam ID.

---

## 6. Responsive Layout

`MainFrame.kt` wraps its content in `BoxWithConstraints` (line 38) and sets `isDesktop = maxWidth > LayoutConstants.DesktopThreshold` (line 40). `LayoutConstants.DesktopThreshold` is `600.dp` (`StatickUiSettings.kt:33`). Below the threshold, the frame uses a `NavigationBar` (line 84). At or above the threshold, it uses a `NavigationRail` (line 46).

`ProfileScreen.kt:136` contains its own check, `val isDesktop = 600.dp < this.maxWidth`, which duplicates the threshold instead of using the constant.

---

## 7. Dependency Injection and Platform Code

Koin modules are in `core/di/`. The platform module is declared as an `expect` in `AppModul.kt:18`, with actuals in `AppModul.android.kt:11` and `AppModul.jvm.kt:12`.

`DbClient.kt:6` declares an empty `expect` on `main`. Its actuals are `DbClient.android.kt:6` and `DbClient.jvm.kt:4`.

`Modifier.systemPadding` is declared as an `expect` in `core/presentation/androidSystemPadding.kt:5`, with actuals in the Android and JVM source sets (`androidSystemPadding.android.kt:6`, `androidSystemPadding.jvm.kt:5`).

On desktop, the app data directory is `System.getProperty("user.home")` plus `.maturimate` (`composeApp/src/jvmMain/kotlin/com/madovygabor/maturimate/core/di/AppModul.jvm.kt:18`).

---

## 8. Preferences Storage

`UserPreferencesRepositoryImpl.kt` stores preferences in DataStore. The keys are defined in a private `Keys` object (line 35): `language`, `eng_level`, `is_vpu_enabled`, `exam_date` (stored as epoch days in a `long`), and `setup_completed`. `saveUserPreferences` writes all values in a single `dataStore.edit` transaction (lines 73-80).

The VPUS/SNI toggle is stored and shown in the onboarding and profile screens. On `main`, the toggle has no effect on any timer, because no exam timer exists. Under the current plan the VPUS/SNI time extension is dropped, so the toggle and its stored value (`is_vpu_enabled`) will be removed during the onboarding rework (see the [ROADMAP](ROADMAP.md)).

---

## 9. Database Layer (unmerged `database` branch only)

The following describes code that exists only on the unmerged `database` branch. None of it is present on `main`.

This design follows the earlier plan (official exam papers as the main content), so the schema is centred on the exam (`ExamEntity`). It will be reworked for the lesson-based structure before it is used; see section 11. Drivers and the seeder idea can be reused.

### 9.1 Components

* SQLDelight 2.2.1 schema files in `composeApp/src/commonMain/sqldelight/com/madovygabor/maturimate/database/` (`ExamEntity.sq`, `ContentUnitEntity.sq`, `TaskEntity.sq`, `UserProgressEntity.sq`).
* `DatabaseSeeder` in `features/subjectsAndExams/data/DatabaseSeeder.kt`. It parses the bundled JSON with `Json { ignoreUnknownKeys = true; coerceInputValues = true }` (lines 20-23) and writes the data inside `database.transaction` (line 38). Inserts are plain `INSERT` statements (for example `ExamEntity.sq:41-43`), so re-running the seeder against existing data is not expected to succeed. The seeder is not idempotent.
* `ExamRepositoryImpl` in `features/subjectsAndExams/data/repository/`. It maps exceptions to `DATABASE_ERROR` and `PARSE_ERROR` (lines 87-89 and 107-109).
* `DatabaseDriverFactory.jvm.kt` creates the database file under the user home directory (line 24) and creates the schema only when the file is new (lines 34 and 39-41). The Android driver is `AndroidSqliteDriver` with the database name `maturimate.db`.

### 9.2 Schema

`ExamEntity` (table root):

* `id` (primary key, for example `HU_MJL_2025_MARCH_251425`), `subjectId`, `year`, `session`
* `totalMinutes`, `vpuMinutes`, `taskCount`

`ContentUnitEntity` (reading passages and other source material):

* `id` (primary key), `examId` (foreign key to `ExamEntity`, `ON DELETE CASCADE`, `ContentUnitEntity.sq:33`)
* `type`, `title`, `author`, `description`, `contentHtml`, `mediaUrl`, `sharedOptionsJson`

`TaskEntity` (questions):

* `id` (primary key), `examId` (foreign key, `ON DELETE CASCADE`), `contentUnitId` (nullable foreign key, `ON DELETE CASCADE`, `TaskEntity.sq:38-39`)
* `orderIndex`, `type`, `question`, `points` (default 1), `imageUrl`, `metadataJson` (`NOT NULL`)

`UserProgressEntity` (saved answers):

* Composite primary key `(attemptId, taskId)` (`UserProgressEntity.sq:31`), foreign key `taskId` to `TaskEntity` with `ON DELETE CASCADE` (`UserProgressEntity.sq:34`)
* `examId`, `userAnswer`, `isCorrect` (mapped to `kotlin.Boolean`), `earnedPoints`, `timestamp`

### 9.3 Stored but not interpreted

On the `database` branch, `contentHtml` and `metadataJson` are stored as strings. They are passed through the mapper (`ExamMapper.kt:82` and `:96`) and are not parsed or rendered. `sharedOptionsJson` is written as `null` by the seeder (`DatabaseSeeder.kt:62`). No code on the `database` branch parses `metadataJson` into typed answer data.

### 9.4 Enum mapping

`TaskType` (`features/subjectsAndExams/domain/model/ExamTypes.kt`) has the values `CHOICE`, `INPUT`, `CHOICE_5`, `WORD_FORMATION`, `GAP_FILL_SELECTION`, and `UNKNOWN`. `ContentUnitType` has `TEXT`, `AUDIO`, `GAP_FILL`, and `UNKNOWN`. `ExamMapper.kt:21-27` converts a string with `TaskType.valueOf` inside a try-catch and returns `UNKNOWN` for any unrecognised value. The bundled `HU_MJL_2025.json` contains two tasks with type `CANCELED`, which is not in the enum, so those two tasks map to `UNKNOWN`.

### 9.5 Subject and exam identifiers

On the `database` branch, `StatickUiSettings.kt` maps UI subjects to database subject IDs through `dbIdMapping`:

* HUNGARIAN maps to `HU_MJL`.
* SLOVAK maps to `HU_SJSL` (Hungarian school type) or `SK_SJL` (Slovak school type).
* ENGLISH maps to `AJ_B1` or `AJ_B2`, depending on the selected English level.
* MATH maps to `HU_MAT` (Hungarian school type) or `SK_MAT` (Slovak school type).

The bundled exam file `composeApp/src/commonMain/composeResources/files/HU_MJL_2025.json` has the exam ID `HU_MJL_2025_MARCH_251425` on line 3. The file is tracked on `origin/database` and is not present on `main`.

---

## 10. Extensibility

Under the earlier plan, the `database` branch was designed so that new exam years could be added as JSON files and imported by `DatabaseSeeder` without schema changes. This was not tested with more than one bundled exam file. Under the current plan, the same idea applies to lessons and tasks: a new subject, lesson or task is new data loaded by an importer, not new code (see section 11).

The `UserProgressEntity` table is intended to store per-attempt answers. No code on `main` writes to it, and no screen reads it.

---

## 11. Planned data architecture (not implemented)

This section describes the target design under the current plan. None of it is present on `main`. The details and the order of work are in the [ROADMAP](ROADMAP.md#data-architecture-offline-first).

The app is offline-first and uses three databases with clear roles:

* **Server database (PostgreSQL):** the source of truth for all content and, later, for accounts and synced user data. The app never talks to it directly; a backend holds the credentials, and passwords are stored hashed.
* **Content database (SQLite, on the phone):** read-only, shipped inside the app as a prebuilt file, and replaced as a whole when a newer `content_version` exists (check the version, download to a temporary location, verify size and hash, then swap). There is no row-level sync for content.
* **User database (SQLite, on the phone):** attempts, answers, streak and settings. A content update never touches it. It refers to tasks by UUID only, because there is no foreign key across the two files, and tasks are marked inactive instead of being deleted so old attempts never point to nothing.

User data flows mainly from the phone to the server through an append-only outbox of unsent attempts, so conflicts are rare. The server sends it back only on a new device or a restore. Settings and streak use last write wins. Until accounts exist, the user database is local only.

The SQLite schema is written separately as SQLDelight `.sq` files, because SQLite has no native UUID, boolean, decimal or timestamp types. Table and column names stay identical to PostgreSQL, so the export and the sync stay simple. Lessons and tasks are written as files, loaded by an importer into PostgreSQL, and exported into the prebuilt content file.
