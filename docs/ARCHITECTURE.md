# MaturiMate Architecture

This document describes the architecture of the code in this repository. Its stack and module layout were last checked on 2026-11-01, after the toolchain update (Milestone 6); the other sections were last checked on 2026-10-28, after Milestone 1 (core and database).

For the current status of each part, see the "Current status" section of the [README](../README.md).

> **2026-10-22: change of plan.** The first version uses only content written for this project (short lessons and practice tasks); the official exam papers moved to a later version (see the [README](../README.md)). The exam-centred database of the earlier plan (unmerged `database` branch) is not used; its drivers and Gradle setup were reused.

---

## 1. Technology Stack

| Component | Version (from `../gradle/libs.versions.toml`) | Notes |
|---|---|---|
| Kotlin Multiplatform | Kotlin 2.4.21 | Android target and desktop `jvm()` target in `:shared` and `:database` |
| Gradle, Android Gradle Plugin | 9.8.2, 9.5.0 | Built-in Kotlin; shared modules use the Android-KMP library plugin (`com.android.kotlin.multiplatform.library`); see [toolchain-agp9.md](../llm/toolchain-agp9.md) |
| Compose Multiplatform | 1.12.2 | Shared UI for Android and desktop (Jetpack Compose 1.12.1 on Android) |
| Material 3 (Compose Multiplatform) | 1.9.0 | Own version line; the newest stable |
| Navigation3 for Compose Multiplatform | 1.2.0 | |
| AndroidX Lifecycle (ViewModel) | 2.11.0 | |
| Compose Hot Reload | 1.2.0 | `:desktopApp` (`./gradlew :desktopApp:hotRun`) |
| Koin | 4.2.2 | `koin-core`, `koin-compose`, `koin-compose-viewmodel`, plus Android artifacts |
| kotlinx.serialization | 1.12.0 | Polymorphic navigation routes; `content-manifest.json` (`:database`); content files (`../tools/content-pipeline`) |
| kotlinx.datetime | 0.8.0 | Used for `LocalDate` and time zone handling |
| kotlinx.coroutines | 1.11.0 | `StateFlow`, `MutableSharedFlow`, and the Swing dispatcher on desktop |
| SQLDelight | 2.4.1 | Content and user databases (`:database` module) |
| PostgreSQL JDBC driver | 42.7.14 | `../tools/content-pipeline` only (server copy of the content) |
| PostgreSQL | 17 (Supabase), 16 (Docker) | Server database on Supabase; a local copy in Docker (`../dbServer/docker-compose.yml`) for development and the importer |
| supabase-kt (Auth, ComposeAuth) | 3.8.0 | Sign-up, sign-in and password reset; Ktor 3.6.1 with the OkHttp engine on Android and desktop |
| Supabase CLI | 2.122.0 | Schema migrations (`../supabase/migrations`) |

Android uses `minSdk` 26 (needed by supabase-kt), `compileSdk` 37, and `targetSdk` 37, with `jvmTarget` `JVM_11`. The build runs on JDK 17 or newer (CI uses 21). Desktop packaging (`:desktopApp`) is configured for DMG, MSI, and DEB formats, but has not been built as part of this document.

---

## 2. Module Layout

Gradle modules:

| Module | Kind | Contents |
|---|---|---|
| `:shared` | Kotlin Multiplatform library (Android, desktop) | UI, features, Koin wiring, platform (expect/actual) code, Compose resources |
| `:androidApp` | Android app | `MainActivity`, `MyApplication`, manifest, launcher icons, splash theme |
| `:desktopApp` | Kotlin/JVM desktop app | `main()`, the window, desktop packaging, Compose Hot Reload |
| `:database` | Kotlin Multiplatform library (Android, desktop) | SQLDelight schemas of the content and user databases, platform drivers, content installer |
| `:tools:content-pipeline` | Kotlin/JVM command-line tool | Validates the content files; exports `content.db`; imports the server copy into PostgreSQL |

Other folders: `supabase/` (Supabase project settings `config.toml` and the schema in `migrations/`, 27 tables), `dbServer/` (local PostgreSQL in Docker with the same schema and a stand-in for Supabase Auth; see [dbServer/README.md](../dbServer/README.md)), `content/` (lesson and task files: a separate private repository cloned here, gitignored), `docs/` (vision, roadmap and this document).

The app source tree, relative to `../shared/src`:

