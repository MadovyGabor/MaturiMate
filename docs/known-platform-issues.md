# Known issues on other platforms

Things we know are not right (or not checked) on a platform, and what to do about them. Kept short on purpose: one entry per issue, with the impact and a fix idea. When an issue is fixed, delete its entry and mention the fix in [DECISIONS](../content/DECISIONS.md) if it was a decision.

Linked from [Milestone 20](ROADMAP.md) (other platforms). The review that found most of these: Milestone 13, slices S1 to S5, 2026-11-14.

**Status:** *open* = to fix, *accepted* = a decision to live with it for now, *unverified* = not run on that platform yet.

---

## Desktop (Windows, macOS, Linux)

Only unit tests and the compile run for the desktop target so far, and the manual checks assumed Windows. Everything marked *unverified* needs a run on macOS and Linux.

### D1. Session tokens are stored in plain text (open, the first one to fix)

- **What:** the session (access and refresh token) and the pending refresh tokens of an offline sign-out are kept by the library's default storage, which is Java `Preferences`. That is the registry on Windows (`HKCU\Software\JavaSoft\Prefs`), a plist in `~/Library/Preferences` on macOS and an XML file under `~/.java/.userPrefs` on Linux. None of them is encrypted.
- **Impact:** anyone with access to the user's operating system account can read the tokens and act as that user until the session is revoked. Not a remote attack. Android is not affected: its session store is excluded from the cloud backup and the device transfer.
- **Decision so far:** accepted for the alpha (see DECISIONS 2026-11-14, decision 24).
- **Fix idea:** put the tokens in the operating system's credential store behind a small `SessionStore` interface: Windows Credential Manager or DPAPI, macOS Keychain, Linux Secret Service (libsecret). A keyring library can do this on the JVM; check supabase-kt's custom session manager hook first. Needed: a fallback when no store exists (headless Linux), a one-time move of the old preference values, and tests with a fake store.
- **Also:** a Java `Preferences` value is limited to 8192 characters. `PendingRevocation` keeps at most 10 tokens, far below that, but a much longer token format would hit it.

### D2. Google sign-in uses a fixed local port (accepted)

- **What:** the receiver listens on `127.0.0.1:47613` for up to 3 minutes. If another program holds the port, the sign-in fails with a generic error. A firewall prompt on first use is possible on Windows (*unverified*; the receiver binds only 127.0.0.1, so none is expected).
- **Impact:** a failed sign-in, never a data problem.
- **Fix idea:** a free random port, with the allow-list entry as a wildcard on the port (Supabase redirect URLs need the exact host, so check whether a port wildcard is accepted).

### D3. The browser is opened with `java.awt.Desktop` (unverified)

- **What:** on some Linux setups `Desktop.browse` is not supported (no `xdg-open`).
- **Impact:** no Google sign-in there. The e-mail sign-in still works.
- **Fix idea:** fall back to `xdg-open`, or show the link to copy.

### D4. No reminder on the desktop (accepted)

- **What:** `remindersAvailable` is false on the JVM. The onboarding skips the step and the settings hide the group.
- **Fix idea:** if a desktop reminder is wanted later, use system notifications (tray) and a scheduler in the app process.

### D5. Export and dialogs (unverified)

- **What:** the export uses an AWT `FileDialog` in save mode. Its look and its remembered folder differ per system. There is no "share" on the desktop.
- **Fix idea:** none needed unless the look is a problem; then a Compose dialog.

### D6. Window icon and title (open, look)

- **What:** the window title comes from the `app_name` resource now. There is no window icon yet (item U9 in `llm/outputs/ui-later-S1-S5.md`).

---

## Android

### A1. The app language change rebuilds the activity (accepted)

- **What:** the language is the AppCompat per-app locale. Changing it, and the first launch of a Hungarian profile on a phone in another language, recreates the activity. The tab and the profile page are kept (`rememberSerializable`).
- **Unverified on a device:** the language stays Hungarian after the background, a font size change and a rotation; on Android 12 and older it survives a process kill (the `autoStoreLocales` service).

### A2. The native Google picker cannot preselect an account (open, server fix pending)

- **What:** for the account deletion the user signs in with Google again. If they pick a Google account that has no MaturiMate account, Supabase first creates an empty user, then the app rejects it and ends that session.
- **Impact:** a small piece of personal data (Google e-mail, name) kept without a purpose.
- **Fix:** `clean_up_empty_users()`, a daily server job that deletes empty users older than 7 days (migration `20261128120000_server_side_proofs.sql`, card 12; live once the owner applies it). The desktop passes `login_hint` and `prompt=select_account` so Google offers the right account.

