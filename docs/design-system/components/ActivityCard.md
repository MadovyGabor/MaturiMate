# ActivityCard

A finished lesson or test round in a list: the last activities of the home screen and the profile, and the full history.

- **The caller provides:** the title, the line under it (the topic, and the day where the list has no day headings), whether it is a test, the result tag ("Kész", "Átment · 5 / 5", "Újra · 3 / 5") with its style, and optionally a kind tag ("Lecke", "Teszt") shown above the result.
- `surface-container-low` card with a thin `outline-variant` border, `radius-md`, at least 64 dp tall. The icon sits in a small `surface-container-highest` shape: a book in a circle for a lesson, a question mark in a rounded square for a test.
- The result uses [Tag](Tag.md): `SUCCESS` for a lesson done and a passed test, `NEUTRAL` for a test to try again.
- Compose: `AppActivityCard` in `core/presentation/uiComponents/AppActivityCard.kt`; used by the home screen and the profile.
