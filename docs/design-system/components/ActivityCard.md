# ActivityCard

A finished lesson, test round or practice round in a list: the last activities of the home screen and the profile, and the full history.

- **The caller provides:** the title, the line under it (the topic of a lesson or test, the subject of a practice round, and the day where the list has no day headings), the icon kind, the result tag ("Kész", "Átment · 5 / 5", "Újra · 3 / 5", or "8 / 10" for practice) with its style, and optionally a kind tag ("Lecke", "Teszt", "Gyakorlás") shown above the result.
- `surface-container-low` card with a thin `outline-variant` border, `radius-md`, at least 64 dp tall. The icon sits in a small `surface-container-highest` shape: a book in a circle for a lesson, a question mark in a rounded square for a test, a light bulb in a circle for practice (the icon of the practice tab).
- The result uses [Tag](Tag.md): `SUCCESS` for a lesson done, a passed test and a practice round with every answer right; `NEUTRAL` for a test to try again and any other practice round. Practice has no passing, so its result is only the score.
- Compose: `AppActivityCard` in `core/presentation/uiComponents/AppActivityCard.kt` (`ActivityIcon`: `LESSON`, `TEST`, `PRACTICE`); used by the home screen and the profile.
