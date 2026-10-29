# MaturiMate — Project Overview & Scope

> **Status:** This document describes the intended product and its planned scope. It was updated on 2026-10-22 after the change of plan described in the [README](../README.md): the first version uses only content written for this project, and the official exam papers moved to a later version. For what is implemented on `main`, see the "Current status" section of the README. For the order of the work, see the [ROADMAP](ROADMAP.md). Sections 4 and 5 describe planned work.

A planned offline-first learning app for high school students in Slovakia who prepare for the graduation exam (*maturita*). It offers short lessons and practice tasks, so that preparation becomes a daily habit instead of a last-minute effort.

---

## 1. Summary & Problem Space

### The Core Problem

* **Procrastination & Exam Anxiety:** High school finals (*Maturita* / *Érettségi*) appear overwhelming when viewed as a multi-year syllabus. Students frequently postpone preparation until the final weeks before central testing.
* **No Small, Interactive Steps:** Study material is usually long (textbooks, workbooks, PDF sheets) and gives no instant feedback. There is little that lets a student study for five minutes, check the answer immediately and see progress.
* **Bilingual / Minority Education Gap:** Hungarian-minority students in Slovakia must satisfy dual curriculum tracks: Hungarian Language & Literature, Slovak Language & Slovak Literature, alongside Foreign Languages (B1/B2 English) and Mathematics. Tailored digital tooling for these exact syllabi is virtually non-existent.

### The Solution: MaturiMate

MaturiMate breaks the syllabus into short lessons, each followed by practice tasks with instant, self-graded feedback. It runs completely offline on mobile and desktop platforms, and it tracks progress and a daily streak to build a study habit. These are targets; the current state of the code is described in the README.

All content is written for this project. Following the exam's task formats, topic structure and difficulty is part of the aim; the texts and task wording are our own.

---

## 2. Target Audience

MaturiMate is for high school students in Slovakia who prepare for the graduation exam:

* **Students at schools with Hungarian as the language of instruction.** They prepare for Hungarian Language & Literature and for Slovak Language, alongside a foreign language and Mathematics.
* **Students at schools with Slovak as the language of instruction.** They prepare for the Slovak Language & Literature exam, alongside a foreign language and Mathematics.
* **Any student who wants short, clear lessons and realistic practice with instant feedback,** without juggling paper sheets or external answer keys.

---

## 3. Product Architecture: Phased Rollout

Development is split into progressive versions, so that tangible value reaches students early. The order of subjects is Hungarian first, then Slovak, English and Mathematics.

```
┌────────────────────────────────────────────────────────────────────────┐
│  V1: MICRO-LEARNING ON OWN CONTENT (Core Foundation)                   │
│  "Short lessons, practice tasks, exam-style practice, daily streak"    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│  LATER: MORE SUBJECTS, PLATFORMS, ACCOUNTS AND SYNC                    │
│  "Slovak, English, Mathematics; desktop, iOS, web; login and sync"     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│  V2: OFFICIAL EXAM CONTENT (only with permission)                      │
│  "Official papers and listening tasks, if the exam authority agrees"   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. V1: Micro-Learning on Own Content — Planned

V1 is planned to deliver a complete learning loop on one platform (Android first) and one subject (Hungarian first), built so that more subjects are added as new data, not new code. None of the capabilities below is complete on `main`.

### 4.1 Key Capabilities

* **Subjects, Topics and Lessons:**
  * Subjects are split into topics, and topics into short lessons (a few minutes each) written in the app's own text format.
  * The number of topics per subject is not fixed yet.

* **Practice Tasks with Instant Feedback:**
  * *Multiple Choice (ABCD):* instant selection and feedback.
  * *Short Text / Fill-in:* free-text input for vocabulary, grammar questions or numerical values, with support for accepted answer variants.
  * Reading passages are shown together with the tasks that belong to them.

* **Exam-Style Practice:**
  * A practice mode that combines a passage with its tasks under a time limit, graded on submission, with a task-by-task review (correct answers in green, incorrect entries in red). The tasks are our own, written in the style of the exam.

* **Streak and Progress:**
  * Daily activity counter and per-topic progress, stored locally. Reminders and motivational nudges may follow.

* **Smart Review (Error Vault), later addition:**
  * Missed tasks are flagged into a review queue and re-surfaced until mastered.

* **Local History:**
  * A persistent log of previous attempts with date, time spent and percentage.

### 4.2 Content Sources

* **Own experience:** lessons and tasks based on what I learned in my own graduation exam preparation, including material I worked out myself in high school.
* **Teachers:** content written together with teachers, only with their written permission.
* **AI:** AI helps to draft and extend lessons and tasks, and a human reviews everything before it goes into the app.
* **Legally available material:** for example public-domain texts. Published textbooks and workbooks are not copied.

Every task records its source, so the origin of the content is always clear.

### 4.3 Offline-First Principle

The app works without a network. Content ships inside the app as a read-only database and is replaced as a whole when a newer version is available. The user's progress lives in a separate local database that a content update never touches. The details are in the [ROADMAP](ROADMAP.md#data-architecture-offline-first).

---

## 5. Later and V2 — Planned

### 5.1 More Subjects, Platforms, Accounts

* Slovak, English (B1/B2) and Mathematics follow Hungarian on the same engine.
* Desktop, iOS and web follow Android.
* Login and sync of user data come before the public release of the first subject. The app stays fully usable without an account, and until then progress is stored on the device only.

### 5.2 V2: Official Exam Content

The official exam papers, answer keys and audio are copyrighted, so they can only be used with permission from the exam authority (NIVAM). The request is planned for when the app works and there are real usage numbers to show. If the permission is refused, the app continues on its own content.

---

## 6. Product Value Summary

> **Scope Summary:**
> 1. Short lessons and self-graded practice tasks, written for the Slovak graduation exam, with instant feedback.
> 2. Full offline accessibility, with a daily streak and progress to build a study habit.
> 3. Own content with a clear origin, so the app can be released without waiting for a third party. Official exam content may follow later, with permission.