```
commonMain/kotlin/com/madovygabor/maturimate/
├── App.kt
├── core/
│   ├── config/             # SupabaseConfig.kt (public values only)
│   ├── database/           # ContentDatabaseProvider, LocalUserProvider
│   ├── di/                 # Koin modules (AppModul.kt, initKoin.kt)
│   ├── domain/error/       # Error.kt, OperationResult.kt
│   ├── network/            # Supabase client factory
│   └── presentation/       # MainFrame.kt, navigation/, theme/, uiComponents/, utils/, locale/
└── features/
    ├── appOnboarding/      # presentation/ (one folder per question screen)
    ├── auth/               # data/, domain/, presentation/ (account and sign-in screens)
    ├── examTaking/         # ExamWriteScreen.kt (placeholder)
    ├── learning/           # data/, domain/, presentation/ (subjects tab: subjects, parts, topics, path)
    ├── profileDetails/     # presentation/ only
    └── userProfile/        # data/, domain/ (profile, subjects, exam dates)
commonMain/composeResources/files/   # bundled content.db + content-manifest.json (generated, gitignored)
androidMain/kotlin/...      # Android actuals (AppModul.android.kt, ...)
jvmMain/kotlin/...          # Desktop actuals (AppModul.jvm.kt, ...)
jvmTest/kotlin/...          # Repository, ViewModel and screen tests, and the whole onboarding flow
```

The app modules hold only the entry points: `../androidApp/src/main` (`MainActivity.kt`, `core/di/MyApplication.kt`, `AndroidManifest.xml`, `res/`) and `../desktopApp/src/main` (`main.kt`).

---

## 3. Layering

`features/appOnboarding` has the full `data/`, `domain/`, and `presentation/` split. `features/learning` has `data/`, `domain/` (section 9.3) and `presentation/`. The other features contain only presentation code:

* `features/profileDetails` contains only `presentation/`.
* `features/examTaking` contains a single placeholder screen, `ExamWriteScreen.kt`.
* `features/practice` has `domain/` and `presentation/`; it reads through the repositories of `features/learning`.

`userProfile` holds the profile, settings and role of the local user: the domain has `UserProfile`, `TeachingLanguage`, `UserRole`, `DataError` and the `UserProfileRepository` interface, the data layer `UserProfileRepositoryImpl` over the user database (`user_profile`, `user_settings`, `user_role`; saving a role replaces the student, learner or teacher role and keeps roles the server gives). `appOnboarding` has the onboarding screens in its presentation layer. Each question screen has its own small ViewModel; the one-answer questions (language, role, and the next simple ones) extend `OnboardingQuestionViewModel<T>`, which loads the saved answer, keeps the selection, saves on Next and shows a snackbar when saving fails, so a screen's ViewModel only says how its answer is read and saved.  `auth` holds accounts: the domain has `AuthState`, `AuthError` and the `AuthRepository` interface (sign-up with a 6-digit code, sign-in, sign-out, password reset in two steps: the code, then the new password), the data layer `AuthRepositoryImpl` over the Auth plugin of the Supabase client, `SupabaseAccountRepository` over Postgrest (the account's profile, settings, role, subjects and consents on the server) and `AccountSetupImpl`, which runs after a sign-up or a sign-in: it moves the guest's rows to the account (`LocalUserProvider.adoptAccount`), then saves the profile, answers and consents in one server call (`save_sign_up`, one transaction), or downloads the account's profile. Supabase classes stay in the data layer and in `core/network/`.

**The learning path (Milestone 7, step 7.1).** `LearningCourse` (`features/learning/domain`) builds what the subjects tab shows from the `ContentRepository` and the `ProgressRepository`: the subjects of the installed content with the progress in them, the big parts of a subject, the topics below a topic with their progress, and the steps of a leaf topic (lessons and tests) with the state of each (done, current, to do; the first step not done is the current one). A topic with topics below it is a `TopicLevel.Branch` (its topics, then its `FINAL` test), a leaf topic a `TopicLevel.Path`; the tree can be any depth. `SubjectListViewModel` combines it with the user's subjects (`CatalogSubject`); `TopicViewModel` reads one level (`TopicTarget`). Both read again whenever their screen comes back, so progress made deeper in shows. The screens are `SubjectListScreen` and `TopicScreen`, built from `AppProgressCard` (the big card of a subject, a part or a topic: its name, what it is about, what is in it, and the gold progress bar with its value as text) and `AppPathStep` (a node on the vertical line and a card; the line is drawn by the steps). `AppProgressRing` is a finished component that no screen uses at the moment. A tap on a lesson opens it, a tap on a test opens the test; a step ahead is locked until the ones before it are done (`StepState.TODO`: a lock on the node, a tap says so), and the final test of a parent topic is locked until everything below it is done. A lesson that is followed by a test ends with "Mehet a teszt" and opens that test.

