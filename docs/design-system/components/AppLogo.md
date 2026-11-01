# AppLogo

The MaturiMate logo. Never redrawn or recolored.

- **The caller provides:** the size (`logo-large` on the welcome screen, `logo-small` in the side panel), a content description (none when the app name is written next to it), and optionally the tile shape.
- Light theme: `logoTransparent.png` straight on the cream background.
- Dark theme: `logo.png`, the logo on its own cream square, clipped to a rounded tile (`radius-xl` by default, `radius-md` for the small size).
- The theme in effect decides, not the system setting, so a forced light or dark theme shows the right file.
- Compose: `AppLogo` in `core/presentation/uiComponents/AppLogo.kt`.
