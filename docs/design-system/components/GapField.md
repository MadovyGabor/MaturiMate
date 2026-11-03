# GapField

A gap in a sentence that is filled by typing: a small text field in the line of text.

- **The caller provides:** the value, the change handler, a description for screen readers ("2. hézag") and, after checking, the state.
- `surface-container-lowest` with a `border-thin` `outline` border while writing; after checking `success-container` with a `success` border (right) or `error-container` with an `error` border (wrong), and the field is read-only.
- `radius-sm` corners, `gap-field-width` (128 dp) wide, `gap-slot-height` (40 dp) tall, `body-large` text. The keyboard's action is "next", so the next gap gets the focus.
- Compose: `AppGapField` in `core/presentation/uiComponents/AppGapField.kt` (a `BasicTextField`).
