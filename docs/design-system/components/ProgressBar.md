# ProgressBar

Horizontal progress bar. `primary` for steps (onboarding, a practice set), `brand-gold` for the daily goal and the streak.

- **The caller provides:** the fraction (0 to 1) and, for gold bars, the value as text.
- `progress-bar-height` (8 dp) tall, `radius-full`, the empty part is `surface-container-highest`.
- A gold bar always shows its value as text ("7 / 10 perc"), because in the light theme the gold fill has only 1.4:1 contrast against the track.
- Onboarding shows the step count as text above the bar ("4. lépés / 5").
- Compose: `AppProgressBar` in `core/presentation/uiComponents/AppProgressBar.kt` (a `LinearProgressIndicator` without gap or stop dot).
