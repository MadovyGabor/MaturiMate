# Onboarding design

The plan for the first-start flow (Milestones 3 and 5). Status: **draft for discussion**. Sections are filled step by step: practices (3.1), flow (3.2), prototype (3.3), texts (3.4).

## 1. What learning apps do (3.1)

Patterns seen in well-known learning and habit apps (Duolingo, Babbel, Brilliant, Headspace, Quizlet) and in the local inspiration pack. Described in our own words; no screenshots are copied.

| Pattern | Who does it | Take it? |
| :--- | :--- | :--- |
| **Value before the account.** Questions and often a first lesson come first; the account is asked for afterwards, with "later" allowed until a natural point. | Duolingo, Brilliant | **Yes.** Matches our anonymous sign-in and the required sign-in after the first lesson. |
| **Account first** (sign-up form before anything else). | Quizlet, Babbel, most shops | **No.** Students drop off before they see what the app does. |
| **"Get started" and "I already have an account" on the welcome screen.** | Duolingo, most apps | **Yes.** A returning user (new phone) must be able to skip the questions. |
| **One question per screen**, a progress bar on top, a back button. | Duolingo, Babbel, Headspace | **Yes.** Short screens, clear progress, easy to fix a wrong answer. |
| **Only questions that change the experience**, each with a one-line reason ("so we can plan your weeks"). | Brilliant, Headspace | **Yes.** No marketing questions ("how did you hear about us"). |
| **A sensible default is preselected** (for example 10 minutes a day). | Duolingo | **Yes.** One tap to continue. |
| **"I don't know yet" option** on questions the student may not know. | Babbel (level) | **Yes**, for the English level. |
| **Daily goal as a commitment** (5 / 10 / 15 / 20 minutes, named levels). | Duolingo, Headspace | **Yes**, with minutes only, no playful names. |
| **Reminder: explain first, then the system prompt** ("soft ask"), with a time picker and "not now". | Headspace, Duolingo | **Yes.** Android 13+ needs a runtime permission; a refused system prompt is hard to undo. |
| **"Your plan" summary at the end** (goal, time frame, what comes next). | Brilliant, Babbel | **Yes.** For us: days until the exam and the daily goal, turned into one concrete number. |
| **Paywall right after the plan.** | Brilliant, Babbel | **No.** Not part of V1. |
| **Mascot and playful tone.** | Duolingo | **No.** The design system is calm and elegant; we use the logo and plain, friendly text. |
| **Sign-in with Google first, e-mail second**, terms and privacy linked under the buttons. | Most apps | **Yes.** |
| **Age gate** before an account. | Quizlet | **Yes, light.** GDPR digital consent age is 16 in Slovakia and Hungary; a confirmation is needed at account creation. |
| **Answers saved after every screen**, the flow resumes where it stopped. | Duolingo | **Yes.** Cheap with the user database, avoids starting over. |

## 2. Practices for MaturiMate

1. **At most about one minute.** Seven or eight short screens, each answerable with one tap.
2. **The first question sets the language of the app.** Every later screen is already in Hungarian or Slovak.
3. **Defaults everywhere they are safe**: daily goal 10 minutes, the next exam date suggested.
4. **Every question says why it is asked**, in one short line under the question.
5. **Back is always possible; nothing is lost.** Answers are saved to the user database after each screen.
6. **Works offline.** If there is no network, the anonymous account is created later; the questions still work.
7. **The account screen is the last step**, with a quiet "Körülnézek" option. Signing in becomes required after the first lesson (Milestone 6.5).
8. **No dark patterns**: no pre-ticked marketing consent, no fake urgency, "not now" is always visible on permission screens.
9. **Design system only**: one main button per screen, step counter as text above the progress bar, `headline-medium` questions.

## 3. Who uses the app

Not everyone prepares for the exam, so the flow asks first what the app is for. The answer decides which questions follow.

| Role | Who | Asked | Not asked |
| :--- | :--- | :--- | :--- |
| **Student** ("Érettségire készülök") | prepares for the maturita | subjects, exam date, daily goal, reminder | |
| **Learner** ("Csak tanulni szeretnék") | wants to learn or practise | subjects, daily goal, reminder | exam date |
| **Teacher** ("Tanár vagyok") | looks at or tries the content | subjects they teach | exam date, daily goal |

Teachers get the same app as everyone else. There is no teacher mode (classes, assigning tasks) in V1; see [DECISIONS](../DECISIONS.md).