**The lesson (step 7.2).** `LessonViewModel(unitId)` reads a `SUMMARY` unit (its pages and blocks) and holds the page being read; the reading time of a page is its words divided by 200 a minute, at least one. Finishing the last page saves the unit as `COMPLETED` through `ProgressRepository.saveUnitProgress` (keeping an earlier start time) and then emits `finished`, which closes the screen; a failed save keeps the lesson open and shows a snackbar. `LessonScreen` shows one page: title, paragraphs (`AppRichText`, the `reading` style) and boxes (`AppLessonBox`), a segmented progress bar (`AppSegmentedProgress`), next and previous page by the buttons or by swiping, and the table of contents: a bottom sheet on a phone, a list at the side from `breakpoint-expanded` (840 dp) up. A page that does not fit scrolls. The text format of the content (`**bold**`, `*italic*`, paragraphs) is turned into styled text by `richText` in `core/presentation/utils/RichText.kt`; the gap markers `[[n]]` belong to the task screens.

**The tasks of a test (step 7.3).** `AnswerChecker` (`features/learning/domain`) decides whether an `Answer` is right, for every task format: the choice formats compare the chosen option label with the key of each slot, the typed formats compare the text with the accepted answers of each slot (spaces at the ends and doubled spaces and the case do not count, a missing accent does). It returns the result per slot, so a task with several gaps or pairs can say which one is wrong, and it gives the hint of a typed task (first letter and length). `TestSessionViewModel(unitId)` runs a round of a test unit: `RoundPlanner` chooses the tasks (every required task, `isCore`, and random ones of the others to fill `roundSize`, in random order; a switched off task is never chosen), then choose or write, check, feedback, next, and at the end a `Result` state with how every task went. `TestRecorder` keeps what the round leaves in the user database, in the background and after the earlier save (it goes on when the screen is closed at once): the attempt (`UNIT_ROUND`, `IN_PROGRESS`, then `FINISHED` with the points or `ABANDONED` when the student leaves), the answer to every task (`attempt_answer`: right or wrong, the points, the answer as short text such as `1=B;2=A`), the progress of the task (right and wrong counts, the right answers in a row, `MASTERED` after three in a row; `next_review_at` stays empty until the mistake practice) and, when the round is finished and passed (every task of the round right), the unit as `COMPLETED`; a round that is not passed is kept, but the unit stays open and the next step of the path stays locked (a unit that is done stays done). A task with several slots earns points by the share of right slots. `TestScreen` draws the task by its format, the result (a passed or not passed round, what is needed to pass, `AppResultRow` for every task, a wrong one opening to the right answer, the `why` and the explanation, and "Újra" as the main button when the round is not passed, else "Vissza az útvonalra") and, after checking, an `AppFeedbackSheet` with the right answer, the `why` of the option the student chose, and the explanation.

**One round, two kinds (step 9a.2).** How a round begins and ends is a `RoundSource` (`features/learning/domain`): it starts the round (`RoundStart`: the tasks and the attempt, or empty, or failed), records each answer, and finishes or abandons it. `UnitRoundSource` is the test of the path described above. `TestSessionViewModel` runs any source; the task-by-task logic is the same for every kind, and its `close()` decides what the close button does: a test is abandoned and the screen closes, a practice round with answers is finished with them and its result is shown (practice has no fixed end). `TestUiState.Result` carries the `RoundKind`, and the result of practice speaks of no passing ("Kész a kör!", "Még egy kör", "Vissza a Gyakorláshoz"). `RoundScreen` wires any round view-model to `TestContent`.

