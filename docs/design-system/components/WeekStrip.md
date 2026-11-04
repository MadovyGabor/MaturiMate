# WeekStrip

The seven days of the current week, Monday first, one circle per day with its short name under it. Used under the daily goal on the home screen (tablet and desktop).

- **The caller provides:** for each day the short name, the state (`DONE`, `MISSED`, `TODAY`, `LATER`) and a description for screen readers.
- `week-dot` (30 dp) circles. `DONE`: `brand-gold` with a check mark in `on-brand-gold`. `MISSED`: `surface-container-highest`. `TODAY`: a `border-thick` outline in `primary`, and the name in `on-background`. `LATER`: a `border-thin` outline in `outline-variant`.
- The names are `label-small` in `on-surface-variant`.
- Compose: `AppWeekStrip` in `core/presentation/uiComponents/AppWeekStrip.kt`.
