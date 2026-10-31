# Onboarding design

The plan for the first-start flow (Milestones 3 and 5). Status: **draft for discussion**. Sections are filled step by step: practices (3.1), flow (3.2), prototype (3.3), texts (3.4).

## 1. What learning apps do (3.1)

Patterns seen in well-known learning and habit apps (Duolingo, Babbel, Brilliant, Headspace, Quizlet) and in the local inspiration pack. Described in our own words; no screenshots are copied.

| Pattern | Who does it | Take it? |
| :--- | :--- | :--- |
| **Value before the account.** Questions and often a first lesson come first; the account is asked for afterwards, with "later" allowed until a natural point. | Duolingo, Brilliant | **Yes.** Matches our local guest ("Körülnézek") and the required sign-in after the first lesson. |
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
6. **Works offline until the account step.** The questions and the guest mode need no network; creating an account does.
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

A guest ("Körülnézek") has no server account, name or e-mail: the answers stay on the phone under a temporary local id. Nothing is sent to the server before the user signs up and accepts the terms. Signing in is required after the first lesson.

Before the public release (Milestone 10), the privacy notice and the terms of use are checked by a legal professional.

## 5. Flow (3.2)

One question per screen. Every answer is saved to the user database at once, so the flow resumes where it stopped. A line under the questions says that every answer can be changed later in the profile.

