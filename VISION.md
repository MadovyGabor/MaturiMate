# MaturiMate — Project Overview & Scope

An offline-first exam preparation application for high school students in Slovakia, replacing static test paper PDFs with interactive, self-grading tests.

---

## 1. Summary & Problem Space

### The Core Problem

* **Procrastination & Exam Anxiety:** High school finals (*Maturita* / *Érettségi*) appear overwhelming when viewed as a multi-year syllabus. Students frequently postpone preparation until the final weeks before central testing.
* **Antiquated Study Workflows:** Past examination sheets exist primarily as non-interactive PDFs on institutional portals. Students must juggle separate question sheets, printed answer booklets, and grading keys, creating high friction for self-testing.
* **Bilingual / Minority Education Gap:** Hungarian-minority students in Slovakia must satisfy dual curriculum tracks: Hungarian Language & Literature, Slovak Language & Slovak Literature, alongside Foreign Languages (B1/B2 English) and Mathematics. Tailored digital tooling for these exact syllabi is virtually non-existent.

### The Solution: MaturiMate

MaturiMate transforms static examination material into a responsive, interactive exam platform. It provides instant validation, eliminates manual grade checking, and runs completely offline on mobile and desktop platforms.

---

## 2. Target Audience & Personas

* **Primary Persona — Minority Track High Schooler:**
  * Bilingual student attending a Hungarian-curriculum grammar or vocational school in Slovakia.
  * Needs focused drills for Slovak language requirements alongside native literature and language exams.

* **Secondary Persona — Standard Curriculum Student:**
  * Slovak track high schooler preparing for state-administered written graduation tests.

* **Universal Student Need:**
  * Wants to evaluate readiness against real exam questions under timed conditions without managing paper sheets or consulting external grading documents.

---

## 3. Product Architecture: Two-Phase Rollout

To ensure early delivery of tangible value, development is strictly split into two progressive versions:

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

---

## 4. Phase 1 (v1.0): Full Interactive Exam Runner (MVP)

Phase 1 delivers a complete, interactive examination environment covering all past central test sheets across every key subject.

### 4.1 Key Capabilities

* **Subject & Past Year Catalog:**
  * Clean selection of examination tracks: Hungarian Language & Literature, Slovak Language, English (B1 & B2 tiers), and Mathematics.
  * Comprehensive coverage of past official exam years loaded dynamically from offline storage.

* **Interactive Exam Taking (`ExamWrite`):**
  * **Unified Reading Comprehension View:** Displays primary source texts (literature excerpts, non-fiction articles, dialogues) side-by-side or paired directly above corresponding question sets.
  * **Input Mechanics:**
    * *Multiple Choice (Single Choice / ABCD):* Instant radio or tile selection.
    * *Short Text / Fill-in:* Free-text input fields for vocabulary, grammar questions, or numerical values with support for synonymous grading keys.
  * **Exam Clock:** Countdown timer calibrated to official regulations (e.g., 100/150 minutes), automatically adjusted for students with special educational accommodations (VPUS / SNI extended duration).

* **Automated Evaluation & Score Summary (`ResultScreen`):**
  * Instant grading upon submission: overall score, percentage, and clear Pass/Fail indication against national passing thresholds.
  * Comprehensive review mode: task-by-task breakdown highlighting correct solutions in green, incorrect student entries in red, and revealing official answer keys.

* **Local Exam History (`HistoryScreen`):**
  * Persistent log of previous attempts, recording completion dates, elapsed time, and percentage outcomes.

---

## 5. Phase 2 (v2.0): Habit Formation & Gamification

Phase 2 builds upon the validated task bank of Phase 1 by introducing daily micro-learning routines inspired by modern retention apps.

### 5.1 Key Capabilities

* **Daily Micro-Learning Bites:**
  * Algorithmic generation of 5-question rapid sessions selected from across the full subject syllabus for 5–10 minute daily study sessions.

* **Daily Streak System:**
  * Daily activity counter accompanied by motivational nudges and scheduled reminders when the streak is at risk.

* **Topic-Based Progression Trees:**
  * Granular practice categorized across the 30 official curriculum topics per subject (e.g., syntax, poetry analysis, trigonometry).

* **Smart Review (Error Vault):**
  * Automatic flagging of missed tasks into a spaced-repetition review queue, re-surfacing problem areas until mastery is demonstrated.

---

## 6. Product Value Summary

> **Scope Summary:**
> 1. Digitisation of national graduation test sheets into interactive, self-grading formats.
> 2. Full offline accessibility on both mobile devices and desktop computers.
> 3. A clear, progressive transition from realistic full exam simulation to daily retention drills.
