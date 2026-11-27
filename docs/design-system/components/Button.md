# Button

Pill-shaped button in five variants. One `filled` main button per screen.

- **The caller provides:** the label (a verb: "Tovább", "Kezdjük"), the variant, and whether it is disabled.
- `filled`: the screen's main action. `primary` fill, `on-primary` text.
- `tonal`: a secondary action. `secondary-container` fill, `on-secondary-container` text.
- `outlined`: a third-level action. `outline` border, `primary` text.
- `text`: a quiet action, for example "Körülnézek" on the sign-in screen. `primary` text.
- `destructive`: a destructive filled action (e.g. account deletion, sign-out confirmation). `error` fill, `on-error` text.
- Disabled: `surface-container-highest` fill, `on-surface-variant` text.
- `radius-full` corners, `label-large` text.
- At least `touch-target` (48 dp) tall.
- A press shrinks the button to `press-scale` and back over `duration-short` (not with reduced motion). The Google sign-in has its own button: [GoogleButton](GoogleButton.md).
- Compose: `AppButton` in `core/presentation/uiComponents/AppButton.kt`, with `AppButtonVariant.FILLED` (`Button`), `TONAL` (`FilledTonalButton`, first used by "Összes hiba" on the practice tab), `OUTLINED` (`OutlinedButton`), `TEXT` (`TextButton`), and `DESTRUCTIVE` (`Button` in `error`/`onError`).
- **Compact** (`compact = true`): 36 dp tall (`button-compact-height`) with 16 dp at the sides, for a button inside a card such as the guest card of the profile; the tap area stays 48 dp (`touch-target`).
- **Loading** (`loading = true`): a spinner (`icon-small`, `border-thick` stroke, the text color) before the text while work is under way; the text stays, so the width does not jump. A loading button does not react to taps; screen readers hear "Betöltés…" (`common_loading`). Use it for the screen's main action together with disabling the fields (`enabled = !busy`).
