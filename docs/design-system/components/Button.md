# Button

Pill-shaped button in four variants. One `filled` main button per screen.

- **The caller provides:** the label (a verb: "Tovább", "Kezdjük"), the variant, and whether it is disabled.
- `filled`: the screen's main action. `primary` fill, `on-primary` text.
- `tonal`: a secondary action. `secondary-container` fill, `on-secondary-container` text.
- `outlined`: a third-level action. `outline` border, `primary` text.
- `text`: a quiet action, for example "Körülnézek" on the sign-in screen. `primary` text.
- Disabled: `surface-container-highest` fill, `on-surface-variant` text.
- `radius-full` corners, `label-large` text.
- At least `touch-target` (48 dp) tall.
- Compose: `AppButton` in `core/presentation/uiComponents/AppButton.kt`, with `AppButtonVariant.FILLED` (`Button`) and `AppButtonVariant.TEXT` (`TextButton`). `tonal` (`FilledTonalButton`) and `outlined` (`OutlinedButton`) are added when a screen needs them.