| # | Screen | Shown to | Content | Skip |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Welcome** | everyone | Logo, one sentence on what the app gives. "Kezdjük" starts the questions; "Már van fiókom" signs in (Google or e-mail), loads the profile and goes straight to home. | |
| 2 | **Language** | everyone | "Melyik tagozatra jársz?" Hungarian or Slovak. Also sets the app language. | |
| 3 | **Role** | everyone | "Mire szeretnéd használni az appot?" Student, learner or teacher (section 3). | |
| 4 | **Subjects** | everyone | Compulsory subjects preselected and locked, English level below English (B1 / B2 / I don't know yet → B1), optional subjects, "Hamarosan" labels. The question reads "Miből érettségizel?", for teachers "Mit tanítasz?", for learners "Mit szeretnél tanulni?". | |
| 5 | **Exam date** | students | "Mikor érettségizel?" Date picker with the next spring exam date suggested. | |
| 6 | **Daily goal** | students, learners | 5 / 10 / 15 / 20 minutes, 10 preselected. | |
| 7 | **Reminder** | everyone | "Emlékeztessünk naponta?" Time picker, then the Android permission prompt. | "Most nem" |
| 8 | **Your plan** | everyone | Students: days until the exam, daily goal, total practice time ("185 nap, napi 10 perc, kb. 30 óra"). Learners: goal and subjects. Teachers: subjects. | |
| 9 | **Account** | everyone | First and last name (prefilled from Google), Google or e-mail and password, a required checkbox "I am 16 or older and accept the terms and the privacy notice" (with links), an optional unticked checkbox for news by e-mail. | "Körülnézek" (continues as a local guest) |

Then the home screen with the first lesson. Students see 9 screens, learners 8, teachers 7: about one to one and a half minutes.

**After the onboarding**

* **After the first lesson**, a guest must create an account or sign in: the account screen without "Körülnézek". The guest's local data moves to the new account id, so no progress is lost (step 6.5).
* **Back** works on every screen and keeps the answers. Only the reminder and the account screen can be skipped; every other question has a default or a clear choice.
* **Everything can be changed later** in the profile: language, role, subjects, English level, exam date, goal, reminder, name.

## 6. Prototype (3.3)

[prototype.html](prototype.html) is a clickable prototype of the flow above. Open it in a browser from this folder (it loads the logo from `composeApp/` and the fonts from Google Fonts).

* **Phone, Tablet, PC** switch the same app between three layouts, following the app's breakpoints: one column below 600 dp; a centred, wider column with the buttons on the right from 600 dp; from about 1000 dp two panes, with the logo and a step list on the left and the question on the right.
* **Light and dark** follow the design system tokens. On dark surfaces the logo sits on a cream tile.
* The exam date step offers the next two spring exams as cards (with the days left) and "Más dátum", which opens a calendar. In the app this is the Material 3 date picker.
* The notes panel shows the data the app would save from the answers.
* Mock only: sign-in, the notification permission prompt and saving. Text is Hungarian only. The spring exam dates (15 March) are examples until the official dates are checked.

## 7. Texts (3.4)

The keys become entries in `composeResources/values-hu/string.xml` and `values-sk/string.xml` in Milestone 5. `%1$d`, `%1$s` are placeholders. Subject names are not here: they come from the content database (`name_hu`, `name_sk`).

**Status:** the Hungarian text is final after the owner's review. **Every Slovak line needs a check by a native speaker** before the release. ⚠ marks a line that may be tight on a button, chip or the desktop step list.

**Language of the first two screens.** The app language is chosen on the language screen, so the welcome and language screens use the phone's language: Hungarian if the phone is set to Hungarian, otherwise Slovak. The two language options are always written in their own language.

**Slovak grammar notes.** Numbers need plural forms (`1 deň`, `2–4 dni`, `5+ dní`; `hodina`, `hodiny`, `hodín`), so these keys become `<plurals>` resources. Slovak text addresses the user informally (tykanie) and avoids gendered past forms where possible.

### Common

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `common_next` | Tovább | Ďalej |
| `common_back` | Vissza | Späť |
| `common_cancel` | Mégse | Zrušiť |
| `common_ok` | OK | OK |
| `onboarding_step` | %1$d. lépés / %2$d | Krok %1$d z %2$d |
| `onboarding_change_later` | Később a profilodban bármikor módosíthatod. | Neskôr to môžeš kedykoľvek zmeniť vo svojom profile. |

### 1. Welcome

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `welcome_tagline` | Rövid leckék és gyakorlás az érettségiig, magyarul és szlovákul. | Krátke lekcie a precvičovanie až do maturity, po maďarsky aj po slovensky. |
| `welcome_start` | Kezdjük | Začnime |
| `welcome_have_account` | Már van fiókom | Už mám účet |

### 2. Language

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `language_title` | Milyen tanítási nyelvű iskolába jársz? | Aký je vyučovací jazyk tvojej školy? |
| `language_why` | Ebből tudjuk, milyen tantárgyakból érettségizel. Az appot is ezen a nyelven látod. | Podľa toho vieme, z ktorých predmetov maturuješ. Aplikáciu uvidíš v tomto jazyku. |
| `language_hu` | Magyar | Magyar |
| `language_hu_desc` | Magyar tanítási nyelvű iskola | Magyar tanítási nyelvű iskola |
| `language_sk` | Slovenský | Slovenský |
| `language_sk_desc` | Škola s vyučovacím jazykom slovenským | Škola s vyučovacím jazykom slovenským |

### 3. Role

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `role_title` | Mire szeretnéd használni az appot? | Na čo chceš aplikáciu používať? |
| `role_why` | Ehhez igazítjuk a következő kérdéseket. | Podľa toho prispôsobíme ďalšie otázky. |
| `role_student` | Érettségire készülök | Pripravujem sa na maturitu |
| `role_student_desc` | Visszaszámlálás, napi cél és gyakorlás az érettségi tantárgyaiból. | Odpočítavanie, denný cieľ a precvičovanie maturitných predmetov. |
| `role_learner` | Csak tanulni szeretnék | Chcem sa len učiť |
| `role_learner_desc` | Leckék és gyakorlás, érettségi dátum nélkül. | Lekcie a precvičovanie bez dátumu maturity. |
| `role_teacher` | Tanár vagyok | Som učiteľ/ka |
| `role_teacher_desc` | Megnézem és kipróbálom a tartalmat. | Pozriem si a vyskúšam obsah. |

### 4. Subjects

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `subjects_title_student` | Miből érettségizel? | Z čoho maturuješ? |
| `subjects_title_learner` | Mit szeretnél tanulni? | Čo sa chceš učiť? |
| `subjects_title_teacher` | Mit tanítasz? | Čo učíš? |
| `subjects_why_student` | A kötelező tantárgyakat már kijelöltük. Ha mást is választasz, jelöld be. | Povinné predmety sme už označili. Ak si vyberáš aj ďalšie, označ ich. |
| `subjects_why_other` | Jelöld be, ami érdekel. Többet is választhatsz. | Označ, čo ťa zaujíma. Môžeš vybrať aj viac. |
| `subjects_compulsory` | Kötelező | Povinný |
| `subjects_coming_soon` | Hamarosan | Čoskoro |
| `subjects_english_level` | Milyen szinten? | Na akej úrovni? |
| `level_unknown` ⚠ | Még nem tudom | Ešte neviem |
| `subjects_level_unknown_hint` | B1-gyel kezdünk, később átállíthatod. | Začneme úrovňou B1, neskôr ju môžeš zmeniť. |

### 5. Exam date

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `date_title` | Mikor érettségizel? | Kedy maturuješ? |
| `date_why` | Ebből számoljuk a visszaszámlálást és a tervedet. | Podľa toho vypočítame odpočítavanie a tvoj plán. |
| `date_spring` | %1$d tavasza | Jar %1$d |
| `date_written_days` (plural) | Írásbeli: %1$s · %2$d nap múlva | Písomná časť: %1$s · o %2$d dní |
| `date_other` | Más dátum | Iný dátum |
| `date_other_desc` | Válaszd ki a naptárból | Vyber v kalendári |
| `date_picker_title` | Érettségi dátuma | Dátum maturity |
| `date_error_past` | Ez a dátum már elmúlt. Válassz egy későbbi napot. | Tento dátum už uplynul. Vyber neskorší deň. |
| `date_error_too_far` | Legfeljebb 3 évre előre választhatsz. | Môžeš vybrať najviac 3 roky dopredu. |

### 6. Daily goal

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `goal_title` | Mennyi időt szánsz naponta a tanulásra? | Koľko času denne venuješ učeniu? |
| `goal_why` | Ehhez mérjük a napi sorozatodat. | Podľa toho počítame tvoju dennú sériu. |
| `goal_minutes` (plural) | %1$d perc | %1$d minút |
| `goal_year_hint` (plural) | Napi %1$d perc egy év alatt körülbelül %2$d óra gyakorlás. | %1$d minút denne je za rok približne %2$d hodín precvičovania. |

### 7. Reminder

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `reminder_title` | Emlékeztessünk naponta? | Máme ti každý deň pripomenúť učenie? |
| `reminder_why` | Egy rövid értesítés a választott időben segít, hogy ne szakadjon meg a sorozatod. | Krátke upozornenie vo zvolenom čase ti pomôže udržať sériu. |
| `reminder_time` | Melyik időpontban? | V akom čase? |
| `reminder_permission_hint` | A következő lépésben a telefon engedélyt kér az értesítésekhez. | V ďalšom kroku ťa telefón požiada o povolenie upozornení. |
| `reminder_turn_on` | Bekapcsolom | Zapnúť |
| `reminder_not_now` | Most nem | Teraz nie |

The system permission dialog itself is Android's and follows the phone's language.

### 8. Your plan

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `plan_title` | A te terved | Tvoj plán |
| `plan_until_exam` | Érettségiig | Do maturity |
| `plan_days` (plural) | %1$d nap | %1$d dní |
| `plan_student_total` (plural) | Napi %1$d perccel ez körülbelül %2$d óra gyakorlás. | Pri %1$d minútach denne je to približne %2$d hodín precvičovania. |
| `plan_daily_goal` | Napi cél | Denný cieľ |
| `plan_learner_year` (plural) | Egy év alatt körülbelül %1$d óra. | Za rok približne %1$d hodín. |
| `plan_reminder` | Emlékeztető minden nap %1$s-kor | Pripomienka každý deň o %1$s |
| `plan_change_later` | Mindent módosíthatsz később a profilodban. | Všetko môžeš neskôr zmeniť vo svojom profile. |

### 9. Account

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `account_title` | Hozd létre a fiókodat | Vytvor si účet |
| `account_why` | Így nem vész el a haladásod, és másik telefonon is folytathatod. | Tak o svoj pokrok neprídeš a môžeš pokračovať aj na inom telefóne. |
| `account_google` | Folytatás Google-lel | Pokračovať cez Google |
| `account_google_signed` | Google-fiókkal: %1$s | Účet Google: %1$s |
| `account_or_email` | vagy e-maillel | alebo e-mailom |
| `account_last_name` | Vezetéknév | Priezvisko |
| `account_first_name` | Keresztnév | Meno |
| `account_email` | E-mail cím | E-mailová adresa |
| `account_password` | Jelszó (legalább 8 karakter, kis- és nagybetű, szám) | Heslo (aspoň 8 znakov, malé a veľké písmeno, číslica) |
| `account_consent` | Elmúltam 16 éves, és elfogadom a felhasználási feltételeket és az adatvédelmi tájékoztatót. | Mám aspoň 16 rokov a súhlasím s podmienkami používania a so zásadami ochrany osobných údajov. |
| `account_news` | Kérek híreket és újdonságokat e-mailben. | Chcem dostávať novinky e-mailom. |
| `account_create` | Fiók létrehozása | Vytvoriť účet |
| `account_browse` ⚠ | Körülnézek | Najprv sa poobzerám |
| `error_last_name_empty` | Add meg a vezetékneved. | Zadaj priezvisko. |
| `error_first_name_empty` | Add meg a keresztneved. | Zadaj meno. |
| `error_email_invalid` | Ez nem tűnik e-mail címnek. Ellenőrizd. | Toto nevyzerá ako e-mailová adresa. Skontroluj ju. |
| `error_password_weak` | A jelszó legalább 8 karakter legyen, és tartalmazzon kisbetűt, nagybetűt és számot. | Heslo musí mať aspoň 8 znakov a obsahovať malé a veľké písmeno a číslicu. |
| `error_email_taken` | Ezzel az e-mail címmel már van fiók. Lépj be helyette. | S týmto e-mailom už účet existuje. Prihlás sa. |
| `error_network` | Nincs internetkapcsolat. Próbáld újra, ha újra van net. | Nie si pripojený na internet. Skús to znova, keď budeš online. |

### Sign-in ("Már van fiókom")

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `signin_title` | Belépés | Prihlásenie |
| `signin_why` | Lépj be, és betöltjük a profilodat és a haladásodat. | Prihlás sa a načítame tvoj profil aj pokrok. |
| `signin_password` | Jelszó | Heslo |
| `signin_button` | Belépés | Prihlásiť sa |
| `signin_forgot` | Elfelejtetted a jelszavad? | Zabudnuté heslo? |
| `error_signin` | Hibás e-mail cím vagy jelszó. | Nesprávny e-mail alebo heslo. |

### Home, after the onboarding

| Key | Hungarian | Slovak |
| :--- | :--- | :--- |
| `home_guest_banner` | Fiók nélkül nézel körül. Az első lecke után létrehozzuk a fiókodat, hogy ne vesszen el a haladásod. | Prezeráš si aplikáciu bez účtu. Po prvej lekcii si vytvoríš účet, aby sa tvoj pokrok nestratil. |
| `home_returning_banner` | Betöltöttük a profilodat és a haladásodat. | Načítali sme tvoj profil aj pokrok. |
