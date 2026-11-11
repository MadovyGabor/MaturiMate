# MaturiMate

> Kotlin Multiplatform app for Android and desktop (JVM), in early development. The product goal is an offline-first learning app (short lessons and practice tasks) for high school students who prepare for the graduation exam (*maturita*) in Slovakia. [VISION.md](docs/VISION.md) still describes the earlier plan; see the update below.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.2-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android_%7C_Desktop_(JVM)-3DDC84.svg?logo=android&logoColor=white)](https://github.com/MadovyGabor/MaturiMate)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM_%2B_StateFlow-brightgreen.svg)](#architecture)
[![Database](https://img.shields.io/badge/Database-SQLDelight_%2B_PostgreSQL-blue.svg)](https://cashapp.github.io/sqldelight/)
[![DI](https://img.shields.io/badge/DI-Koin_4.2.2-red.svg)](https://insert-koin.io/)

## Update, 2026-10-22: change of plan

**The plan changed.** The original plan was that the first version digitises the official Slovak graduation exam papers. The new first version (V1) uses only content written for this project. The official papers move to a later V2, and only if the exam authority (NIVAM) gives permission. The "Current status" section below describes the code on `main`; the "Planned" section, [VISION.md](docs/VISION.md), [ROADMAP.md](docs/ROADMAP.md) and [ARCHITECTURE.md](docs/ARCHITECTURE.md) follow the new plan.

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

This section describes the code on `main`, after Milestone 1 (core and database).

### Built

- One Kotlin Multiplatform project with an Android target and a desktop (JVM) target. About 95% of production Kotlin lines of the app and the `:database` module (4479 of 4718 non-blank lines) are in `commonMain`.
- Onboarding (old screens, rebuilt in Milestone 5): welcome screen, language choice (HU or SK) and graduation date picker with validation.
- The profile and settings are stored in the local user database (`user_profile`, `user_settings`), under a guest id until sign-up.
- Profile screen: days left until the graduation date, and edit dialogs for the date, language and level.
- Adaptive navigation: a bottom `NavigationBar` below 600 dp and a `NavigationRail` at 600 dp and above.
- Navigation3 (alpha) root with serializable route keys, top-level tabs and per-tab back stacks.
- Koin dependency injection modules, shared and per platform.
- Android splash screen and a desktop window entry point.
- Database core (Milestone 1):
  - The PostgreSQL schema (originally 30 tables, now 27 in `supabase/migrations` with Supabase Auth) and a local PostgreSQL in Docker.
  - Content database (15 tables) and user database (10 tables, plus 2 phone-only state tables) with SQLDelight, in a shared `:database` module.
  - Content pipeline (`tools/content-pipeline`): validates the lesson and task files, exports the content database file for the app, and imports the content into PostgreSQL.
  - The app installs the bundled content file on first start (checked on the Android emulator).
  - Content and progress repositories (domain interfaces, SQLDelight implementations), not used by any screen yet.

### In progress

- The subjects tab reads the content and the progress (subjects, parts, topics, the learning path); the lesson and practice screens come in Milestone 7, steps 7.2 to 7.5.
- Next: the design system in the code (Milestone 2), then the onboarding rework with Supabase accounts (Milestones 3 to 5). See [ROADMAP.md](docs/ROADMAP.md).
- The real lessons and tasks are written in a separate private repository, cloned into `content/` (gitignored).

### Planned

Under the new plan (see the update above):

- Short lessons with practice tasks for one subject, on Android first.
- Daily streak and simple progress based on real attempt data.
- Offline-first storage: content shipped with the app, user progress stored separately.
- Full localization of UI strings.
- More subjects and platforms later, see [ROADMAP.md](docs/ROADMAP.md).

Postponed to V2, only with permission from the exam authority: official exam papers and the exam-specific subject content (`SK_SJL`, English B1/B2, Mathematics).

The English level (B1/B2) is stored per subject in `user_subject.level`; it returns with the subjects step of the new onboarding. The VPUS/SNI toggle is removed (see the ROADMAP).

## Technology

Versions are taken from `gradle/libs.versions.toml` on `main`.

| Component | Version | Notes |
| :--- | :--- | :--- |
| Kotlin | 2.4.21 | Also used for the Compose compiler plugin and the serialization plugin |
| Compose Multiplatform | 1.12.2 | Shared UI in `shared/src/commonMain`; Material 3 1.9.0 |
| Koin | 4.2.2 | `koin-core`, `koin-compose`, `koin-compose-viewmodel`; Android and Compose extras |
| Navigation3 (`navigation3-ui`) | 1.2.0 | |
| AndroidX lifecycle (`viewmodel-compose`) | 2.11.0 | |
| kotlinx.serialization (JSON) | 1.12.0 | Navigation key polymorphism; content manifest and content files |
| kotlinx.datetime | 0.8.0 | Date handling |
| kotlinx.coroutines | 1.11.0 | `StateFlow`, `SharedFlow`, `viewModelScope` |
| SQLDelight | 2.4.1 | Content and user databases (`:database` module) |
| supabase-kt | 3.8.0 | Sign-up and sign-in; Ktor 3.6.1 |
| PostgreSQL | 17 (Supabase), 16 (Docker), JDBC 42.7.14 | Server database and content importer; not used by the app directly |
| Android | minSdk 26, compileSdk 37, targetSdk 37 | Android Gradle Plugin 9.5.0, Gradle 9.8.2 |
| JVM | `jvmTarget` 11 for Android; JDK 21 for the build | See [Setup](#setup) |

## Architecture

Presentation follows MVVM with `StateFlow`. Each ViewModel keeps a private `MutableStateFlow` and exposes it as a read-only `StateFlow`. The state is an immutable `data class` (for example `ProfileUiState`), updated with `copy()`. Screens call ViewModel functions directly. There are no intent types and no reducer, so this is not MVI.

One-shot messages (snackbars) go through a singleton `SnackbarController` that wraps a `MutableSharedFlow`.

Layering is not uniform:

- `features/appOnboarding` has `data`, `domain` and `presentation` packages.
- `features/learning` has `data` and `domain` packages (content and progress repositories); its screens come later.
- `features/profileDetails`, `features/subjects` and `features/examTaking` have only `presentation` code.
- `core/domain` contains the shared `OperationResult` and `Error` types.

User profile domain: `UserProfile`, `UserProfileRepository` (interface), `TeachingLanguage` and `DataError`. The learning domain has its own `Subject` model and the `LearningCourse` use case that builds the learning path.

Profile data: `UserProfileRepositoryImpl` writes one field per save to `user_profile` and `user_settings`; the first save creates both rows. The app starts on the home screen once `user_profile.onboarding_completed_at` is set.

Platform code (`expect`/`actual`): the platform Koin module (`platformModule`), `DatabaseDriverFactory` (in `:database`), and `Modifier.systemPadding()`.

Data: three databases with clear roles. The content database (read-only on the phone) is built from the lesson and task files and installed as a whole file; the user database holds the progress; PostgreSQL holds accounts and synced progress later, plus a copy of the content. See [ARCHITECTURE.md](docs/ARCHITECTURE.md#9-data-architecture).

## Project structure

```
MaturiMate/
├── README.md
├── docs/                    VISION.md, ROADMAP.md, ARCHITECTURE.md
├── supabase/                Supabase settings and the schema migrations (27 tables)
├── dbServer/                local PostgreSQL in Docker with the same schema
├── content/                 lesson and task files (private repository, gitignored)
├── shared/                  shared UI, features and platform code (Android and desktop)
├── androidApp/              the Android app (entry point, manifest, icons)
├── desktopApp/              the desktop app (entry point, packaging)
│   └── src/
│       ├── commonMain/      App.kt, core/ (database, di, domain, presentation), features/
│       ├── androidMain/     MainActivity, Koin platform module
│       ├── jvmMain/         main.kt, Koin platform module
│       ├── commonTest/      placeholder test
│       └── jvmTest/         content provider and repository tests
├── database/                shared module: SQLDelight .sq files, drivers, content installer
└── tools/content-pipeline/  content validator, exporter and importer (see its README)
```

## Setup

Requirements:

- JDK 21 (the one bundled with Android Studio works; AGP 9 needs at least 17).
- Android SDK Platform 37 (for the Android target). Put the SDK path in `local.properties`; that file is not committed.
- Android Studio with AGP 9.5 support, or IntelliJ IDEA with the Compose Multiplatform plugin, is optional. Run configurations: `androidApp` for Android, `desktopApp` for desktop.

Commands:

```bash
# Android debug APK
./gradlew :androidApp:assembleDebug

# Desktop application
./gradlew :desktopApp:run

# Desktop application with Compose Hot Reload
./gradlew :desktopApp:hotRun

# Compile the desktop (JVM) target
./gradlew :shared:compileKotlinJvm

# Tests (database, content pipeline, app)
./gradlew :database:jvmTest :tools:content-pipeline:test :shared:jvmTest

# Bundle the content files into the app (needs the content/ folder)
./gradlew :tools:content-pipeline:bundleContent -PcontentVersion=2026102801

# Local PostgreSQL (copy dbServer/.env.example to dbServer/.env first; see dbServer/README.md)
docker compose -f dbServer/docker-compose.yml up -d
```

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`.

Desktop packaging is configured for Dmg, Msi and Deb (`desktopApp/build.gradle.kts`). Packaging has not been run.

## Known limitations

- Placeholders and mock data: `ExamWriteScreen` is a text placeholder; the History entry is a text placeholder; profile subject statistics are hard-coded values.
- Tests cover the database, the content pipeline and the repositories (70 tests). No tests exist for the onboarding, profile, or navigation code. No coverage tool is configured.
- CI (GitHub Actions, `.github/workflows/ci.yml`) runs on every push that changes more than documentation, in four parallel jobs: database, content pipeline (with a PostgreSQL 16 service), app (tests and the desktop jar) and the Android build. A pull request shows the result of its branch's push. Each job shows its test results as a table in the run summary and as its own check.
- Localization: Slovak in `values` (the default for every phone language except Hungarian), Hungarian in `values-hu`. The old setup and profile screens still have hard-coded Hungarian text; they are rebuilt in Milestone 5.

## Development approach

I design the architecture and make the technical decisions myself. AI assistants help with boilerplate, documentation and drafts and speed up implementation; I review what goes into the repository.

## Documentation

- [VISION.md](docs/VISION.md): product goal, target audience, content sources and planned scope, updated for the own-content plan.
- [ROADMAP.md](docs/ROADMAP.md): milestones in working order, with the offline-first data architecture.
- [ARCHITECTURE.md](docs/ARCHITECTURE.md): technology, modules, layering, navigation, and the data architecture with the content pipeline.
- [tools/content-pipeline/README.md](tools/content-pipeline/README.md): how to write, check and export content.
- [docs/design-system/](docs/design-system/README.md): colors, typography, shapes and components; `tokens.json` is the source of truth for the theme.
- [AGENTS.md](AGENTS.md): working rules for AI agents (`CLAUDE.md` imports it).
