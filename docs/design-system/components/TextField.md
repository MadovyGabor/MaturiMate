# TextField

Outlined text field with a label and helper or error text.

- **The caller provides:** the label, the value, the helper text and, on error, the error text.
- `surface-container-lowest` background, `outline` border. Focused: 2 dp `primary` border. Error: 2 dp `error` border and `error` helper text.
- The error text says what is wrong and what to do.
- `radius-md` corners, at least 56 dp tall.
- Compose: `AppTextField` in `core/presentation/uiComponents/AppTextField.kt` (an `OutlinedTextField`, one line; `password = true` hides the characters).
- **No floating label:** a field may have no label on its border (the label then sits above it as a small capital line, for example "A VÁLASZOD") and a grey placeholder inside ("Írd ide…"). The short answer of a task is built so.
- **An answer field** (the short answer of a task) is the same field, read-only after checking: `correct = true` makes it `success-container` with a `success` border, `correct = false` `error-container` with an `error` border.
