# MaturiMate

> Kotlin Multiplatform app for Android and desktop (JVM), in early development. The product goal is an offline-first learning app (short lessons and practice tasks) for high school students who prepare for the graduation exam (*maturita*) in Slovakia. [VISION.md](VISION.md) still describes the earlier plan; see the update below.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.9.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android_%7C_Desktop_(JVM)-3DDC84.svg?logo=android&logoColor=white)](https://github.com/MadovyGabor/MaturiMate)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM_%2B_StateFlow-brightgreen.svg)](#architecture)
[![Database](https://img.shields.io/badge/Database-SQLDelight_(unmerged_branch)-lightgrey.svg)](https://cashapp.github.io/sqldelight/)
[![DI](https://img.shields.io/badge/DI-Koin_4.1.1-red.svg)](https://insert-koin.io/)

## Update, 2026-10-22: change of plan

**The plan changed.** This README and the other documents (VISION, ROADMAP, ARCHITECTURE) still describe the old plan, where the first version digitised the official Slovak graduation exam papers. The new first version (V1) uses only content written for this project. The official papers move to a later V2, and only if the exam authority (NIVAM) gives permission. The "Current status" section below still describes the code accurately; the "Planned" parts and the other documents will be updated step by step.

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

This section describes the `main` branch. Work that exists only on the unmerged `database` branch is labelled as such.

### Built

- One Kotlin Multiplatform project with an Android target and a desktop (JVM) target. About 96% of production Kotlin lines (3777 of 3949 non-blank lines) are in `commonMain`.
- Onboarding: welcome screen, language choice (HU or SK), English level (B1 or B2), graduation date picker with validation, and a VPUS/SNI toggle. The toggle value is stored, but no exam timer exists yet, so it does not change any time limit.
- User settings are persisted with Jetpack DataStore.
- Profile screen: days left until the graduation date, and edit dialogs for the date, language and level.
- Adaptive navigation: a bottom `NavigationBar` below 600 dp and a `NavigationRail` at 600 dp and above.
- Navigation3 (alpha) root with serializable route keys, top-level tabs and per-tab back stacks.
- Koin dependency injection modules, shared and per platform.
- Android splash screen and a desktop window entry point.

### In progress

- On the unmerged `database` branch (not in `main`): SQLDelight schema (four tables), `DatabaseSeeder`, and database-backed subject and exam selection. This branch follows the earlier plan (official exam papers), so its schema will be reworked before it is merged.
- On `main`, the subject and exam selection screens are a static shell. They read hard-coded subject data and do not load exams.

### Planned

Under the new plan (see the update above):

- Short lessons with practice tasks for one subject, on Android first.
- Daily streak and simple progress based on real attempt data.
- Offline-first storage: content shipped with the app, user progress stored separately.
- Full localization of UI strings.
- More subjects and platforms later, see [ROADMAP.md](ROADMAP.md) (not yet updated to the new plan).

Postponed to V2, only with permission from the exam authority: official exam papers, the exam runner with timer, VPUS/SNI time extension, and the exam-specific subject content (`SK_SJL`, English B1/B2, Mathematics).

Open question: the VPUS/SNI toggle and the English level (B1/B2) setting from onboarding were built for the exam plan. Whether they stay in V1 is not decided yet.

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
| kotlinx.serialization (JSON) | 1.9.0 | On `main`, used for navigation key polymorphism only |
| kotlinx.datetime | 0.7.1 | Date handling |
| kotlinx.coroutines | 1.10.2 | `StateFlow`, `SharedFlow`, `viewModelScope` |
| SQLDelight | 2.2.1 | Schema and drivers are on the unmerged `database` branch; `main` declares the runtime artifacts but has no `.sq` file |
| Android | minSdk 24, compileSdk 36, targetSdk 36 | Android Gradle Plugin 8.13.0 |
| JVM | `jvmTarget` 11 for Android; JDK 21 for the build | See [Setup](#setup) |

## Architecture

Presentation follows MVVM with `StateFlow`. Each ViewModel keeps a private `MutableStateFlow` and exposes it as a read-only `StateFlow`. The state is an immutable `data class` (for example `ProfileUiState`), updated with `copy()`. Screens call ViewModel functions directly. There are no intent types and no reducer, so this is not MVI.

One-shot messages (snackbars) go through a singleton `SnackbarController` that wraps a `MutableSharedFlow`.

Layering is not uniform:

- `features/appOnboarding` has `data`, `domain` and `presentation` packages.
- `features/profileDetails`, `features/subjects` and `features/examTaking` have only `presentation` code.
- `core/domain` contains the shared `OperationResult` and `Error` types.

Domain on `main`: `UserPreferences`, `UserPreferencesRepository` (interface), `MaturitaValidator`, and the `MaturitaLanguage` and `MaturitaLevel` enums. There is no `Subject` model in the domain package. Two data classes named `Subject` exist in presentation code: `core/presentation/utils/StatickUiSettings.kt` and `features/subjects/presentation/SubjectListTabScreen.kt`.

Data on `main`: `UserPreferencesRepositoryImpl` writes the DataStore keys `language`, `eng_level`, `is_vpu_enabled`, `exam_date` and `setup_completed` in one `edit` transaction.

Platform code (`expect`/`actual`): the platform Koin module (`platformModule`), the empty `DbClient` class, and `Modifier.systemPadding()`.

Database (unmerged `database` branch only): four SQLDelight tables (`ExamEntity`, `ContentUnitEntity`, `TaskEntity`, `UserProgressEntity`), a `DatabaseSeeder` that inserts one exam from a JSON file inside a transaction, and platform database drivers. The seeder is not idempotent: a second run fails on the primary key and is reported as a parse error. See [ARCHITECTURE.md](ARCHITECTURE.md) for the schema.

## Project structure

Tracked files on `main`, at package level:

```
MaturiMate/
├── .gitignore
├── README.md  ARCHITECTURE.md  ROADMAP.md  VISION.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew
├── gradlew.bat
└── composeApp/
    ├── build.gradle.kts
    └── src/
        ├── androidMain/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   └── kotlin/com/madovygabor/maturimate/
        │       ├── MainActivity.kt
        │       ├── core/di/            AppModul.android, DbClient.android, MyApplication
        │       ├── core/presentation/  androidSystemPadding.android
        │       └── features/           createDataStore
        ├── commonMain/
        │   ├── composeResources/       drawable/, values/, values-hu/, values-sk/
        │   └── kotlin/com/madovygabor/maturimate/
        │       ├── App.kt
        │       ├── core/
        │       │   ├── di/             AppModul, DbClient (expect), initKoin
        │       │   ├── domain/error/   Error, OperationResult
        │       │   └── presentation/
        │       │       ├── navigation/ (NavigationRoot, Route, MainFrame/)
        │       │       ├── theme/
        │       │       ├── uiComponents/
        │       │       └── utils/      StatickUiSettings, UiText, snackbar/
        │       └── features/
        │           ├── appOnboarding/  data/, domain/, presentation/
        │           ├── examTaking/     ExamWriteScreen (placeholder)
        │           ├── profileDetails/ presentation/
        │           └── subjects/       presentation/ (static)
        ├── commonTest/                 ComposeAppCommonTest (placeholder)
        └── jvmMain/
            └── kotlin/com/madovygabor/maturimate/
                ├── main.kt
                └── core/               di/ (AppModul.jvm, DbClient.jvm), presentation/
```

## Setup

Requirements:

- JDK 21.
- Android SDK Platform 36 (for the Android target). Put its path in `local.properties`; that file is not committed.
- Android Studio or IntelliJ IDEA with the Compose Multiplatform plugin is optional.

Important: `gradle.properties` line 4 sets `org.gradle.java.home` to a JDK 21 (JBR) path under a local Windows user profile. This path exists only on the original development machine. On any other machine, change that line to your JDK 21 location, or remove it and set `JAVA_HOME`.

Commands (names come from the build configuration; they were not executed while writing this section):

```bash
# Android debug APK
./gradlew :composeApp:assembleDebug

# Desktop application
./gradlew :composeApp:run

# Compile the desktop (JVM) target
./gradlew :composeApp:compileKotlinJvm

# Run the multiplatform tests (one placeholder test)
./gradlew :composeApp:allTests
```

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`.

Desktop packaging is configured for Dmg, Msi and Deb (`composeApp/build.gradle.kts`). Packaging has not been run.

## Known limitations

- Placeholders and mock data: `ExamWriteScreen` is a text placeholder; the History entry is a text placeholder; profile subject statistics are hard-coded values; the subject list and the exam list are static, with no data source.
- Alpha dependencies: `androidx.lifecycle` 2.10.0-alpha07 and Navigation3 1.0.0-alpha06.
- One placeholder test (`ComposeAppCommonTest`, which checks `1 + 2 == 3`). No tests exist for the onboarding, profile, or navigation code. No coverage tool is configured.
- No CI: the repository has no `.github` directory and no workflow files.
- Partial localization: 12 strings in `values`, 3 in `values-hu` and 3 in `values-sk`. Some UI text is hard-coded in Kotlin source.

## Development approach

I design the architecture and make the technical decisions myself. AI assistants help with boilerplate, documentation and drafts and speed up implementation; I review what goes into the repository.

## Documentation

The documents below were written for the earlier plan (digitising official exam papers) and have not been updated yet. Where they disagree with the update at the top of this README, the update wins.

- [VISION.md](VISION.md): product goal, personas and planned scope.
- [ARCHITECTURE.md](ARCHITECTURE.md): technology, layering, navigation and the database design on the `database` branch.
- [ROADMAP.md](ROADMAP.md): milestones with checkbox status.
