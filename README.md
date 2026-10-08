# 🧭 MaturiMate

> **Interactive, offline-first exam preparation platform and daily study companion for high school students in Slovakia.**  
> Built with **Kotlin Multiplatform** and **Compose Multiplatform** to replace static examination paper PDFs with an engaging, immediate-feedback study experience.

---

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.9.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android_%7C_Desktop_(JVM)-3DDC84.svg?logo=android&logoColor=white)](https://github.com/)
[![Database](https://img.shields.io/badge/Database-SQLDelight_2.2.1-blue.svg)](https://cashapp.github.io/sqldelight/)
[![DI](https://img.shields.io/badge/DI-Koin_4.1.1-red.svg)](https://insert-koin.io/)
[![Architecture](https://img.shields.io/badge/Architecture-Clean_%2B_MVI-brightgreen.svg)](#-architecture--system-outline)

---

## 📑 Table of Contents

- [Vision & Value Proposition](#-vision--value-proposition)
  - [The Problem Space](#the-problem-space)
  - [The Solution](#the-solution)
  - [Two-Phase Strategy](#two-phase-strategic-rollout)
- [System Architecture & Tech Stack](#-system-architecture--tech-stack)
  - [Core Technology Stack](#core-technology-stack)
  - [Architectural Blueprint](#architectural-blueprint)
  - [Logical Relational Schema](#logical-relational-schema-sqldelight)
  - [Responsive Adaptive UI](#responsive-adaptive-ui-m3)
- [Roadmap & Milestone Status](#-roadmap--milestone-status)
  - [Status Overview](#status-overview)
  - [Milestone Breakdown](#milestone-breakdown)
- [Project Directory Structure](#-project-directory-structure)
- [Getting Started & Tooling](#-getting-started--tooling)
  - [Prerequisites](#prerequisites)
  - [Build and Run Commands](#build-and-run-commands)
- [Documentation Hub](#-documentation-hub)

---

## 🎯 Vision & Value Proposition

### The Problem Space

* **Procrastination & Exam Anxiety:** High school finals (*Maturita* in Slovakia, *Érettségi* in Hungarian-curriculum schools) span massive, multi-year syllabi. Students easily become overwhelmed and postpone preparation until the final weeks before national testing.
* **Antiquated Study Workflows:** Past examination sheets exist primarily as non-interactive PDFs on institutional portals (e.g., NÚCEM / NIVaM). Self-testing requires juggling separate question sheets, printed answer booklets, and grading keys.
* **Bilingual & Minority Education Gap:** Hungarian-minority students in Slovakia must satisfy dual curriculum tracks: Hungarian Language & Literature, Slovak Language & Slovak Literature, alongside Foreign Languages (B1/B2 English) and Mathematics. Tailored digital tooling for these parallel tracks is practically non-existent.

### The Solution

**MaturiMate** converts static past exam papers into responsive, self-grading interactive tests. It operates completely **offline** across mobile and desktop environments, providing instant scoring, official answer key reveals, and persistent progress tracking.

### Two-Phase Strategic Rollout

```
┌────────────────────────────────────────────────────────────────────────┐
│  PHASE 1 (v1.0): INTERACTIVE EXAM ENGINE (Core Foundation)             │
│  "Select a subject and past year, solve the test, inspect errors"      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│  PHASE 2 (v2.0): GAMIFICATION & MICRO-LEARNING (Habit Engine)          │
│  "5-minute daily bites, streak tracking, topic trees & mistake recall" │
└────────────────────────────────────────────────────────────────────────┘
```

> [!TIP]
> For in-depth product rationale and personas, see [VISION.md](VISION.md).

---

## 🏛️ System Architecture & Tech Stack

### Core Technology Stack

MaturiMate leverages the modern Kotlin Multiplatform ecosystem to share **100% of domain, data, and UI logic** across targets:

| Component | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language & Runtime** | Kotlin Multiplatform (KMP) | `2.2.20` | Shared business logic, models, and platform abstractions |
| **UI Framework** | Compose Multiplatform (CMP) | `1.9.1` | Declarative, shared single-codebase UI for Android and Desktop |
| **Local Database** | CashApp SQLDelight | `2.2.1` | Offline-first SQLite database with type-safe query generation |
| **Dependency Injection** | Koin Multiplatform | `4.1.1` | Loose coupling of repositories, drivers, and ViewModels |
| **Navigation** | JetBrains Navigation3 | `1.0.0-alpha06` | Type-safe, state-preserving multi-stack routing (`navigation3-ui`) |
| **Preferences Storage** | AndroidX DataStore | `1.2.0` | Multiplatform key-value persistence for student preferences |
| **Serialization & Time** | kotlinx.serialization / datetime | `2.2.20` / `0.7.1` | Bundled JSON exam parsing and graduation countdown math |
| **Asynchronous Engine** | kotlinx.coroutines | `1.10.2` | Concurrency, reactive `StateFlow` streams, and platform dispatchers |

### Architectural Blueprint

The application follows a **Feature-First Clean Architecture** with an **MVI / Unidirectional Data Flow (UDF)** presentation model:

* **Domain Layer:** Pure Kotlin models (`UserPreferences`, `Subject`, `Exam`), repository contracts (`ExamRepository`, `UserPreferencesRepository`), and business validation rules (`MaturitaValidator`).
* **Data Layer:** Implementations of repositories, SQLDelight drivers (`android-driver` / `sqlite-driver` for JVM), DataStore preferences, and the transactional `DatabaseSeeder`.
* **Presentation Layer:** Composables emit intents/actions to Koin-injected ViewModels, which process business logic and emit immutable `UiState` via `StateFlow`.

### Logical Relational Schema (SQLDelight)

The offline examination engine is built upon four relational entities:

1. **`ExamEntity`:** Primary exam headers (subject identifier, year, session, total duration, and accommodated VPUS duration).
2. **`ContentUnitEntity`:** Reading passages, poetry, prose, and shared resource texts linked to exams via foreign keys with cascade deletion.
3. **`TaskEntity`:** Individual examination questions, order indices, point weights, interaction types (`CHOICE`, `INPUT`), and JSON metadata for keys and accepted synonyms.
4. **`UserProgressEntity`:** Historical session attempts, recorded student choices, scored points, evaluation timestamps, and review logs.

### Responsive Adaptive UI (M3)

The layout automatically responds to the window size using `BoxWithConstraints` and `LayoutConstants.DesktopThreshold` (`600.dp`):
* **Mobile (< 600 dp):** Standard Material 3 bottom `NavigationBar`.
* **Desktop / Tablet (≥ 600 dp):** Side `NavigationRail` with spacious centered content area.

> [!NOTE]
> Detailed technical diagrams and entity definitions are documented in [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 🗺️ Roadmap & Milestone Status

### Status Overview

* `[x]` = **Completed & Operational**
* `[/]` = **In Progress / Current Active Station**
* `[ ]` = **Planned / Backlog**

---

### Milestone Breakdown

#### 0. Baseline Setup & Existing Implementation `[x]`
- [x] Multiplatform project scaffolding (Android SDK 36, minSdk 24, JVM Desktop).
- [x] Responsive adaptive frame (`MainFrame`) with dual bottom bar / side rail modes.
- [x] User onboarding journey: Language section (`HU` / `SK`), English level (`B1` / `B2`), graduation date picker, and VPUS/SNI accommodation toggle.
- [x] Profile screen with countdown timer and persistent DataStore preferences.
- [x] Root navigation setup using Navigation3 and polymorphic route serialization.

#### Milestone 1: Database Core & Parser Scaffolding (SQLDelight) `[/]` 📍 *CURRENT ACTIVE STATION*
- [x] Implement SQLDelight schema files: `ExamEntity`, `ContentUnitEntity`, `TaskEntity`, and `UserProgressEntity` on `database` branch.
- [x] Configure platform SQLite drivers (`DatabaseDriverFactory` expect/actual) and bind `ExamRepository` in Koin.
- [x] Asset reader for bundled JSON files (`composeResources/files/`) and atomic `DatabaseSeeder`.
- [/] Finalize and merge `origin/database` branch into `main`.

#### Milestone 2: 1st Vertical Slice — Hungarian Language & Literature 🎯 `[/]`
- [x] Ingest and parse official `HU_MJL_2025.json` exam dataset.
- [/] Dynamic subject & year selection UI populated directly from SQLDelight queries.
- [/] **Exam Runner Interface (`ExamWriteScreen`):**
  - [ ] Dedicated scrollable reading passage viewport (prose, poetry, essays).
  - [ ] Multiple-choice (ABCD) and short-text / synonym-tolerant input components.
  - [ ] Official countdown timer with automatic VPUS accommodation extension.
- [ ] **Grading Engine & Result Screen (`ResultScreen`):** Auto-evaluation with item-by-item correct/incorrect breakdown and answer key reveals.
- [ ] **Exam History (`HistoryScreen`):** Persistent attempt logging in `UserProgressEntity`.

#### Milestone 3: 2nd Vertical Slice — Slovak Language & Literature `[ ]`
- [ ] Standardize Slovak exam datasets (`SK_SJL`) into unified JSON schema.
- [ ] Character encoding and Slovak typography validation.
- [ ] Track-based filtering (Hungarian-minority track vs. Slovak national track).

#### Milestone 4: 3rd Vertical Slice — English Language (B1 / B2) `[ ]`
- [ ] Dynamic filtering by student CEFR level (`B1` or `B2`).
- [ ] Cloze (gap-fill) input mechanics.
- [ ] Audio player integration and textual transcript support for listening comprehension.

#### Milestone 5: UI/UX Refinement, Localization & Parity Review `[ ]`
- [ ] Application and content localization (full Slovak & Hungarian UI strings via Compose Multiplatform string resources, localized dates, and curriculum content).
- [ ] Question transition animations and split-screen reading passage scroll synchronization.
- [ ] Comprehensive analytics and performance aggregation on the user profile dashboard.

#### Milestone 6: Final Subject — Mathematics 📐 `[ ]`
- [ ] Geometric charts, coordinate plane graphics, and inline figure rendering.
- [ ] Mathematical formula layout (LaTeX / vector rendering).
- [ ] Numeric grading normalization (decimal commas vs. dots and scientific units).

#### 🔵 Phase 2 (v2.0): Gamification & Habit Formation `[ ]`
- [ ] **Daily Micro-Lessons:** Algorithmic 5-question daily sessions across syllabus topics.
- [ ] **Streak Engine:** Consecutive day tracking with motivational notifications.
- [ ] **30-Topic Progress Trees:** Modular skill trees covering the official curriculum.
- [ ] **Error Vault:** Spaced-repetition review of missed examination questions.

> [!TIP]
> Track granular implementation progress in [ROADMAP.md](ROADMAP.md).

---

## 📂 Project Directory Structure

```
MaturiMate/
├── .github/                     # GitHub workflows and issue templates
├── gradle/
│   └── libs.versions.toml       # Centralized version catalog
├── composeApp/
│   ├── src/
│   │   ├── commonMain/
│   │   │   ├── composeResources/# Images, localized strings, and bundled exam JSONs
│   │   │   ├── sqldelight/      # SQLDelight database definitions (.sq)
│   │   │   └── kotlin/com/madovygabor/maturimate/
│   │   │       ├── App.kt       # Root composable & theme scaffold
│   │   │       ├── core/        # Shared database factories, DI, navigation & theme
│   │   │       │   ├── database/
│   │   │       │   ├── di/
│   │   │       │   ├── domain/
│   │   │       │   └── presentation/
│   │   │       └── features/    # Feature-first modules
│   │   │           ├── appOnboarding/   # First-run setup & user preferences
│   │   │           ├── subjectsAndExams/# Subject catalog, seeder & exam repo
│   │   │           ├── examTaking/      # Exam runner, timer & question inputs
│   │   │           └── profileDetails/  # Profile, countdown & preferences
│   │   ├── androidMain/         # Android application entry & platform drivers
│   │   └── jvmMain/             # Desktop JVM application entry & platform drivers
├── ARCHITECTURE.md              # Technical architecture specification
├── ROADMAP.md                   # Vertical slice development roadmap
├── VISION.md                    # Product vision and strategic concept
└── README.md                    # Project overview & documentation index
```

---

## 🚀 Getting Started & Tooling

### Prerequisites

* **JDK 21** or **JDK 17** configured as the default Gradle JDK.
* **Android Studio Ladybug (or newer)** or **IntelliJ IDEA Ultimate / Community** with the Compose Multiplatform plugin.
* Android SDK Platform 36 installed (Android build).

### Build and Run Commands

Clone the repository and run the Gradle tasks from your terminal:

```bash
# Clone the repository
git clone https://github.com/MadovyGabor/MaturiMate.git
cd MaturiMate
```

#### Android Application

```bash
# Build Android debug APK
./gradlew :composeApp:assembleDebug

# On Windows PowerShell
.\gradlew.bat :composeApp:assembleDebug
```

#### Desktop (JVM) Application

```bash
# Run the Desktop app locally
./gradlew :composeApp:run

# On Windows PowerShell
.\gradlew.bat :composeApp:run

# Compile Desktop JVM target classes
./gradlew :composeApp:compileKotlinJvm
```

#### Tests & Verification

```bash
# Run multiplatform unit tests
./gradlew :composeApp:allTests
```

---

## 📚 Documentation Hub

* [🧭 Product Vision & Concept (`VISION.md`)](VISION.md) — Problem background, personas, and long-term vision.
* [🏛️ Technical Architecture (`ARCHITECTURE.md`)](ARCHITECTURE.md) — Comprehensive tech stack, Clean Architecture, and SQLDelight schemas.
* [🗺️ Development Roadmap (`ROADMAP.md`)](ROADMAP.md) — Detailed milestone tracking across all subject vertical slices.

---

<p align="center">
  <b>MaturiMate</b> • Built with ❤️ for students preparing for graduation.
</p>
