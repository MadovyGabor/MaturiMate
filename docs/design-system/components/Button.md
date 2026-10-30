# Button

Pill-shaped button in four variants. One `filled` main button per screen.

- **The caller provides:** the label (a verb: "Tovább", "Kezdjük"), the variant, and whether it is disabled.
- `filled`: the screen's main action. `primary` fill, `on-primary` text.
- `tonal`: a secondary action. `secondary-container` fill, `on-secondary-container` text.
- `outlined`: a third-level action. `outline` border, `primary` text.
- `text`: a quiet action, for example "Körülnézek" on the sign-in screen. `primary` text.
- Disabled: `surface-container-highest` fill, `on-surface-variant` text.
- At least 48 dp tall, `radius-full` corners, `label-large` text.
- Compose: `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`.