### A3. A proof that never finished can leave the phone without a session (accepted)

- **What:** if the process dies in the middle of the Google deletion proof, the next opening ends the other account's session, and the phone's own session may already be replaced. The user signs in again. The local data stays.

### A4. The reminder (unverified on devices)

- **What:** the receiver re-plans the next day whatever happens and works with a 7-second limit. Not yet run on a device: battery saver, a permission blocked for good (the settings hint), the lock screen showing only the neutral text, the notification language on a phone in another language, a phone restart and an app update re-planning the alarm.
- **Note:** a channel the phone already had may keep its old lock-screen visibility; the notification's own setting applies.
- **Note:** `AppCompatDelegate.getApplicationLocales()` can be empty in a receiver on Android 13 and newer when no activity is alive; the profile's app language is the fallback.

### A5. Export and share (unverified on devices)

- **What:** save through the system picker; share through a file in `cache/export`, emptied before each share and at every start. If the process dies while the picker is open, its result is dropped.

### A6. Autofill (unverified)

- **What:** the e-mail, password, new password and the e-mail code have content types set; nobody has checked a real autofill service.

### A7. Cloud backup keeps the user database (accepted)

- **What:** the user database is in the Android cloud backup (DECISIONS 2026-11-14, decision 19). The session store is not. The privacy notice must say so (Milestone 15.3).

---

## iOS (not built yet, Milestone 20)

Things to design when the target exists, learned from the Android and desktop work:

- Session and pending tokens belong in the Keychain, not in user defaults.
- The Google sign-in should use the system web authentication session (no local port) or the native Google SDK.
- A per-app language is a system setting on iOS; the profile's app language must follow it, not fight it.
- The export and sharing need the system share sheet and the file export dialog.
- Local notifications for the reminder, with the permission flow.
- A background refresh for the sync, instead of the foreground triggers alone.

## Web (not planned for V1)

- Tokens in the browser are exposed to scripts of the page: prefer an `HttpOnly` cookie flow or a short session.
- No file system: the export is a download.
- The offline-first database needs a web SQLite driver and its own storage limits.

---

## All platforms

### X1. A partly answered server profile is not brought down (accepted)

- **What:** if an account never finished its sign-up, the phone keeps the guest's answers, and `save_sign_up` overwrites the account's partly answered profile.

### X2. The replace dialog is only exact for finished accounts (open, wording)

- **What:** the dialog before "Már van fiókom" says the account's answers replace those on the phone. A not-set-up account keeps the guest's answers instead.

### X3. "Account already exists" tells whether an e-mail is registered (accepted)

- **What:** the sign-up shows it so that the person is not stuck. The cost is that someone can find out whether an e-mail has an account. DECISIONS 2026-11-14, decision 17.

### X4. The reauthentication code is checked only on sessions older than 24 hours (accepted)

- **What:** GoTrue behaviour, confirmed by the Supabase docs ("Require reauthentication when changing password": a user counts as recently logged in for 24 hours). The password change therefore also needs the current password. Both dashboard settings are on (Authentication → Sign In / Providers → Email: "Require reauthentication when changing password" and "Require current password when changing password"). Not yet tested end to end on the live project.

### X6. A person who signed up with Google first cannot sign up with e-mail and the same address (open, wording; not tried yet)

- **What:** Supabase links identities with the same verified e-mail address into one user (so e-mail first, Google later works, with one account and one progress). The other way round, an e-mail sign-up for an address that already has a Google identity gets an obfuscated answer and **no code e-mail** (the docs' FAQ: "Can you sign up with email if already using OAuth?"). The app then waits at the code step and the code never arrives.
- **Impact:** a stuck sign-up. No data is lost or split.
- **Fix idea:** a hint at the code step ("No code? If you signed up with Google using this address, sign in with Google"); later a "link Google" or "add a password" row in the settings (Supabase manual linking has to be switched on in the dashboard). Whether a Google-only account can add a password with "Require current password" on is not checked.
- **Check first:** with the new build, make an e-mail account and sign in with Google using the same address, and the other way round, and see what the app shows.

### X7. Two different e-mail addresses are two accounts (accepted)

- **What:** someone who signs in with Google using one address and with e-mail using another gets two separate accounts with separate progress. Supabase does not merge them.
- **Impact:** looks like lost progress to the person. No data is lost.
- **Fix idea:** a hint in the app, and later the manual "link an identity" feature.

### X5. Test doubles still run on `Dispatchers.Unconfined` (open, tests)

- **What:** about 26 test files. The flaky ones were moved to a single-thread dispatcher (two threads on one in-memory connection was the cause). The rest may show the same race if they flake.
