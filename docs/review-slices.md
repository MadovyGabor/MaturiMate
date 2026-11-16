# Review slices: the order to go through

The owner's checklist for Milestone 13 ([ROADMAP](ROADMAP.md#milestone-13-review-and-refinement-by-feature-slice)). Every slice is a small group of code and screens that belong together, so only that part of the app has to be in the head at once. Each slice goes through the nine steps of [review-procedure.md](review-procedure.md) (version A for screens, version B for server and sync) on the milestone's branch `chore/review-and-refinement` (small commits, the slice in the scope), and is ticked here when its step 9 is done.

Paths are inside `shared/src/commonMain/kotlin/com/madovygabor/maturimate/` unless said otherwise. Sizes are lines, state of 2026-11-13.

**Why this order:** from what every screen stands on (the frame), through the first-use path (onboarding, account, settings), the learning path from the outside in (the path, the lesson, the tasks, the rounds), then what is built on top of the learning data (practice, home, profile), and last the backend that carries it all. Shared `App…` components are polished in the first slice that uses them and only reused after.

---

## Part 1: The frame and the first use

* [ ] **S1. App frame and start-up**
  * *Holds:* the theme, the adaptive frame and navigation, the start decision, the app language, the config.
  * *Files:* `core/presentation/theme/`, `core/presentation/navigation/` (`NavigationRoot`, `MainFrame`, `Route`), `core/presentation/AppViewModel.kt`, `core/presentation/locale/` and `AppLocale.*`, `core/network/`, `core/config/`, `core/database/LocalUserProvider.kt`, `core/di/`.
  * *Also here:* the Slovak-instead-of-Hungarian interface bug (13.5 of the old list); the dead placeholder `features/examTaking/ExamWriteScreen.kt` and its route.
  * *Version:* A.

* [ ] **S2. Onboarding questions**
  * *Holds:* welcome, teaching language, role, subjects and English level, graduation date, daily goal, the plan summary, and the resuming of an unfinished onboarding.
  * *Files:* `features/appOnboarding/` (`presentation/`, `OnboardingResume`, `OnboardingStep`), `features/userProfile/` (profile, `Subjects`, `ExamDates`, `UserProfileRepositoryImpl`).
  * *Components polished first:* `AppButton`, `AppChoiceCard`, `AppChoiceChip`, `AppProgressBar`, `AppDatePickerDialog`, `OnboardingScaffold`.
  * *Also here:* after a change of the teaching language the subjects are not asked again (bug).
  * *Version:* A.

* [ ] **S3. Account: sign-up, sign-in, guest**
  * *Holds:* the account screen (e-mail with the 6-digit code, Google), sign-in, the forgotten password, the guest who looks around, what happens to the guest's data when an account is made.
  * *Files:* `features/auth/` (presentation, `AuthRepository`, `AccountSetup`, `SupabaseAccountRepository`, `AccountRules`), `GoogleSignIn.kt`.
  * *Components polished first:* `AppTextField`, `AppCheckRow`.
  * *Also here:* Google sign-in on desktop is not tried yet (bug).
  * *Version:* A.

* [ ] **S4. Reminder**
  * *Holds:* the reminder step of the onboarding, the daily notification, the notification permission, the alarm after a restart.
  * *Files:* `features/appOnboarding/presentation/reminder/`, `features/reminder/` (`ReminderMessage`, `ReminderScheduler`, `ReminderSync`), `NotificationPermission.*`, `androidApp/…/reminder/` (`ReminderReceiver`, `BootReceiver`, `AndroidReminderScheduler`), `AppTimePickerDialog`.
  * *Why apart:* it is Android platform code with its own risks (battery savers, permissions); small.
  * *Version:* A.

* [ ] **S5. Settings**
  * *Holds:* the settings screen and its actions: name, password, sign-out, reset of the progress, language changes, the account deletion and the export (the buttons; the server part is S16).
  * *Files:* `features/profileDetails/presentation/` (`SettingsScreen` 605, `SettingsViewModel`, `SettingsDialogs`), `features/auth/data/AccountSettingsImpl.kt`, `AccountSettings`, `ProgressRepository.resetProgress`.
  * *Components polished first:* `AppSettingsRow`.
  * *Watch:* `SettingsScreen.kt` is the largest file of the app.
  * *Version:* A.

---

## Part 2: Learning

The learning screens share `TestScreen.kt` (572) and the round engine, so S8 and S9 may be taken together if cutting them apart costs more than it gives.

* [ ] **S6. Subjects and the path**
  * *Holds:* the subject list, the big parts, the topic list with its progress rings, the path of a topic with the line that fills, locked and open steps.
  * *Files:* `features/learning/presentation/` (`SubjectListScreen`, `TopicScreen`, `LearningNavigation`), `features/learning/domain/LearningCourse.kt`, the models, `features/learning/data/` (`ContentRepositoryImpl`, `ContentMapper`), `core/database/ContentDatabaseProvider.kt`, `features/content/data/InstalledContentPacks`.
  * *Components polished first:* `AppPathStep`, `AppProgressRing`.
  * *Version:* A.

* [ ] **S7. The lesson**
  * *Holds:* the lesson pages, the table of contents (sheet or side list), the reading time, the boxes, the app text format.
  * *Files:* `features/learning/presentation/` (`LessonScreen` 297, `LessonViewModel`).
  * *Components polished first:* `AppLessonBox`, `AppSegmentedProgress`, the rich text component.
  * *Version:* A.

