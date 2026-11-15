# TextField

Outlined text field with a label and helper or error text.

- **The caller provides:** the label, the value, the helper text and, on error, the error text.
- `surface-container-lowest` background, `outline` border. Focused: 2 dp `primary` border. Error: 2 dp `error` border and `error` helper text.
- The error text says what is wrong and what to do.
- `radius-md` corners, at least 56 dp tall.
- Compose: `AppTextField` in `core/presentation/uiComponents/AppTextField.kt` (an `OutlinedTextField`, one line; `password = true` hides the characters).
- **No floating label:** a field may have no label on its border (the label then sits above it as a small capital line, for example "A VÁLASZOD") and a grey placeholder inside ("Írd ide…"). The short answer of a task is built so.
- **An answer field** (the short answer of a task) is the same field, read-only after checking: `correct = true` makes it `success-container` with a `success` border, `correct = false` `error-container` with an `error` border.
- **No fixed height:** the field is as tall as Material makes it (at least `touch-target`); the helper or error text under it is not part of that height, so a message appearing does not push the field.
- **Password** (`password = true`): the characters are hidden and a trailing icon button shows or hides them (content description "Jelszó megjelenítése" / "Jelszó elrejtése", `password_show`/`password_hide`). The state is kept across rotation. Sign-up and reset have no repeat field: the toggle replaces it.
- **Content type** (`contentType`): what the field holds, for autofill and the password manager: `ContentType.EmailAddress`, `Password`, `NewPassword`, `PersonFirstName`, `PersonLastName`. Set it on every account field.
- **Code** (`code = true`): a one-time code from an e-mail. Number keyboard, the `code` text style (monospace, `letter-spacing` 6 px, so the digits are easy to compare) and the one-time-code content type. Not six boxes.
- **Action key** (`imeAction`, `onImeAction`): `ImeAction.Next` on a field that is followed by another (the focus moves on), `ImeAction.Done` on the last field of a form; give `onImeAction` to run the form's action (for example save) instead. Default: the keyboard's own action, nothing runs.