**Subjects.** The subject step is a multiple choice. The compulsory exam subjects are preselected by the teaching language and cannot be removed: Slovak language and literature, English, and for a Hungarian-language school also Hungarian language and literature. Mathematics and later subjects are optional. English always asks for the level: B1, B2 or "I don't know yet", which is saved as B1. Subjects without content yet are shown with a "Hamarosan" (coming soon) label. Stored in `user_subject` (`source` = `AUTO` for compulsory, `CHOSEN` for picked).

## 4. User data

Ask only for what is used. Everything is stored per account; the account id comes from Supabase Auth.

| Data | Used for | Stored in | Asked from |
| :--- | :--- | :--- | :--- |
| Teaching language (HU/SK) | subjects, app language | `user_profile.teaching_language`, `user_settings.ui_language` | everyone |
| Role | which questions follow | `user_role` | everyone |
| Subjects, English level | content, home screen | `user_subject` | everyone |
| Exam date | countdown, plan | `user_profile.graduation_date` | students |
| Daily goal | streak, plan | `user_settings.daily_goal_minutes` | students, learners |
| Reminder and its time | notifications | `user_settings.reminder_*` | anyone who wants it |
| Time zone | counting the daily streak | `user_settings.timezone` (from the phone, not asked) | automatic |
| First name and last name | identifying the account holder, addressing the user | `user_profile.first_name`, `last_name` | account screen (prefilled from Google) |
| E-mail | sign-in, password reset, messages about the account | Supabase Auth | account screen |
| Terms and privacy accepted, age 16 or older | legal record | `user_consent` (new): version and time | account screen |
| Marketing e-mails | newsletter | `user_consent`, separate, never preselected | account screen, optional |

Not asked: date of birth (the age is confirmed instead; V1 is for users aged 16 or older, the GDPR consent age in Slovakia and Hungary), school type, school name. `date_of_birth` becomes nullable in the schema (step 4.2).

The anonymous account ("Körülnézek") has no name or e-mail until the user signs in; signing in is required after the first lesson.

Before the public release (Milestone 10), the privacy notice and the terms of use are checked by a legal professional.

## 5. Flow (3.2)

One question per screen. Every answer is saved to the user database at once, so the flow resumes where it stopped. A line under the questions says that every answer can be changed later in the profile.

| # | Screen | Shown to | Content | Skip |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Welcome** | everyone | Logo, one sentence on what the app gives. "Kezdjük" starts the questions; "Már van fiókom" signs in (Google or e-mail), loads the profile and goes straight to home. An anonymous account is created in the background (later if offline). | |
| 2 | **Language** | everyone | "Melyik tagozatra jársz?" Hungarian or Slovak. Also sets the app language. | |
| 3 | **Role** | everyone | "Mire szeretnéd használni az appot?" Student, learner or teacher (section 3). | |
| 4 | **Subjects** | everyone | Compulsory subjects preselected and locked, English level below English (B1 / B2 / I don't know yet → B1), optional subjects, "Hamarosan" labels. The question reads "Miből érettségizel?", for teachers "Mit tanítasz?", for learners "Mit szeretnél tanulni?". | |
| 5 | **Exam date** | students | "Mikor érettségizel?" Date picker with the next spring exam date suggested. | |
| 6 | **Daily goal** | students, learners | 5 / 10 / 15 / 20 minutes, 10 preselected. | |
| 7 | **Reminder** | everyone | "Emlékeztessünk naponta?" Time picker, then the Android permission prompt. | "Most nem" |
| 8 | **Your plan** | everyone | Students: days until the exam, daily goal, total practice time ("185 nap, napi 10 perc, kb. 30 óra"). Learners: goal and subjects. Teachers: subjects. | |
| 9 | **Account** | everyone | First and last name (prefilled from Google), Google or e-mail and password, a required checkbox "I am 16 or older and accept the terms and the privacy notice" (with links), an optional unticked checkbox for news by e-mail. | "Körülnézek" (stays anonymous) |

Then the home screen with the first lesson. Students see 9 screens, learners 8, teachers 7: about one to one and a half minutes.

**After the onboarding**

* **After the first lesson**, an anonymous user must create an account: the account screen without "Körülnézek". The anonymous account is linked to the new one, so no progress is lost (step 6.5).
* **Back** works on every screen and keeps the answers. Only the reminder and the account screen can be skipped; every other question has a default or a clear choice.
* **Everything can be changed later** in the profile: language, role, subjects, English level, exam date, goal, reminder, name.

## 6. Prototype (3.3)

*To be added.*

## 7. Texts (3.4)

*To be added, in Hungarian and Slovak.*
