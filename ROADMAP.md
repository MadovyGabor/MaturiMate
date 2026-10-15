# MaturiMate Roadmap

Legend: `[x]` = done and present on `main`; `[/]` = in progress, or implemented only on the unmerged `database` branch (not merged); `[ ]` = planned, not started on `main`.

---

## 0. Baseline on `main`

* [x] **Project scaffolding:** Kotlin 2.2.20, Compose Multiplatform 1.9.1, Koin 4.1.1, Material 3.
* [x] **Adaptive frame (`MainFrame`):** bottom `NavigationBar` below 600 dp and side `NavigationRail` at 600 dp and above (`core/presentation/MainFrame.kt`, `StatickUiSettings.kt:33`).
* [x] **User onboarding:** welcome screen, language selector (HU/SK), English level selector (B1/B2), date picker, and VPUS/SNI toggle. The VPUS toggle is stored but has no effect yet.
* [x] **Profile screen:** graduation countdown, settings saved in DataStore, and dialog editing. The subject statistics shown on this screen are mock data (`ProfileViewModel.kt:164-171`).
* [x] **Initial shells:** subject tab list and multi-stack back stack navigation. The subject list is static, and the exam list is not connected to a data source.

---

## Milestone 1: Database Core & Parser Scaffolding (SQLDelight) *(in progress)*

Establish the local database and JSON loading pipeline before building interactive exam screens. All items in this milestone exist only on the unmerged `database` branch.

* [/] **1.1. Remote database branch integration (`origin/database` ➔ `main`):**
  * [/] Integrate SQLDelight schema files `ExamEntity`, `ContentUnitEntity`, `TaskEntity`, and `UserProgressEntity` *(on the database branch, not merged)*.
  * [/] Register platform SQLite drivers (Android and JVM) and bind `ExamRepository` in Koin modules *(on the database branch, not merged)*.
  * [ ] Merge the `database` branch into `main`.

* [/] **1.2. Universal JSON parser & seeder:**
  * [/] Read exam JSON from shared Compose resources (`composeResources/files/`) *(on the database branch, not merged; the file is not on `main`)*.
  * [/] Seed the database with `DatabaseSeeder` inside a single transaction *(on the database branch, not merged)*.

---

## Milestone 2: First Full Vertical Slice — HUNGARIAN LANGUAGE & LITERATURE

Goal: complete the end-to-end user loop (selection, exam taking, auto-grading, history) using Hungarian tests.

* [/] **2.1. Hungarian exam parser & ingestion:**
  * [/] Parse the bundled `HU_MJL_2025.json` into SQLDelight tables via `DatabaseSeeder` *(on the database branch, not merged)*.
  * [ ] Validate relationships between reading source texts (`ContentUnit`) and task items (`Task`). No validation code exists on `main`.

* [/] **2.2. Dynamic subject & year selection UI:**
  * [/] Connect `SubjectViewModel` to SQLDelight through `ExamRepository.getExamsBySubject()` *(on the database branch, not merged)*.
  * [ ] Finalize exam summary card rendering (year, task count, time limit) and pass the selected `examId` to `ExamWriteScreen`. Not present on `main`.

* [ ] **2.3. Hungarian exam runner interface (`ExamWriteScreen`):** `ExamWriteScreen` is a placeholder on `main`.
  * [ ] *Reading passage view:* scrollable viewport showing the source text next to the questions.
  * [ ] *Input components:* multiple-choice ABCD selection and short text fields.
  * [ ] *Exam timer:* countdown based on the official time limit, with the extended time for VPUS accommodations.

* [ ] **2.4. Grading engine & summary view (`ResultScreen`):**
  * [ ] Compare submitted answers against answer keys and accepted synonym lists.
  * [ ] Show the total score, percentage, and a task-by-task breakdown (correct and incorrect answers, with the answer key for incorrect ones).

* [ ] **2.5. Persistence & review (`HistoryScreen`):**
  * [ ] Save completed attempts to `UserProgressEntity`.
  * [ ] Show history entries with completion date, percentage, and time spent.

---

## Milestone 3: Second Vertical Slice — SLOVAK LANGUAGE & LITERATURE

Reuse the exam engine from Milestone 2 and adapt the content parsing and curriculum-specific structures.

* [ ] **3.1. Slovak exam data pipeline:**
  * [ ] Structure past Slovak graduation tests (`SK_SJL`) in the standard JSON format.
  * [ ] Verify character encoding, diacritics, and the layout of reading comprehension blocks.

* [ ] **3.2. Curriculum & filtering configuration:**
  * [ ] Make sure grammar and literature tasks render correctly for both choice and short-answer components.
  * [ ] Filter available tests by the student's selected language track (Hungarian or Slovak).

---

## Milestone 4: Third Vertical Slice — ENGLISH LANGUAGE (B1 / B2)

Add difficulty levels and language-specific task formats.

* [ ] **4.1. Level-based filtering:**
  * [ ] Show B1 or B2 test papers according to the level in the student's profile.

* [ ] **4.2. Language exercise formats:**
  * [ ] Support cloze (fill-in-the-blank) text inputs.
  * [ ] Add audio playback or text transcripts for listening comprehension tasks.

---

## Milestone 5: UI/UX Refinement, Polish & Localization

Stabilise all implemented subjects, bring the two languages to parity, and polish UI interactions before adding mathematical notation.

* [ ] **5.1. UI & motion polish:**
  * [ ] Transition animations between tasks.
  * [ ] Smooth scrolling in the split-screen reading passage view on mobile and desktop.

* [ ] **5.2. Analytics overview:**
  * [ ] Aggregate completion trends and average scores per subject on the profile screen. The current statistics are mock data.

* [ ] **5.3. App & content localization:**
  * [ ] Complete localization of the application UI strings in Slovak and Hungarian through Compose Multiplatform `composeResources` (`values`, `values-hu`, `values-sk`). On `main`, `values` has 12 strings, and `values-hu` and `values-sk` have 3 each.
  * [ ] Localized date and time formatting that follows the student's preferences.
  * [ ] Content-level localization and bilingual metadata for curriculum tracks and subject guidelines.

---

## Milestone 6: Final Exam Subject — MATHEMATICS

Deferred to the final milestone because of its rendering and verification requirements.

* [ ] **6.1. Equation & diagram integration:**
  * [ ] Show geometric sketches and coordinate charts next to problem statements.
  * [ ] Render mathematical formulas and expressions (vector or graphic assets, or LaTeX parsing).

* [ ] **6.2. Math evaluation engine:**
  * [ ] Accept numeric answers with decimal separators (comma and dot normalisation) and unit variations.
  * [ ] Import past mathematics test series and verify their answer keys.

---

## Phase 2 (v2.0): Gamification & Micro-Learning (Future Scope)

Add habit-forming features on top of the Phase 1 exam bank.

* [ ] **2.1 Daily micro-lessons:** algorithmic selection of 5-question daily sessions across syllabus topics.
* [ ] **2.2 Streak engine:** consecutive-day counter with visual indicators and local reminder notifications.
* [ ] **2.3 Topic progress trees:** modular drills grouped by curriculum topic.
* [ ] **2.4 Smart review / error vault:** automatic flagging and spaced repetition of missed exam questions.