**Free practice (step 9a.2).** `PracticeCatalog` (`features/practice/domain`) builds the practice picker from the content and the progress: each subject with its parts and topics (`PracticeNode`, any depth), whether a topic is **finished** (it has units and all of them are `COMPLETED`), how many different tasks it can give, and the finished topics with tasks below a node (`leafIds`, what a tick on a part or a subject chooses). `pool(topicIds)` gives the different active tasks of the tests of the finished topics chosen (a task in two tests counts once; the final test of a parent topic adds nothing of its own). `PracticePlanner` takes n of them at random. `PracticeRoundSource` starts a `FREE_PRACTICE` attempt (`TestRecorder.startPractice`, no unit and no topic) and finishes it with `finishPractice`, which never marks a unit as done. The practice tab (`PracticeScreen`, `PracticeViewModel`) shows the free practice card (its button is off until a topic is finished) and a "Hamarosan" card; the picker (`PracticePickerScreen`, `PracticePickerViewModel`) holds the selection, the folds, the length of the round (10, 20, 50 or a custom number from 1 to 999) and the exact task count of the selection, and is built from `AppCheckRow` (a tri-state tick box row) and `AppChoiceChip`. The view-model of a practice round is the round view-model with a `PracticeRoundSource`, registered in Koin under the name `PRACTICE_ROUND`.

**Mistake practice (step 9a.3).** `ProgressRepository.mistakeTaskIds()` reads the tasks whose latest answer is wrong from `user_task_progress` (`times_wrong > 0 AND correct_streak = 0`). `PracticeCatalog.mistakes()` builds the same tree as the free practice, but a topic counts its mistakes and can be chosen when it has one, finished or not; `mistakePool(topicIds)` and `allMistakes()` give the tasks. Only published topics count: the catalog walks the tree from the subjects, because `ContentRepository.topic(id)` also returns a topic that is not published. `PracticeRoundSource` has a `PracticeMode` (`FREE`, `MISTAKES`); in mistake mode it starts a `MISTAKE_PRACTICE` attempt, an empty `topicIds` means every mistake, and with none left it returns `RoundStart.NoMistakes` (`TestUiState.NoMistakes`). The picker takes the mode (`PracticePickerViewModel(catalog, mode)`, `Route.PracticePicker(mistakes)`), and so does the round (`Route.PracticeRound(topicIds, count, mistakes)`). The home screen gets the number of mistakes through `HomeOverview(mistakeCount)`, wired to the catalog in Koin, and its card opens the practice tab.

**Weak and strong topics (step 9a.4).** `TopicScores` (`features/practice/domain`) reads every answered task with whether its latest answer is right (`ProgressRepository.answeredTasks()`, from `user_task_progress`), counts them per published topic (`publishedTree()`, the walk shared with the catalog), and gives at most three weak topics (below `WEAK_BELOW`, 80%) and three strong ones, from `MIN_SEEN` (5) tasks seen. The practice of a weak topic is `PracticeMode.TOPIC`: `PracticePlanner.plan(required = the topic's mistakes, pool = PracticeCatalog.topicPool(topic))`, every mistake first, filled up to 30 with the topic's other tasks, finished or not. `Route.PracticeRound` carries the `PracticeMode`.

**Review of old material (step 9a.5).** `ReviewList` (`features/practice/domain`) takes, for every finished published topic, the newest of its units' `completed_at` and of its tasks' newest answers (`ProgressRepository.lastAnswerTimes()`, a `GROUP BY` on `attempt_answer`), counts the calendar days since in the user's time zone, and lists the topics due (`DUE_AFTER_DAYS`) the oldest first. `PracticeMode.REVIEW` asks `ROUND` random tasks of the topic (`topicPool`) and starts a `REVIEW` attempt with the topic (`TestRecorder.startReview`). `RecentActivities` lists finished reviews as `ActivityKind.REVIEW` with their topic. With this card the practice tab has every card of Milestone 9a; the "Hamarosan" card is gone. From `breakpoint-medium` (600 dp of content width) `PracticeContent` lays the cards out in two columns, as the profile does: the free practice and the mistakes on the left, the topic scores and the old material on the right (step 9a.6).

**Activities in the lists (step 9a.2).** `RecentActivities` lists the finished lessons, test rounds and practice rounds, newest first, as `RecentActivity` with an `ActivityKind` (`LESSON`, `TEST`, `PRACTICE`, `MISTAKE_PRACTICE`); abandoned rounds are never listed. A practice round has no unit and no title of its own; its `subjectIds` come from its answers (the topic of each task answered, walked up to the part, which holds the subject). The home screen, the profile and the full history draw every kind with `AppActivityCard` (an `ActivityIcon` per kind) through the helpers in `ActivityTexts.kt` (`activityTitle`, `activityPlace`, `practiceResult`); the history filter has `PRACTICE` for both practice kinds. `HomeOverview` takes the newest activity **with a unit** for "Folytatás", so a practice round never moves the path.

