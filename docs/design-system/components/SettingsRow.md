# SettingsRow and SettingsGroup

The rows of the settings screen, in titled groups.

- **Group:** a small icon and the title (`title-small`, `on-surface-variant`) above one `surface-container-low` card with a thin `outline-variant` border, `radius-lg`; the rows inside are divided by thin `outline-variant` lines.
- **Row, the caller provides:** an icon, the name, the current value under it (or nothing, with optional `valueTone`: default, `success`, `error`), what a tap does, `animateSize` (only on dynamic status rows), and what sits at the end (`SettingsTrailing`): an arrow (`Arrow`), nothing (`None`), a switch (`Toggle`), a spinner (`Busy`), a compact tonal action button (`Action`, e.g. Retry / Download / Upload), or a "Hamarosan" tag (`Soon`, row greyed and disabled).
- At least 64 dp tall; the icon in a small `surface-container-highest` square; the name in `title-medium`, the value in `body-medium`.
- **Danger rows** (reset, sign-out, account deletion): the name and the icon in `error`, the icon on `error-container`.
- Compose: `AppSettingsGroup` and `AppSettingsRow` in `core/presentation/uiComponents/AppSettingsRow.kt`; used by the settings screen.
