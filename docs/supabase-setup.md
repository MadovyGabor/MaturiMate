# Supabase setup

How the Supabase project is configured, so it can be checked or rebuilt. No secrets here: the database password, the secret API key and the Google client secret live only in Supabase, Google Cloud or a password manager.

## Project

| Setting | Value |
| :--- | :--- |
| Project | MaturiMate, free plan |
| Reference | `usqefjhcnehwgszvecym` |
| API URL | `https://usqefjhcnehwgszvecym.supabase.co` |
| Region | West EU (Ireland), `eu-west-1` |
| Schema | `supabase/migrations`, applied in name order (see [dbServer/README.md](../dbServer/README.md)) |

## Authentication

**Sign In / Providers**

| Setting | Value | Why |
| :--- | :--- | :--- |
| Email provider | on | e-mail and password sign-up |
| Confirm email | **off until step 4.7**, then on | the built-in sender cannot use our code template |
| Allow anonymous sign-ins | off | guests stay local on the phone ([DECISIONS](DECISIONS.md) 2026-10-31) |
| Secure email change | on | an e-mail change is confirmed on the old and the new address |
| Secure password change | on | changing the password needs a sign-in within 24 hours |
| Require current password when updating | off | could block the password reset flow |
| Minimum password length | 8 | |
| Password requirements | lower case, upper case letters and digits | the app texts name the rule |
| Email OTP expiration / length | 3600 seconds / 6 digits | e-mails carry a 6-digit code, not a link |
| Google | on, nonce check on | |

**Google Cloud** (project "MaturiMate", Google Auth Platform, audience External, testing mode with test users)

| Client | Client ID | Settings |
| :--- | :--- | :--- |
| Web (used by Supabase) | `147440499003-cmepur0p2c4441ghta6bqtl91hm7uimd.apps.googleusercontent.com` | redirect URI `https://usqefjhcnehwgszvecym.supabase.co/auth/v1/callback`; its secret is stored in Supabase only |
| Android (debug) | `147440499003-uij7tm2s30u694ke3mtmfqf6qe9hahgq.apps.googleusercontent.com` | package `com.madovygabor.maturimate`, SHA-1 of the local debug key `02:E4:66:92:0C:93:F4:F5:A7:FE:2A:C3:5C:46:60:E3:D6:21:B4:D7` |

Supabase's Google provider lists both client ids (web first). A release build needs one more Android client with the release key's SHA-1. Publishing the Google app (leaving testing mode) needs a homepage, a privacy notice and a verified domain: `maturimate.madovygabor.com` is planned for these.

## E-mail sending

The built-in sender reaches only the project team and a few e-mails an hour, and its templates cannot be changed. Step 4.7 sets up Resend with the `maturimate.madovygabor.com` subdomain as custom SMTP; then "Confirm email" goes back on with the code templates.
