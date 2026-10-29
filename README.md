# MaturiMate

> Kotlin Multiplatform app for Android and desktop (JVM), in early development. The product goal is an offline-first learning app (short lessons and practice tasks) for high school students who prepare for the graduation exam (*maturita*) in Slovakia. [VISION.md](VISION.md) still describes the earlier plan; see the update below.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.9.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android_%7C_Desktop_(JVM)-3DDC84.svg?logo=android&logoColor=white)](https://github.com/MadovyGabor/MaturiMate)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM_%2B_StateFlow-brightgreen.svg)](#architecture)
[![Database](https://img.shields.io/badge/Database-SQLDelight_%2B_PostgreSQL-blue.svg)](https://cashapp.github.io/sqldelight/)
[![DI](https://img.shields.io/badge/DI-Koin_4.1.1-red.svg)](https://insert-koin.io/)

## Update, 2026-10-22: change of plan

**The plan changed.** The original plan was that the first version digitises the official Slovak graduation exam papers. The new first version (V1) uses only content written for this project. The official papers move to a later V2, and only if the exam authority (NIVAM) gives permission. The "Current status" section below describes the code on `main`; the "Planned" section, [VISION.md](VISION.md), [ROADMAP.md](ROADMAP.md) and [ARCHITECTURE.md](ARCHITECTURE.md) follow the new plan.

### Why: the problems and barriers

- **Copyright.** The official papers and audio are copyrighted, so using them needs permission. The repository may become public, so real exam content cannot live in it.
- **Effort.** Digitising real papers (task formats, answer keys, audio, accommodation variants, missing keys and papers) took much more work than expected.
- **The hard parts are not the code.** They are content production, legal questions and offline sync.
- **Dependency on a third party.** A release would have to wait for a permission that may take long or never come.

### Why: the new approach is better, faster and works

- **No legal dependency.** The app can be released at any time.
- **Full control** over content, difficulty and topics.
- **A stronger idea.** Short lessons with practice tasks (micro-learning) beat another copy of freely available exam papers.
- **Much smaller scope and risk.** One subject and one platform are enough to start, so a working app comes sooner.
- **A better position for V2.** With real users and usage numbers, a later request for permission is stronger. If it is refused, the app still lives on its own content.
- **No redesign needed.** The planned database design stays compatible: a new subject, lesson or task is new data, not new code.

**Content sources for V1.** The content is written for this project, from four sources:

- **My own experience.** I have been through the graduation exam myself, so the lessons and tasks come from what I learned and how I prepared. This includes notes and study material I worked out myself in high school.
- **Teachers.** Lessons and tasks written together with teachers, who contribute only with their written permission.
- **AI.** AI helps to draft and extend lessons and tasks, and a human reviews everything before it goes into the app.
- **Legally available material.** Sources that can be used legally, such as public-domain texts. Published textbooks and workbooks are not copied.

**What happens next.**

1. Android only, one subject: short lessons, practice tasks, a streak and simple progress.
2. More subjects on the same engine, where a new subject, lesson or task is new data and not new code.
3. More platforms (a desktop target already exists in the project; iOS and web later).
4. V2: official exam content, only with permission.

The app stays offline-first.

## Table of contents

- [Update, 2026-10-22: change of plan](#update-2026-10-22-change-of-plan)
- [Current status](#current-status)
- [Technology](#technology)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Setup](#setup)
- [Known limitations](#known-limitations)
- [Development approach](#development-approach)
- [Documentation](#documentation)

## Current status

This section describes the code on the branch `feat/database-core` (Milestone 1), which is not merged into `main` yet. Everything under "Built" except the database core is also on `main`.

### Built

- One Kotlin Multiplatform project with an Android target and a desktop (JVM) target. About 95% of production Kotlin lines of the app and the `:database` module (4479 of 4718 non-blank lines) are in `commonMain`.
- Onboarding: welcome screen, language choice (HU or SK), English level (B1 or B2), graduation date picker with validation, and a VPUS/SNI toggle. The toggle value is stored, but no exam timer exists yet, so it does not change any time limit. The VPUS/SNI time extension is dropped from the plan, so the toggle will be removed during the onboarding rework.
- User settings are persisted with Jetpack DataStore.
- Profile screen: days left until the graduation date, and edit dialogs for the date, language and level.
- Adaptive navigation: a bottom `NavigationBar` below 600 dp and a `NavigationRail` at 600 dp and above.
- Navigation3 (alpha) root with serializable route keys, top-level tabs and per-tab back stacks.
- Koin dependency injection modules, shared and per platform.
- Android splash screen and a desktop window entry point.
- Database core (Milestone 1, branch `feat/database-core`):
  - The final PostgreSQL schema (30 tables, `db/postgres/01_ddl.sql`) and a local PostgreSQL in Docker.
  - Content database (13 tables) and user database (8 tables) with SQLDelight, in a shared `:database` module.
  - Content pipeline (`tools/content-pipeline`): validates the lesson and task files, exports the content database file for the app, and imports the content into PostgreSQL.
  - The app installs the bundled content file on first start (checked on the Android emulator).
  - Content and progress repositories (domain interfaces, SQLDelight implementations), not used by any screen yet.

### In progress

- The subject and exam selection screens are still a static shell. They read hard-coded subject data and do not use the database yet; the lesson and practice screens come in Milestone 3.
- The real lessons and tasks are written in a separate private repository, cloned into `content/` (gitignored).

### Planned

Under the new plan (see the update above):

- Short lessons with practice tasks for one subject, on Android first.
- Daily streak and simple progress based on real attempt data.
- Offline-first storage: content shipped with the app, user progress stored separately.
- Full localization of UI strings.
- More subjects and platforms later, see [ROADMAP.md](ROADMAP.md).

Postponed to V2, only with permission from the exam authority: official exam papers and the exam-specific subject content (`SK_SJL`, English B1/B2, Mathematics).

Open question: the English level (B1/B2) setting from onboarding was built for the exam plan. Whether it stays in V1 is not decided yet. The VPUS/SNI toggle is removed (see the ROADMAP).

## Technology

Versions are taken from `gradle/libs.versions.toml` on `main`.

| Component | Version | Notes |
| :--- | :--- | :--- |
| Kotlin | 2.2.20 | Also used for the Compose compiler plugin and the serialization plugin |
| Compose Multiplatform | 1.9.1 | Shared UI in `commonMain` |
| Koin | 4.1.1 | `koin-core`, `koin-compose`, `koin-compose-viewmodel`; Android and Compose extras |
| Navigation3 (`navigation3-ui`) | 1.0.0-alpha06 | **Alpha** |
| AndroidX lifecycle (`viewmodel-compose`) | 2.10.0-alpha07 | **Alpha** |
| DataStore Preferences | 1.2.0 | User settings |
| kotlinx.serialization (JSON) | 1.9.0 | Navigation key polymorphism; content manifest and content files |
| kotlinx.datetime | 0.7.1 | Date handling |
| kotlinx.coroutines | 1.10.2 | `StateFlow`, `SharedFlow`, `viewModelScope` |
| SQLDelight | 2.2.1 | Content and user databases (`:database` module) |
| PostgreSQL | 16 (Docker), JDBC 42.7.4 | Local development database and content importer; not used by the app |
| Android | minSdk 24, compileSdk 36, targetSdk 36 | Android Gradle Plugin 8.13.0 |
| JVM | `jvmTarget` 11 for Android; JDK 21 for the build | See [Setup](#setup) |

## Architecture

Presentation follows MVVM with `StateFlow`. Each ViewModel keeps a private `MutableStateFlow` and exposes it as a read-only `StateFlow`. The state is an immutable `data class` (for example `ProfileUiState`), updated with `copy()`. Screens call ViewModel functions directly. There are no intent types and no reducer, so this is not MVI.

One-shot messages (snackbars) go through a singleton `SnackbarController` that wraps a `MutableSharedFlow`.

Layering is not uniform:

- `features/appOnboarding` has `data`, `domain` and `presentation` packages.
- `features/learning` has `data` and `domain` packages (content and progress repositories); its screens come later.
- `features/profileDetails`, `features/subjects` and `features/examTaking` have only `presentation` code.
- `core/domain` contains the shared `OperationResult` and `Error` types.

Onboarding domain: `UserPreferences`, `UserPreferencesRepository` (interface), `MaturitaValidator`, and the `MaturitaLanguage` and `MaturitaLevel` enums. The learning domain has its own `Subject` model; the subject screens still use two presentation classes named `Subject`: `core/presentation/utils/StatickUiSettings.kt` and `features/subjects/presentation/SubjectListTabScreen.kt`.

Onboarding data: `UserPreferencesRepositoryImpl` writes the DataStore keys `language`, `eng_level`, `is_vpu_enabled`, `exam_date` and `setup_completed` in one `edit` transaction.

Platform code (`expect`/`actual`): the platform Koin module (`platformModule`), `DatabaseDriverFactory` (in `:database`), and `Modifier.systemPadding()`.

Data: three databases with clear roles. The content database (read-only on the phone) is built from the lesson and task files and installed as a whole file; the user database holds the progress; PostgreSQL holds accounts and synced progress later, plus a copy of the content. See [ARCHITECTURE.md](ARCHITECTURE.md#9-data-architecture).

## Project structure

```
MaturiMate/
├── README.md  ARCHITECTURE.md  ROADMAP.md  VISION.md
├── docs/                    IMPLEMENTATION_PLAN.md
├── llm/                     decisions and notes for planning
├── db/postgres/             01_ddl.sql (PostgreSQL schema, 30 tables)
├── docker-compose.yml       local PostgreSQL; settings in .env (see .env.example)
├── content/                 lesson and task files (private repository, gitignored)
├── composeApp/              the app (Android and desktop)
│   └── src/
│       ├── commonMain/      App.kt, core/ (database, di, domain, presentation), features/
│       ├── androidMain/     MainActivity, Koin platform module, DataStore
│       ├── jvmMain/         main.kt, Koin platform module
│       ├── commonTest/      placeholder test
│       └── jvmTest/         content provider and repository tests
├── database/                shared module: SQLDelight .sq files, drivers, content installer
└── tools/content-pipeline/  content validator, exporter and importer (see its README)
```

## Setup

Requirements:

- JDK 21.
- Android SDK Platform 36 (for the Android target). Put its path in `local.properties`; that file is not committed.
- Android Studio or IntelliJ IDEA with the Compose Multiplatform plugin is optional.

Important: `gradle.properties` line 4 sets `org.gradle.java.home` to a JDK 21 (JBR) path under a local Windows user profile. This path exists only on the original development machine. On any other machine, change that line to your JDK 21 location, or remove it and set `JAVA_HOME`.

Commands:

```bash
# Android debug APK
./gradlew :composeApp:assembleDebug

# Desktop application
./gradlew :composeApp:run

# Compile the desktop (JVM) target
./gradlew :composeApp:compileKotlinJvm

# Tests (database, content pipeline, app)
./gradlew :database:jvmTest :tools:content-pipeline:test :composeApp:jvmTest

# Bundle the content files into the app (needs the content/ folder)
./gradlew :tools:content-pipeline:bundleContent -PcontentVersion=2026102801

# Local PostgreSQL (copy .env.example to .env first)
docker compose up -d
```

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`.

Desktop packaging is configured for Dmg, Msi and Deb (`composeApp/build.gradle.kts`). Packaging has not been run.

## Known limitations

- Placeholders and mock data: `ExamWriteScreen` is a text placeholder; the History entry is a text placeholder; profile subject statistics are hard-coded values; the subject list and the exam list are static, with no data source.
- Alpha dependencies: `androidx.lifecycle` 2.10.0-alpha07 and Navigation3 1.0.0-alpha06.
- Tests cover the database, the content pipeline and the repositories (70 tests). No tests exist for the onboarding, profile, or navigation code. No coverage tool is configured.
- No CI: the repository has no `.github` directory and no workflow files.
- Partial localization: 12 strings in `values`, 3 in `values-hu` and 3 in `values-sk`. Some UI text is hard-coded in Kotlin source.

## Development approach

I design the architecture and make the technical decisions myself. AI assistants help with boilerplate, documentation and drafts and speed up implementation; I review what goes into the repository.

## Documentation

- [VISION.md](VISION.md): product goal, target audience, content sources and planned scope, updated for the own-content plan.
- [ROADMAP.md](ROADMAP.md): milestones in working order, with the offline-first data architecture.
- [ARCHITECTURE.md](ARCHITECTURE.md): technology, modules, layering, navigation, and the data architecture with the content pipeline.
- [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md): the ordered work list with the database details.
- [tools/content-pipeline/README.md](tools/content-pipeline/README.md): how to write, check and export content.
