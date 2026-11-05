# StatCard

A few numbers side by side in one card, for example the streak, the study time and the solved tasks of the profile.

- **The caller provides:** for each number an icon, the value as numbers and units ("12", "ó", "40", "p"), a short label under it, and the sentence a screen reader says instead ("12 óra 40 perc tanulás").
- `surface-container-high` card, `radius-lg`; the numbers share the width and are divided by thin `outline-variant` lines.
- Each number: a 24 dp icon in a gold circle (`primary-container`), the value in `headline-small` on one line with the units smaller (`label-large`, `on-surface-variant`), the label in `body-medium`.
- Compose: `AppStatCard` in `core/presentation/uiComponents/AppStatCard.kt`; used by the profile.
