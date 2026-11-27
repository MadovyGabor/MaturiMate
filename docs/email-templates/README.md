# Supabase Auth E-mail Templates

Text-only, bilingual (Slovak and Hungarian) HTML templates for the e-mails Supabase Auth sends through Resend: three 6-digit code e-mails and two security notifications (no code).

> **Why hex colors appear here:** These files live outside the Compose app (`docs/email-templates/`) and are pasted directly into the Supabase dashboard. Every color value is taken directly from [tokens.json](../design-system/tokens.json) (light: `#f2ebdf`, `#ffffff`, `#211b13`, `#4f4537`, `#e6ddcf`, `#7b5800`, `#ffde9c`, `#261a00`; dark: `#17130e`, `#241e17`, `#ece1d4`, `#d3c4ad`, `#f4c04e`, `#5c4300`, `#ffde9c`, `#4f4537`). App screens continue to use theme tokens only.

## Dashboard mapping

Paste each file into **Supabase Dashboard → Authentication → Emails**:

| Template in Supabase | File | Status in V1 |
| :--- | :--- | :--- |
| **Confirm signup** | [confirm-signup.html](confirm-signup.html) | Active (used on e-mail sign-up and resend) |
| **Reset password** | [reset-password.html](reset-password.html) | Active (used on forgotten password flow) |
| **Reauthentication** | [reauthentication.html](reauthentication.html) | Active (used on password change in Settings; create this template if it does not exist yet) |
| **Password changed** (security notification) | [password-changed.html](password-changed.html) | Ready; sent only once the notification is switched on (see [Security notifications](#security-notifications)). Sent after any password change: Settings and the forgotten-password flow. |
| **Email address changed** (security notification) | [email-changed.html](email-changed.html) | Ready, but **no effect in V1**: the app never changes the address. Switch on once address changes exist (read the limit in [Security notifications](#security-notifications) first). |
| **Change email address** | *Not created* | **Not used in V1** (see `content/DECISIONS.md` 2026-11-14, `settings_email_fixed` in `string.xml`, and `AuthRepositoryImpl.kt`). *Secure email change* remains enabled in Supabase for future use. |

Each template switches on `{{ .Data.lang }}`:
- `{{ if eq .Data.lang "sk" }}` → Slovak
- `{{ else }}` → Hungarian (default branch, because `lang` may be absent before a profile language exists)

The code validity text (code e-mails only) (`1 óráig érvényes` / `platí 1 hodinu`) matches the configured **Email OTP expiration** of `3600 seconds` in [supabase-setup.md](../../llm/supabase-setup.md).

A `<!-- LOGO: later -->` comment marks where an image wordmark/logo can be added later.

## Layout

The classic layout of a code e-mail: on a tinted page (`surface-container`), the wordmark "Maturi**Mate**" above a white card (`surface-container-lowest`, `radius-lg`). In the card, centered: a small all-caps label in `primary` (what the mail is for), the heading "Írd be a kódot" / "Zadaj kód" (the app's `code_title`), one sentence, the code large in a full-width `primary-container` box, the validity, a thin rule and the "not you" line. Below the card: the app's welcome headline in `primary` and a "sent automatically" line. Light by default, dark automatically when the mail client is in dark mode (owner's decision, 2026-11-26); under 480 px the card padding and the code size shrink. Literata and Manrope are named first in the font stacks; most mail clients do not have them and show Georgia and the system sans instead (no web fonts are loaded: nothing is fetched from outside).

## Subject lines

Subjects are rendered with the same Go templates as the body: Supabase's own default subject for Reauthentication is `{{ .Token }} is your verification code`, and its Management API example sets subjects with template syntax ([Email templates](https://supabase.com/docs/guides/auth/auth-email-templates)). `{{ .Data }}` is the user's `user_metadata`.

**No code in the subject** (owner's decision, 2026-11-26; the three code e-mails were pasted without it). A subject with the code shows it on the lock screen and in the inbox list.

Checked locally with Go's `text/template` (the engine GoTrue uses): every body and subject renders for `lang = "sk"`, `lang = "hu"`, a missing `lang`, empty metadata and (notifications) no `Data` at all. The last three fall into the Hungarian `else` branch without an error. A Google-only account has no `lang` in its metadata, so it always gets Hungarian.

| Template | Subject (bilingual Go template) | Neutral fallback subject (no template syntax) |
| :--- | :--- | :--- |
| **Confirm signup** | `{{ if eq .Data.lang "sk" }}MaturiMate – potvrdzovací kód{{ else }}MaturiMate – megerősítő kód{{ end }}` | `MaturiMate – kód` |
| **Reset password** | `{{ if eq .Data.lang "sk" }}MaturiMate – kód na obnovenie hesla{{ else }}MaturiMate – kód az új jelszóhoz{{ end }}` | `MaturiMate – kód` |
| **Reauthentication** | `{{ if eq .Data.lang "sk" }}MaturiMate – kód na zmenu hesla{{ else }}MaturiMate – kód a jelszó megváltoztatásához{{ end }}` | `MaturiMate – kód` |
| **Password changed** | `{{ if eq .Data.lang "sk" }}MaturiMate – tvoje heslo bolo zmenené{{ else }}MaturiMate – a jelszavad megváltozott{{ end }}` | `MaturiMate – jelszó / heslo` |
| **Email address changed** | `{{ if eq .Data.lang "sk" }}MaturiMate – tvoja e-mailová adresa bola zmenená{{ else }}MaturiMate – az e-mail-címed megváltozott{{ end }}` | `MaturiMate – e-mail` |

The three code subjects above are the first subjects with the code removed. The owner pasted their own version: if the dashboard differs, the dashboard wins.

<details>
<summary>Replaced (2026-11-26): the first subjects, with the code</summary>

| Template | Subject |
| :--- | :--- |
| Confirm signup | `{{ if eq .Data.lang "sk" }}MaturiMate – potvrdzovací kód: {{ .Token }}{{ else }}MaturiMate – megerősítő kód: {{ .Token }}{{ end }}` |
| Reset password | `{{ if eq .Data.lang "sk" }}MaturiMate – kód na obnovenie hesla: {{ .Token }}{{ else }}MaturiMate – kód az új jelszóhoz: {{ .Token }}{{ end }}` |
| Reauthentication | `{{ if eq .Data.lang "sk" }}MaturiMate – kód na zmenu hesla: {{ .Token }}{{ else }}MaturiMate – kód a jelszó megváltoztatásához: {{ .Token }}{{ end }}` |

</details>

## Automatic code recognition (what is actually possible)

Do not assume or promise automatic OTP autofill from e-mail across platforms:

1. **Gmail (Email Markup / JSON-LD):** Google's structured email markup (`Schema.org`) requires registering your sending domain with Google, matching DKIM/SPF domains, a consistent static sender address, and sending volume history ([Registering with Google](https://developers.google.com/workspace/gmail/markup/registering-with-google), [Email Markup Registration](https://support.google.com/mail/contact/markup_registration)). It surfaces cards/actions inside Gmail, does not feed OS-level keyboard autofill into third-party Android/desktop apps, and is not practically available through Supabase's SMTP setup.
2. **Apple Mail on iOS 17+:** Native iOS AutoFill can detect one-time verification codes from messages received in the built-in **Apple Mail** app using text-pattern heuristics and suggest them above the keyboard ([Apple Support](https://support.apple.com/guide/iphone/automatically-fill-in-verification-codes-iphc896a30b2/ios) / iOS 17+ AutoFill Passwords & Codes). It does not read third-party mail apps (such as the Gmail app) unless that account is synced into Apple Mail.
3. **Android:** Android provides automatic OTP retrieval for **SMS** via the SMS Retriever API and SMS User Consent API ([Android SMS Retriever](https://developers.google.com/identity/sms-retriever/overview)), not for e-mail inboxes.

To give every client the best chance of surfacing the code cleanly without relying on unsupported markup:
- The word `kód` appears directly before the code in both languages.
- `{{ .Token }}` sits on its own line with nothing else on that line and no internal spaces, so double-clicking selects all 6 digits cleanly.
- ~~`{{ .Token }}` is included in the subject line.~~ Replaced: no code in the subject (see Subject lines).

## Slovak sentences to check (native-speaker review)

Every Slovak sentence in the templates (marked for native-speaker review before release):

| Where | Slovak text |
| :--- | :--- |
| Confirm signup — subject | `MaturiMate – potvrdzovací kód` |
| Confirm signup — intro | `Týmto kódom potvrdíš svoj účet v aplikácii MaturiMate.` |
| Confirm signup — ignore | `Ak si účet nevytváraš, tento e-mail pokojne ignoruj.` |
| Reset password — subject | `MaturiMate – kód na obnovenie hesla` |
| Reset password — intro | `S týmto kódom si v aplikácii MaturiMate nastavíš nové heslo.` |
| Reset password — ignore | `Ak o nové heslo nežiadaš, tento e-mail pokojne ignoruj.` |
| Reauthentication — subject | `MaturiMate – kód na zmenu hesla` |
| Reauthentication — intro | `Týmto kódom potvrdíš zmenu hesla v aplikácii MaturiMate.` |
| Reauthentication — ignore | `Ak heslo nemeníš, tento e-mail pokojne ignoruj.` |
| All templates — validity | `Kód platí 1 hodinu.` |
| Confirm signup — label | `Potvrdenie účtu` |
| Reset password — label | `Nové heslo` |
| Reauthentication — label | `Zmena hesla` |
| Notifications — label | `Bezpečnostné upozornenie` |
| Password changed — subject and heading | `MaturiMate – tvoje heslo bolo zmenené` / `Tvoje heslo bolo zmenené` |
| Password changed — intro | `Heslo k tvojmu účtu MaturiMate bolo práve zmenené.` |
| Email address changed — subject and heading | `MaturiMate – tvoja e-mailová adresa bola zmenená` / `Tvoja e-mailová adresa bola zmenená` |
| Email address changed — intro | `E-mailová adresa tvojho účtu MaturiMate bola práve zmenená.` |
| Email address changed — box | `Pôvodná adresa`, `Nová adresa` |
| Notifications — if it was you | `Ak o zmene vieš, nemusíš nič robiť.` |
| Notifications — if it was not you | `Ak o zmene nevieš, odhlás sa v aplikácii a pri prihlásení si cez „Zabudnuté heslo?“ nastav nové heslo.` |
| All templates — footer | `Tento e-mail ti poslala aplikácia MaturiMate automaticky, neodpovedaj naň.` |

The notification lines avoid a gendered past tense ("ak si to bol/bola ty") on purpose: please keep that in the review.

Taken unchanged from the app (`composeResources/values/string.xml`), so already in use: the heading `Zadaj kód` (`code_title`), the label `6-miestny kód` (`code_label`) and the footer `Maturita po malých krokoch.` (`welcome_headline`).

## Local preview & live testing

Render local HTML previews (sample code `482917`, sample addresses `stara.adresa@example.com` and `nova.adresa@example.com`) of all five templates, in Slovak and Hungarian, into `llm/outputs/email-previews/`:

```powershell
powershell -ExecutionPolicy Bypass -File docs/email-templates/render-previews.ps1
```

Open the generated `*-hu.html` and `*-sk.html` files in a browser before pasting into Supabase.

**Live test (owner):**
1. Paste the templates and subject lines into **Authentication → Emails** in the Supabase dashboard (the notifications in their own part, see [Security notifications](#security-notifications)).
2. With a test account (never commit real addresses or tokens):
   - Sign up in Hungarian (`lang = "hu"`) and verify the **Confirm signup** mail.
   - Sign up in Slovak (`lang = "sk"`) and verify the **Confirm signup** mail.
   - Trigger **Forgot password** and verify **Reset password**.
   - Change password in Settings and verify **Reauthentication** (remember per `llm/supabase-setup.md` that GoTrue enforces the reauthentication code only on sessions older than 24 hours).
   - After the password change in Settings and after a forgotten-password reset, verify **Password changed** arrives, once in each language.

## What could not be verified here

- Actual delivery and rendering inside live mail clients (Gmail web/mobile, Apple Mail, Outlook, Thunderbird).
- Mail-client automatic dark-mode color inversion in real clients.
- Live iOS Apple Mail OTP suggestion behavior.

## Light or dark: who decides

The mails are **light by default** and switch to the app's dark colours **automatically** when the reader's mail client says it is in dark mode (`@media (prefers-color-scheme: dark)`, with `<meta name="color-scheme" content="light dark">`). The mail client decides, not the sender:

- **Apple Mail (iOS, macOS), Outlook for Mac/iOS:** report dark mode, so the mail shows the MaturiMate dark version when the phone or Mac is dark, and the light one otherwise.
- **Gmail web:** does not report dark mode: always the light version.
- **Gmail app (Android, iOS), Outlook.com and Outlook for Windows in dark mode:** do not use the dark rules; they may recolour the light version themselves (lighten text, darken backgrounds). No HTML setting can fully stop this; the design stays readable (dark text on light boxes).
- **Any client that understands none of this:** the light version.

Checked here only in Chrome (light system: light mail, dark system: dark mail); the real clients are part of the owner's test mail.

## Security notifications

Verified in Supabase's docs ([Email templates](https://supabase.com/docs/guides/auth/auth-email-templates), [Customizing email templates](https://supabase.com/docs/guides/local-development/customizing-email-templates)):

- Templates come in two groups, authentication e-mails and **security notification e-mails**; the latter include **Password changed** and **Email address changed** (also phone changed, sign-in method linked or removed, verification method added or removed: not used here).
- "Security emails are only sent to users if the respective security notifications have been enabled at a project-level." Each has its own switch (Management API: `mailer_notifications_password_changed_enabled`, `mailer_notifications_email_changed_enabled`; templates `mailer_templates_password_changed_notification_content`, `mailer_templates_email_changed_notification_content`; subjects `mailer_subjects_password_changed_notification`, `mailer_subjects_email_changed_notification`).
- Default subjects: "Your password was changed", "Your email address was changed". Sent "when a user's password is changed" and "when a user's email address is changed".
- Variables: `{{ .OldEmail }}` is the old address and exists **only** in the Email address changed template; `{{ .Email }}` is the user's address, which after the change is the new one (the docs' own example: "changed from {{ .OldEmail }} to {{ .Email }}"). `{{ .NewEmail }}` exists only in the Change email address template and is not used here.

Not verified (the docs do not say; the test mail settles each):

- whether the switches are **off by default** (the docs only say the mails go out once switched on; check the dashboard);
- **who gets the Email address changed mail** (the old address, the new one or both);
- whether `{{ .Data }}` is filled in the notifications (if not, the mail comes in Hungarian; checked: no error);
- the exact names of the switches in the dashboard.

**Where to switch on:** Supabase Dashboard → Authentication → Emails, the security notifications part. Switch on **Password changed**; leave **Email address changed** off until the app can change the address.

**Limit of the address mail:** if someone else changed the address, the forgotten-password code goes to the account's address, which is then theirs; the app has no "sign out everywhere" and no support contact, so the "not you" line cannot really help then. Before address changes exist, decide on a support contact and change that line.
