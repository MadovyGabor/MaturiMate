# SettingsRow and SettingsGroup

The rows of the settings screen, in titled groups.

- **Group:** a small icon and the title (`title-small`, `on-surface-variant`) above one `surface-container-low` card with a thin `outline-variant` border, `radius-lg`; the rows inside are divided by thin `outline-variant` lines.
- **Row, the caller provides:** an icon, the name, the current value under it (or nothing), what a tap does, and what sits at the end: an arrow (it opens something), nothing (a value only), a switch (a tap on the row turns it), or a "Hamarosan" tag for a step still to come (the row is greyed and does nothing).
- At least 64 dp tall; the icon in a small `surface-container-highest` square; the name in `title-medium`, the value in `body-medium`.
- **Danger rows** (reset, sign-out, account deletion): the name and the icon in `error`, the icon on `error-container`.
- Compose: `AppSettingsGroup` and `AppSettingsRow` in `core/presentation/uiComponents/AppSettingsRow.kt`; used by the settings screen.