* [ ] **S8. The task formats**
  * *Holds:* the seven formats (multiple choice, true or false, short answer, matching, ordering, gaps from a word list, typed gaps), the instant feedback and the answer checking.
  * *Files:* the format screens of `TestScreen.kt`, `features/learning/domain/AnswerChecker.kt`.
  * *Components polished first:* `AppAnswerOption`, `AppTrueFalseCard`, `AppFeedbackSheet`, `AppGapField`, `AppGapSlot`, `AppMatchPair`.
  * *Version:* A.

* [ ] **S9. Test rounds and results**
  * *Holds:* the `MINI`, `TOPIC` and `FINAL` tests, how a round is made (required tasks plus random ones), passing and trying again, the result screen, the review of the wrong answers, saving the attempt, the answer and the unit progress.
  * *Files:* `features/learning/presentation/TestSessionViewModel.kt` (263) and the result part of `TestScreen.kt`, `features/learning/domain/` (`RoundSource`, `RoundPlanner`, `TestRecorder`), `features/learning/data/ProgressRepositoryImpl.kt`, `ProgressMapper`.
  * *Components polished first:* `AppResultRow`.
  * *Version:* A.

---

## Part 3: What is built on the learning data

* [ ] **S10. Practice**
  * *Holds:* the practice tab: plain practice and its picker, the mistake practice, the weak and strong topics, the old material to review, the practice round and result.
  * *Files:* `features/practice/` (all: `PracticeScreen` 319, `PracticePickerScreen` 292, `PracticeViewModels`, `PracticeCatalog`, `PracticePlanner`, `PracticeRoundSource`, `ReviewList`, `TopicScores`).
  * *Version:* A.

* [ ] **S11. Home and the daily goal**
  * *Holds:* the home screen (greeting, streak, days until the exam, the goal ring, "Folytatás", the suggestion, the last activities), the study clock and the streak, the exam countdown and the official exam dates.
  * *Files:* `features/learning/presentation/HomeScreen.kt` (427), `HomeViewModel`, `ActivityTexts`, `features/learning/domain/` (`HomeOverview`, `RecentActivities`, `StudyTracker`, `StudyClock`), `features/userProfile/` (`ExamCalendar`, `ExamDateRepositoryImpl`).
  * *Components polished first:* `AppGoalRing`, `AppWeekStrip`, `AppActivityCard`, `AppInfoChip`.
  * *Watch:* one time zone for the whole day logic; the streak "in danger" until the goal is reached.
  * *Version:* A.

* [ ] **S12. Profile and history**
  * *Holds:* the profile (streak, study time, solved tasks, progress per subject and big part), the history with its filter, the guest's sign-up card.
  * *Files:* `features/profileDetails/` (`ProfileOverviewScreen` 412, `ProfileOverviewViewModel`, `HistoryScreen`, `ProfileNavigation`, `ProfileOverview`, `ActivityHistory`).
  * *Components polished first:* `AppStatCard`, `AppSubjectProgressCard`. `AppProgressCard` and `AppTag` go to the slice where a search shows they are used most.
  * *Version:* A.

---

## Part 4: The backend (version B)

Before S13 the owner's open steps of Milestone 12 must be done: the backup secrets and the restore test (12.2), the migrations on the live project, the Vault secret, the manual two-device test.

* [ ] **S13. Content download**
  * *Holds:* the full content download after sign-up and for updates, the starter pack, the swap of the content file, the mobile-data question, the limit per day.
  * *Files:* `features/content/` (`ContentUpdater`, `WorkerPackSource`, `InstalledContentPacks`), `workers/content-link/`, `20261115120000_content_download.sql`, the `publish` command of `tools/content-pipeline`, `docs/runbooks/cloudflare.md`.
  * *Version:* B.

* [ ] **S14. Sync of the user data**
  * *Holds:* the engine, its triggers, the merge rules, the guest's merge into an account, the offline sign-out, the reset of the progress on the server.
  * *Files:* `features/sync/` (`SyncEngine`, `SyncTriggers`, `SyncStoreImpl` 269, `SyncModels`, `SupabaseSyncRepository`), the migrations `20261112120000` and `20261118120000`, `supabase/tests/sync_test.sql`.
  * *Version:* B.

* [ ] **S15. Server security and account data**
  * *Holds:* the row level security of every table, the server functions, the account deletion with the consent archive, the data export, the clean-up of logs, the backups and the restore.
  * *Files:* `supabase/migrations/` (the 17 files), `supabase/tests/rls_test.sql`, `dbServer/`, `.github/workflows/backup.yml`, `docs/runbooks/` (`backup.md`, `restore.md`).
  * *Version:* B.

---

## The closing round

* [ ] **S16. What no slice owns**
  * `database/` (SQLDelight schemas and the migration test), `androidApp/` (manifest, R8, the activity) and `desktopApp/` (`main.kt`, packaging), `tools/content-pipeline` and `tools/content-viewer`, CI (the layout of the jobs: group 15 of [review-checklist.md](review-checklist.md)), the build files and dependencies, dead code, the documentation check.
  * Android device tests in CI (waiting for an emulator since 6a.5), a test that the phone database upgrades from the version of the alpha, the shared profile flow in the coverage, start-up time on a weak phone, a last look at the whole screen checklist.