**The answer is kept per slot (step 7.4).** A slot is a gap, a pair or a position (`AnswerChecker.slots(task)`); `TestUiState.Ready` holds the chosen option label of each slot (`choices`) and the written text (`texts`); multiple choice, true or false and short answer use slot 1, and the task can be checked when every slot is filled. Matching has an active pair that gets the next answer (`activate`, `pick`); ordering numbers the picked steps in the order they are picked, a step picked again goes back and the rest move up; a gap from a list is filled with the next word and emptied by a tap on it (`pick`, `clear`); typed gaps take text in their place (`typeIn`). The seven formats are drawn with `AppAnswerOption` (multiple choice and ordering), `AppTrueFalseCard` (at the bottom of the screen, above the check button), `AppTextField` (short answer, read-only after checking, green or red), `AppMatchPair` with `AppChoiceChip`s (matching; a wrong pair shows what belongs to it), and `AppGapSlot` or `AppGapField` in the sentence (gaps; the gaps are inline content of the text, split at `[[n]]` by `splitGaps`, so the text flows around them).

---

## 4. Presentation Pattern

Presentation follows MVVM with `StateFlow`. A ViewModel holds a private `MutableStateFlow` and exposes it as a read-only `StateFlow` through `asStateFlow()`. The state is an immutable `data class` that is updated with `copy()`. Example: `SignInViewModel` declares `private val _state = MutableStateFlow(SignInUiState())` and exposes `val state = _state.asStateFlow()`. A ViewModel that only shows saved data maps the repository's flow with `stateIn` (`PlanViewModel`, `ProfileViewModel`).

The app does not use intents, a reducer, or a single-state MVI loop. Screens call ViewModel methods directly.

One-shot messages (for example, snackbars) go through a singleton `SnackbarController` (`core/presentation/utils/snackbar/SnackbarController.kt:11`), which uses a `MutableSharedFlow` (line 14).

**Profile.** `ProfileScreen` keeps a small back stack of its own: the answers, or one onboarding question opened to change its answer. Inside it `LocalEditingAnswer` is true, so `OnboardingScaffold` shows a plain back button and no step list, and the main button says "Mentés". The open main tab is held in `App`, outside the `key(...)` rebuild, so a language changed in the profile keeps the profile open.

---

## 5. Navigation

Navigation uses JetBrains Navigation3 with `@Serializable` routes. `Route` is a `sealed interface Route : NavKey` (`core/presentation/navigation/Route.kt:8`). Routes include `Route.ExamWrite(val examId: String)` (`Route.kt:21`). The back stack is held with `rememberNavBackStack`.

