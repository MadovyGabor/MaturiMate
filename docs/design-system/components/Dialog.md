# Dialog

Short confirmations (sign out, reset progress), one-line choices (app language, export target), and name editing.

- **Container:** `surface-container-high` fill, thin `outline-variant` border, `radius-xl` (`extraLarge`), `dialog-max-width` (400 dp) max width, `space-6` inner padding.
- **Title and text:** `title-large` (`on-background`, marked as heading) and one short sentence in `body-large` (`on-surface-variant`), plus optional custom `content` for choices, text fields, or inline errors.
- **Buttons:** right-aligned `AppButton`s: `TEXT` for Cancel, `FILLED` or `DESTRUCTIVE` (`error` fill) for the primary action, with `confirmLoading` disabling dismissal and showing the spinner.
- **Compose:** `AppDialog` in `core/presentation/uiComponents/AppDialog.kt`.
