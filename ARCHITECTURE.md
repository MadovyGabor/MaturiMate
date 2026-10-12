# MaturiMate — Technical Architecture & System Outline (Phase 1)

This document provides a technical overview of the system architecture, foundational tools, tech stack, and component responsibilities based on the current Kotlin Multiplatform implementation.

---

## 1. Technology Stack & Rationale

The project is built on the modern **Kotlin Multiplatform (KMP)** ecosystem, currently targeting **Android** and **Desktop (JVM)** runtimes:

* **Kotlin Multiplatform (KMP — v2.2.20):**
  * **Role:** Shares 100% of business logic, domain models, validation, and persistence across target platforms.
  * **Targets:** Android (minSdk 24, compileSdk 36, targetSdk 36) and Desktop JVM (packaged for Windows MSI, macOS DMG, Linux DEB).

* **Compose Multiplatform (CMP — v1.9.1):**
  * **Role:** Declarative user interface layer. Shares the exact same UI codebase across mobile and desktop environments.

* **Material 3 (M3) Adaptive Layout:**
  * **Role:** Responsive UI layout adapting dynamically to viewport width:
    * **Mobile (< 600 dp):** Renders a standard bottom `NavigationBar` within the main scaffold.
    * **Desktop & Large Displays (≥ 600 dp):** Switches dynamically to a side `NavigationRail` with spacious content centering (`LayoutConstants.DesktopThreshold = 600.dp`).

* **CashApp SQLDelight (v2.2.1):**
  * **Role:** Type-safe, offline-first SQLite database.
  * **Platform Drivers:** `android-driver` on Android, `sqlite-driver` on JVM/Desktop, along with coroutines extension bindings. Guarantees that graduation exams, content units, and questions remain instantly accessible without an internet connection.

* **Koin Dependency Injection (v4.1.1):**
  * **Role:** Lightweight multiplatform dependency injection (`koin-core`, `koin-compose`, `koin-compose-viewmodel`). Decouples and injects repositories, platform database drivers (`expect`/`actual`), business validators, and ViewModels.

* **JetBrains Navigation3 for Compose Multiplatform (v1.0.0-alpha06):**
  * **Role:** Type-safe, polymorphic, state-preserving routing system (`navigation3-ui` with `lifecycle-viewmodel-navigation3`). Manages back-stack serialization and seamless screen transitions between top-level flows and fullscreen exam experiences.

* **AndroidX DataStore Preferences (v1.2.0):**
  * **Role:** Multiplatform key-value persistence for student profile and preferences (graduation language track `HU`/`SK`, English tier `B1`/`B2`, target exam date, and special accommodations / VPU status).

* **kotlinx.serialization (v2.2.20) & kotlinx.datetime (v0.7.1):**
  * **Role:** Dynamic JSON parsing of bundled exam databases, countdown calculation toward graduation day, and active test timer tracking.

* **kotlinx.coroutines (v1.10.2):**
  * **Role:** Asynchronous concurrency, reactive StateFlow streams, and platform event dispatchers (including `kotlinx-coroutines-swing` on Desktop).

---

## 2. Architecture & Modular Structure

The codebase is organized using a **Feature-First Clean Architecture** approach combined with the **MVI / Unidirectional Data Flow (UDF)** pattern:

```
composeApp/src/commonMain/kotlin/com/madovygabor/maturimate/
├── core/
│   ├── database/        # Platform database driver contracts & factory
│   ├── di/              # Shared & platform Koin modules
│   ├── domain/          # Core cross-cutting domain logic
│   └── presentation/    # Shell, Navigation3 root, M3 Theme & Responsive MainFrame
└── features/
    ├── appOnboarding/   # First-launch setup, language/level selection, preferences
    ├── subjectsAndExams/# Subject catalog, past year list, database seeder, exam repo
    ├── examTaking/      # Live test runner, timers, question layouts
    └── profileDetails/  # User settings, accommodations, study profile
```

### Clean Architecture Layers:

* **Domain Layer:**
  * Contains pure business models (`UserPreferences`, `Subject`, exam models), repository interfaces (`ExamRepository`, `UserPreferencesRepository`), and business rules (`MaturitaValidator`).
  * Free of platform-specific frameworks or UI dependencies.

* **Data Layer:**
  * Implements repository interfaces (`ExamRepositoryImpl`, `UserPreferencesRepositoryImpl`).
  * SQLDelight database query wrappers and offline database driver bindings.
  * `DatabaseSeeder`: Atomically parses bundled JSON exam assets and populates the SQLite database within a single transaction.
  * Preferences persistence powered by DataStore.

* **Presentation Layer:**
  * **Unidirectional Data Flow (UDF):** UI components dispatch intents/events to Koin-injected ViewModels.
  * ViewModels maintain and publish an immutable `UiState` via `StateFlow`.
  * Composable screens consume states reactively and render Material 3 UI accordingly.

---

## 3. Logical Data Schema (v1 Focus)

The offline examination engine is built upon four primary relational entities within the SQLDelight schema (`com.madovygabor.maturimate.database`):

### 1. Exam Header (`ExamEntity`)
* Primary identifier (`id`, e.g., `"HU_MJL_2025_MARCH"`).
* Subject specification (`subjectId`, e.g., `"HU_MJL"`, `"SK_SJL"`, `"HU_MAT"`, `"AJ_B2"`).
* Temporal metadata: `year` (e.g., 2025) and `session` (e.g., `"MARCH"`).
* Time constraints: `totalMinutes` (standard duration) and `vpuMinutes` (extended accommodation duration).
* Quantitative metrics: `taskCount`.

### 2. Reading & Resource Blocks (`ContentUnitEntity`)
* Primary source material linked to an exam via foreign key (`examId` with `ON DELETE CASCADE`).
* Categorization: `type` (e.g., `"TEXT"`, `"AUDIO"`, `"GAP_FILL"`).
* Metadata & Rich Text: `title`, `author`, `description`, `contentHtml` (formatted for UI display), and `mediaUrl`.
* Extensible metadata: `sharedOptionsJson` for structured task pools.

### 3. Tasks / Questions (`TaskEntity`)
* Individual question record linked to parent exam (`examId`) and optional parent resource (`contentUnitId` with `ON DELETE CASCADE`).
* Display order: `orderIndex`.
* Interaction category: `type` (e.g., `"CHOICE"`, `"INPUT"`, `"CHOICE_5"`).
* Content: `question`, point weight `points`, and optional image locator `imageUrl`.
* Type-specific payload: `metadataJson` containing answer choices, correct keys, and accepted synonym variants for automated grading.

### 4. User Progress & History (`UserProgressEntity`)
* Tracks individual exam sessions: composite primary key `(attemptId, taskId)`.
* Denormalized lookup: `examId`.
* Student response data: `userAnswer` (selected choice index or submitted free text).
* Evaluation outcome: `isCorrect` (Boolean) and `earnedPoints` (Integer).
* Audit tracking: `timestamp` (epoch milliseconds) for recording attempt history.

---

## 4. Extensibility & Phase 2 Compatibility

* **Zero-Migration Exam Expansion:**
  * Adding new past exam years or updated examination sheets requires zero SQL schema migrations.
  * Bundled JSON descriptors placed in application resources are parsed and imported atomically by `DatabaseSeeder`.

* **Phase 2 Ready (Habit & Gamification Engine):**
  * The v1 data foundation directly supports Phase 2 features:
    * The task repository feeds algorithmic 5-question daily micro-learning bites.
    * The granular `UserProgressEntity` history directly feeds the Spaced Repetition **Error Vault** for targeted review.
    * Existing topic categorization supports curriculum progression trees without breaking database restructuring.