**App language and start screen.** `App()` reads `AppViewModel`, which follows the saved profile: the app language (the teaching language; the phone's language until one is picked) and whether the onboarding is finished. The language is applied with `LocalAppLocale` (an `expect` in `core/presentation/locale/`: `Locale.setDefault` on desktop, the overridden `LocalConfiguration` on Android), and `key(language, onboardingCompleted)` builds the screens again when either changes. The root route is `Main` once `user_profile.onboarding_completed_at` is set, otherwise `AppOnboarding`. Android keeps its splash screen until the profile has been read once.

**Onboarding resume.** `onboardingBackStack(profile)` (`features/appOnboarding/presentation/OnboardingResume.kt`) turns the saved answers into the onboarding back stack: the welcome screen, then every answered step, then the first unanswered one. So the onboarding continues where it stopped after a restart or a language change, and Back still walks through the earlier answers. Each step says how its route looks and when it is answered (`OnboardingStep.route`, `isAnswered`); `nextRoute(step, role)` gives the screen after a step for the role, and the onboarding ends after the last step that has a screen.

`NavigationRoot.kt` registers the polymorphic subclasses for `Main`, `AppOnboarding`, and `ExamWrite` (lines 28-32). `ExamWrite` renders `ExamWriteScreen()` (lines 64-66). `ExamWriteScreen` currently displays the text "Exam Write Screen" (`features/examTaking/ExamWriteScreen.kt:25`). The `History` tab shows a placeholder text (`core/presentation/navigation/MainFrame/MainFrameNavigation.kt:61`).

The subjects tab has its own back stack inside the tab (`LearningNavigation`, `features/learning/presentation`): `Route.Main.Subject.Selection` (the subjects), `Route.Main.Subject.Parts(subjectId)` (the big parts of a subject) and `Route.Main.Subject.Topic(topicId)` (the topics below a topic, or the path of a leaf topic, one route for every depth). Each entry has its own ViewModel store. A lesson and a test are root routes, `Route.Lesson(unitId)` and `Route.Test(unitId)`, like `ExamWrite`: they open over the whole screen, without the navigation bar, and closing them returns to the path, which reads its progress again.

The practice tab (`Route.Main.Practice`) opens two more root routes: `Route.PracticePicker` (the picker) and `Route.PracticeRound(topicIds, count)` (a free practice round). Starting a round replaces the picker with the round, so the end of the round goes back to the practice tab, which reads again whether there is anything to practice.

---

## 6. Responsive Layout

`MainFrame.kt` wraps its content in `BoxWithConstraints` (line 38) and sets `isDesktop = maxWidth > LayoutConstants.DesktopThreshold` (line 40). `LayoutConstants.DesktopThreshold` is `600.dp` (`StatickUiSettings.kt:33`). Below the threshold, the frame uses a `NavigationBar` (line 84). At or above the threshold, it uses a `NavigationRail` (line 46).

---

## 7. Dependency Injection and Platform Code

Koin modules are in `core/di/`. The platform module is declared as an `expect` in `AppModul.kt:25`, with actuals in `AppModul.android.kt:11` and `AppModul.jvm.kt:12`. The platform modules provide `DatabaseDriverFactory` (from `:database`); the shared module provides the user database, `ContentDatabaseProvider` (bound as `ContentDatabaseSource`), `ContentRepository`, `ProgressRepository`, `UserProfileRepository`, the Supabase client and `AuthRepository`.

The Supabase client is built in `core/network/SupabaseClientFactory.kt` from `SupabaseConfig`, with the Auth plugin (PKCE flow) and ComposeAuth (native Google sign-in on Android). `platformAuthConfig()` is an `expect`: on desktop the browser returns from an OAuth sign-in to a local server on port 47613 (`DESKTOP_AUTH_CALLBACK_PORT`); Android needs nothing yet. The session is kept by the library's default storage: SharedPreferences on Android, Java Preferences on desktop.

`rememberNotificationPermissionRequest` (`core/presentation/NotificationPermission.kt`) is an `expect`: Android asks for `POST_NOTIFICATIONS` from Android 13; desktop needs no permission.

`Modifier.systemPadding` is declared as an `expect` in `core/presentation/androidSystemPadding.kt:5`, with actuals in the Android and JVM source sets (`androidSystemPadding.android.kt:6`, `androidSystemPadding.jvm.kt:5`).

On desktop, the app data directory is `System.getProperty("user.home")` plus `.maturimate` (`AppModul.jvm.kt`); the databases are stored there. On Android, the databases are in the app's `databases/` folder.

---

## 8. Profile and Settings Storage

The profile and settings live in the local user database, in the `user_profile` and `user_settings` rows of the local user (a guest id from `LocalUserProvider` until sign-up). `UserProfileRepositoryImpl` saves one field at a time, so the onboarding can save after every screen; the first save creates both rows and takes the time zone from the phone. Saving the teaching language also sets `ui_language`. `profile` is a flow over the joined rows (`selectUserProfile` in `Profile.sq`) and emits again after every change.

The start screen is chosen in `MainActivity` and the desktop `main.kt`: home when `onboarding_completed_at` is set, otherwise the onboarding. DataStore and the VPUS/SNI toggle were removed in step 5.1–5.2.

---

## 9. Data Architecture

Offline-first, with three databases:

| Database | Where | Contents | Written by |
|---|---|---|---|
| Content (SQLite, `content.db`) | Phone, read-only | 15 content tables: subjects, topic tree, learning units, tasks with options, keys and accepted answers, tags | The exporter; installed as a whole file |
| User (SQLite, `maturimate_user.db`) | Phone | 10 user tables: profile, role, settings, subjects, rounds (`attempt`) and their answers, unit and task progress, streak, daily activity; plus the phone-only `local_user` (whose rows these are) and `local_device` (the id of this installation) | The app |
| Server (PostgreSQL) | Supabase; a local copy in Docker | The 28 tables of `../supabase/migrations`: synced progress, plus a copy of the content. Accounts are Supabase Auth users (`auth.users`) | The importer (content); the app through Supabase, protected by row level security (accounts, progress) |

The PostgreSQL schema in `supabase/migrations` comes from the original 30-table design: the design's own sign-in tables (`app_user`, `user_session`, `password_reset_token`) are replaced by Supabase Auth, every `user_id` is a `uuid` referencing `auth.users`, and `user_consent` also records the age confirmation (`AGE_16`). Schema changes are new migration files; an applied migration is never edited. The SQLite schemas repeat it table by table with the same table and column names; only the types and the constraints SQLite cannot express differ (timestamps as epoch milliseconds, dates as `YYYY-MM-DD` text, booleans as `0/1`, no PL/pgSQL triggers, no foreign keys from user tables to content tables or to `auth.users`). Two columns were added after the original design: `user_subject.level` (English level) and `user_profile.onboarding_completed_at` (see [DECISIONS](DECISIONS.md) 2026-10-31). A change to the user database also gets a SQLDelight migration (`database/src/commonMain/sqldelight/user/migrations/<version>.sqm`, the same change as the Supabase migration), so the database on an installed app is upgraded instead of rebuilt.

**The user database since the freeze (step 11.1, decisions of 2026-11-10).** The user tables are shaped for the sync (Milestone 12); from here every change is a migration pair (a phone `.sqm` and a Supabase migration), and `SchemaMigrationTest` fails when a schema migrated from version 1 differs from a fresh one.

* **A round (`attempt`) has an id made by the device** (a UUID text on the phone, `uuid` on the server); `attempt_answer` points at it. Two devices of one account never collide.
* **`updated_at`** (epoch milliseconds on the phone, `timestamptz` on the server) is on `user_profile`, `user_settings`, `user_unit_progress`, `user_streak` and `activity_log`. On the phone it is a column default plus `updated_at = …` in every `UPDATE` query (SQLDelight 2.4 does not read triggers); the server has a trigger. `attempt` and `attempt_answer` do not change after the round, `user_subject` is replaced whole.
* **`user_profile.progress_reset_at`** is set by "Haladás újrakezdése": the sync ignores progress older than it, because deleted rows cannot be seen by a sync.
* **`activity_log` is one row per device and day** (`device_id` from `local_device`); the screens read the sum of a day (`selectActivity` groups by day).
* **`user_task_progress` is a phone-side cache, not synced**: the counters of the mistakes and the topic scores, which the sync rebuilds from `attempt_answer`. The server has no such table.
* **A unit is `IN_PROGRESS` as soon as its lesson or test is opened** (`TestRecorder.markOpened`, `LessonViewModel`), `COMPLETED` when done. Readers treat only `COMPLETED` as done.
* All server times of the user tables are `timestamptz`; the progress tables have DELETE policies for the owner.
* **One time zone:** the device's. `AppViewModel` writes it to `user_settings.timezone` at every start (`UserProfileRepository.refreshTimeZone`); `StudyTracker.timeZone()` is what the streak, the goal, the home and profile screens, `ReviewList` and `ActivityHistory` count their days in.
* **Writes that must not lose a count are done in the database:** `ProgressRepository.recordTaskAnswer` (the answers and the right answers in a row, in SQL), `updateStreak` (the streak read and written in one transaction; the rule stays in `StudyTracker`). A round left `IN_PROGRESS` when the app was killed is closed as `ABANDONED` when the next round starts.

### 9.1 Content pipeline

```
              ┌─→ export → content.db + content-manifest.json → bundled in the app (later: downloaded)
Files (Git) ──┤
              └─→ import → PostgreSQL (server copy; the user tables there reference content by foreign key)
```

* The lesson and task files are the source of truth: Markdown for `SUMMARY` lessons (pages and boxes, see the [content pipeline](../tools/content-pipeline/README.md#lesson-files-pages-and-boxes)), JSON for tasks and `PRACTICE` units (`MINI`, `TOPIC`, `FINAL`), one folder per topic. All ids are written in the files and never change. Format and rules: [tools/content-pipeline/README.md](../tools/content-pipeline/README.md).
* Both commands use the same loader and `ContentValidator` (format rules of the design, text format, leaf topics, provenance). Nothing is written if a check fails.
* The exporter creates the file with the generated `ContentDatabase.Schema` from `:database`, so the file matches what the app reads. It sets `PRAGMA user_version` to the schema version and writes a manifest with `content_version`, size and SHA-256. The same files give a byte-identical file. With `--previous`, it fails if a row of the previous release is missing: rows are switched off (`is_active`, `is_published`), never removed.
* The importer upserts by id in one transaction, replaces the rows a task owns, deactivates rows missing from the files, and moves the identity sequences past the imported ids.

### 9.2 Content in the app

* `bundleContent` (Gradle task of `:tools:content-pipeline`) exports into `../shared/src/commonMain/composeResources/files`. These files are gitignored; a build without them runs, but has no content.
* `ContentInstaller` (`:database`) installs a content file only if its schema version equals the app's, its size and SHA-256 match the manifest, and its `content_version` is higher than the installed one. It writes a temporary file and renames it over the old one, manifest last. The user database is never touched.
* `ContentDatabaseProvider` (`core/database`) installs the bundled file on first use and opens the installed one; it returns null when there is no content. Checked on the Android emulator (API 37): installed on first start, "up to date" after a restart.
* Not built yet: downloading newer content files. They will go through the same installer; the open content driver must be closed before a swap.

### 9.3 Repositories

`features/learning/domain` holds the domain models (`Subject`, `Topic`, `LearningUnit`, `Task`, `Attempt`, `UnitProgress`, `TaskProgress`, `Streak`, `DailyActivity`), the `ContentRepository` and `ProgressRepository` interfaces, and `LearningError` (`NO_CONTENT`, `NOT_FOUND`, `DATABASE_ERROR`). Results use `OperationResult`.

`features/learning/data` holds `ContentRepositoryImpl` (reads through `ContentDatabaseSource`), `ProgressRepositoryImpl` and the mappers. The generated SQLDelight classes do not leave the data layer. In the domain, task keys reference options by label. The phone has one local user: `LocalUserProvider` (`core/database`) stores a guest id (`guest-` and a random UUID) on the first start, and sign-up replaces it with the Supabase account id (step 6.5). `user_id` is text on the phone. On the phone `first_name` and `last_name` may be empty, because a guest has no name yet. The repositories hold no rules: grading, unlocking and repetition come as use cases with the screens.

---

## 10. Not built yet

* The lesson and test screens (Milestone 7, steps 7.2 to 7.5); the subjects tab already uses the content and the progress.
* Content updates by download and sync of progress (see the [ROADMAP](ROADMAP.md)). Accounts exist: sign-up, sign-in and the profile on the server (step 5.12).

---

## 11. Tests

| Module | Tests | What they cover |
|---|---|---|
| `:database` (`jvmTest`) | 19 | Schema constraints and queries on in-memory SQLite; the content installer |
| `:tools:content-pipeline` | 40 | Validator rules; PostgreSQL writer against a throwaway database with the real DDL (skipped when no local PostgreSQL); exporter |
| `:shared` (`jvmTest`) | 167 | Content provider with the bundled file (skipped without it); the whole onboarding from the welcome screen to the home screen with the real navigation (`OnboardingFlowTest`); `AppViewModel`; design tokens against the code; the onboarding resume and the steps per role; `OnboardingQuestionViewModel` and `RoleViewModel` on an in-memory database; the welcome, language, role, subject, date, goal, reminder, plan, account and sign-in screens and their ViewModels (Compose UI tests, plus pictures at phone, tablet and desktop size in both themes and languages, written to `composeApp/build/screenshots/`); repositories, the auth repository against a mocked Supabase (Ktor `MockEngine`); the app's Supabase client; placeholder test. `AuthRepositoryLiveTest` signs in to the real project with a test account and is skipped unless `MATURIMATE_TEST_EMAIL` and `MATURIMATE_TEST_PASSWORD` are set |

Run all with `./gradlew :database:jvmTest :tools:content-pipeline:test :shared:jvmTest`.

CI (`.github/workflows/ci.yml`) runs on every push that changes more than documentation (a pull request shows its branch's run), with parallel jobs so a failure points at the part that broke:

| Job | Runs | Notes |
|---|---|---|
| Database (SQLDelight) | `:database:jvmTest`, coverage | Kover line coverage in the summary; fails under 80 % |
| Content pipeline | migrations and row level security tests, `:tools:content-pipeline:test` | PostgreSQL 16 service; `REQUIRE_POSTGRES=true`, so the importer tests fail instead of being skipped |
| App | `:shared:jvmTest`, coverage, the desktop jar (`:desktopApp`) | Bundles the test fixture as content, so the content provider test runs too; coverage as for the database |
| Android build | `:androidApp:assembleDebug` | Beside the tests, because it is the slowest part |

Each job publishes its JUnit results as its own check and as a table (every test, passed or failed) in the run summary.
