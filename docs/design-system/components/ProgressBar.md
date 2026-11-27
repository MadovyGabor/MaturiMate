# ProgressBar

Horizontal progress bar. `primary` for steps (onboarding, a practice set), `brand-gold` for the daily goal and the streak.

- **The caller provides:** the fraction (0 to 1), an optional `initialFraction` (so onboarding starts from the previous step without an extra composition) and, for gold bars, the value as text.
- `progress-bar-height` (8 dp) tall, `radius-full`, the empty part is `surface-container-highest`.
- A gold bar always shows its value as text ("7 / 10 perc"), because in the light theme the gold fill has only 1.4:1 contrast against the track.
- A new value fills (or empties) the bar over `duration-medium` with the standard easing; the onboarding bar starts at the previous step when a step opens. With reduced motion it jumps.
- Onboarding shows the step count as text above the bar ("4. lépés / 5").
- Compose: `AppProgressBar` in `core/presentation/uiComponents/AppProgressBar.kt` (a `LinearProgressIndicator` without gap or stop dot).
