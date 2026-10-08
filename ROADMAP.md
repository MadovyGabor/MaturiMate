# 🧭 MATURIMATE — Development Plan & Vertical Slice Subject Roadmap

Legend:
* `[x]` = Completed & Operational
* `[/]` = In Progress / Immediate Next Step
* `[ ]` = Planned / Backlog

---

## 0. Baseline Setup & Existing Implementation

The foundational scaffolding and onboarding journeys are functional on the `main` branch:

* [x] **Project Scaffolding:** Kotlin Multiplatform 2.2.20, Compose Multiplatform 1.9.1, Koin 4.1.1, and Material 3 environment.
* [x] **Adaptive Frame (`MainFrame`):** Dual navigation pattern with a bottom `NavigationBar` on mobile (< 600 dp) and a side `NavigationRail` on desktop/tablet viewports (≥ 600 dp).
* [x] **User Onboarding:** Welcome screen, language section selector (HU/SK), English tier selector (B1/B2), date picker, and VPUS/SNI special accommodation toggle.
* [x] **Profile Screen:** Graduation countdown counter, persistent settings storage in DataStore, and modal dialog editing.
* [x] **Initial Shells:** Subject tab list navigation and multi-stack backstack router.

---

## Milestone 1: Database Core & Parser Scaffolding (SQLDelight) 📍 *CURRENT STATUS*

Establish the local database and JSON loading pipeline before building interactive exam screens.

* [/] **1.1. Remote Database Branch Integration (`origin/database` ➔ `main`):**
  * [x] Integrate SQLDelight schema files: `ExamEntity`, `ContentUnitEntity`, `TaskEntity`, and `UserProgressEntity` *(implemented on `database` branch)*.
  * [x] Register platform-specific SQLite drivers (Android and JVM) and bind `ExamRepository` within Koin dependency injection modules.
  * [/] Merge/rebase `database` branch into `main`.

* [x] **1.2. Universal JSON Parser & Seeder:**
  * [x] Implement an asset reader to ingest structured exam JSON files from shared Compose resources (`composeResources/files/`).
  * [x] Execute initial database seeding via `DatabaseSeeder` with transaction atomicity.

---

## Milestone 2: First Full Vertical Slice — HUNGARIAN LANGUAGE & LITERATURE 🎯

Goal: Complete the end-to-end user loop (Selection ➔ Exam Taking ➔ Auto-grading ➔ History) using Hungarian tests.

* [x] **2.1. Hungarian Exam Parser & Ingestion:**
  * [x] Bundled and parsed `HU_MJL_2025.json` into SQLDelight tables via `DatabaseSeeder`.
  * [x] Validated relationships between reading comprehension source texts (`ContentUnit`) and associated question items (`Task`).

* [/] **2.2. Dynamic Subject & Year Selection UI:**
  * [x] Connect `SubjectViewModel` to SQLDelight via `ExamRepository.getExamsBySubject()`.
  * [/] Finalize exam summary card rendering (year, question count, time limit) and route passing to `ExamWriteScreen` with selected `examId`.

* [/] **2.3. Hungarian Exam Runner Interface (`ExamWriteScreen`):** 📍 *Immediate Next Step*
  * [ ] *Reading Passage View:* Dedicated scrollable viewport displaying literary prose, essays, or poems alongside active questions.
  * [ ] *Input Components:* Multiple-choice ABCD radio groups and short text fields.
  * [ ] *Exam Timer:* Countdown clock reflecting official time allowances with automatic extension for VPUS accommodations.

* [ ] **2.4. Grading Engine & Summary View (`ResultScreen`):**
  * [ ] Compare submitted student entries against answer keys and accepted synonym lists.
  * [ ] Display final score, percentage, and item-by-item breakdown (green for correct, red for incorrect with answer key reveal).

* [ ] **2.5. Persistence & Review (`HistoryScreen`):**
  * [ ] Persist completed attempt records into `UserProgressEntity`.
  * [ ] Query and display history entries showing completion date, percentage, and time spent.

---

## Milestone 3: Second Vertical Slice — SLOVAK LANGUAGE & LITERATURE

Re-use the completed exam engine; adapt content parsing and curriculum-specific structures.

* [ ] **3.1. Slovak Exam Data Pipeline:**
  * [ ] Structure past Slovak graduation tests (`SK_SJL`) into standardized JSON format.
  * [ ] Verify character encoding, diacritics, and reading comprehension block layouts.

* [ ] **3.2. Curriculum & Filtering Configuration:**
  * [ ] Ensure grammar and literature items render properly across choice and short-answer components.
  * [ ] Filter available tests according to the student's selected language section (Hungarian vs. Slovak track).

---

## Milestone 4: Third Vertical Slice — ENGLISH LANGUAGE (B1 / B2)

Integrate difficulty tiers and language-specific task patterns.

* [ ] **4.1. Level-Based Filtering:**
  * [ ] Display B1 or B2 test papers dynamically matching the user's profile setting.

* [ ] **4.2. Language Exercise Formats:**
  * [ ] Support cloze/fill-in-the-blank text inputs commonly used in foreign language exams.
  * [ ] Prepare audio player integration or textual transcripts for listening comprehension sections.

---

## Milestone 5: UI/UX Refinement, Polish & Localization

Stabilize all implemented subjects, ensure bilingual platform parity, and polish UI interactions before introducing complex mathematical notation.

* [ ] **5.1. UI & Motion Polish:**
  * [ ] Add transition animations during question progression.
  * [ ] Smooth scrolling on split-screen reading passage viewports across mobile and desktop.

* [ ] **5.2. Analytics Overview:**
  * [ ] Aggregate completion trends and average scores per subject in user profile dashboards.

* [ ] **5.3. App & Content Localization:**
  * [ ] Full localization of application UI strings across Slovak and Hungarian via Compose Multiplatform `composeResources` (`values`, `values-hu`, `values-sk`).
  * [ ] Localized date and time formatting aligned with student preferences.
  * [ ] Content-level localization and bilingual metadata for curriculum tracks and subject guidelines.

---

## Milestone 6: Final Exam Subject — MATHEMATICS 📐

Deferred to the final milestone due to specific rendering and verification requirements.

* [ ] **6.1. Equation & Diagram Integration:**
  * [ ] Display geometric sketches and coordinate system charts inline with problem prompts.
  * [ ] Render mathematical formulas and expressions (vector/graphic assets or LaTeX parsing).

* [ ] **6.2. Math Evaluation Engine:**
  * [ ] Handle numeric answer inputs with decimal separators (comma vs. dot normalization) and unit variations.
  * [ ] Ingest past math test series and verify grading keys.

---

## 🔵 PHASE 2 (v2.0): Gamification & Duolingo-Style Micro-Learning (Future Scope)

Layer habit-forming mechanics on top of the validated Phase 1 exam bank.

* [ ] **2.1 Daily Micro-Lessons:** Algorithmic extraction of 5-question daily sessions across syllabus topics.
* [ ] **2.2 Streak Engine:** Consecutive day counter with visual flame indicators and local reminder notifications.
* [ ] **2.3 Topic Progress Trees:** Targeted modular drills categorized under the 30 official curriculum topics.
* [ ] **2.4 Smart Review / Error Vault:** Automated flagging and spaced repetition of missed exam questions.
