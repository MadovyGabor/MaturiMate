# Snackbar

The short message at the bottom of the screen: "Mentve", an error after a failed save. One host for the whole app (`MainScaffoldWrapper`), raised above the bottom bar of the main frame.

- **The caller provides** (through `SnackbarController.showSnackbar`): the text, and the kind.
- **Three kinds** (`SnackbarKind`): `INFO` (the default, the quiet one: `inverse-surface` with `inverse-on-surface` text, no icon); `SUCCESS` (something worked: `success-container` with `on-success-container` text and a check icon in `success`); `ERROR` (something failed or is missing: `error-container` with `on-error-container` text and an error icon in `error`). The kind is told by the icon too, not by color alone; the icon is read as "Kész" / "Hiba" (`snackbar_success`, `snackbar_error`).
- `radius-xs` corners. Four seconds on screen (`SnackbarDuration.Short`), one at a time.
- **Motion:** it comes in with the soft entrance of the motion tokens: it fades in over `duration-medium` while sliding up by `slide-offset`; it leaves with the fade of the host. With reduced motion it appears at once.
- **Where each kind is used:** `SUCCESS` for the "saved" messages of the settings (name, password, reset, export); `ERROR` for every failed save or request (the error texts of the data and auth layers) and for the missing consent of the account form; `INFO` for a plain note (the locked test of a topic).
- Compose: `AppSnackbar` in `core/presentation/uiComponents/AppSnackbar.kt`; the kind travels in `AppSnackbarVisuals`.
