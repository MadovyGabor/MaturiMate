# CodeField

The 6-digit code of an e-mail (sign-up, sign-in confirmation, password reset, password change) as six boxes.

- **The caller provides:** the digits typed so far (a string of at most 6 digits), the change handler, the accessible `label` ("6 jegyű kód", not shown) and, on error, the error text.
- **Six boxes** share the row, `space-2` apart, each at most `code-box-max-width` (56 dp) wide and `code-box-height` (64 dp) tall, `radius-md` corners, `surface-container-lowest` fill, the digit in the monospaced `code` style (without its letter spacing) centered.
- **States:** empty and filled boxes have a `border-thin` `outline` border; the box that takes the next digit has a `border-thick` `primary` border while the field is focused (the last box when the code is complete); with an error every box has a `border-thick` `error` border and the error text appears below with an error icon (the error is never told by color alone), as `body-small` in `error`. A disabled field (while a request runs) has `surface-container-high` boxes.
- **Typing:** the number keyboard; only the digits 0 to 9 count; a digit moves on to the next box, backspace removes the last digit and goes back one box. The logic is `applyCodeInput` (and `activeCodeBox`) in `AppCodeField.kt`, tested without a screen.
- **Paste and auto-fill:** a pasted code (also "123 456", or a code that arrives on top of digits already typed) fills all six boxes; more than six digits are cut. The keyboard's clipboard suggestion and the e-mail code auto-fill both use this path, because the boxes are only the picture of one text field.
- **One hidden text field** takes the input, so the keyboard, backspace, paste, auto-fill and the screen reader work as with any field. Tapping any box focuses it. `contentType = SmsOtpCode` is kept on it (one-time code auto-fill).
- **Screen reader:** one element named by `label`, with the state "3 / 6 jegy beírva" (`code_field_state`) and the error as its error. The six boxes themselves are hidden from the reader (no six empty elements).
- **Touch target:** each box is 64 dp tall; on a phone of 360 dp the boxes are about 48 dp wide. On a very narrow window they shrink with the row; tapping anywhere on the row works, so the target is the row.
- **Motion:** the border color and width change over `duration-short` with the standard easing; with reduced motion they change at once.
- Compose: `AppCodeField` in `core/presentation/uiComponents/AppCodeField.kt`; `autoFocus = true` opens the keyboard when the field appears. Used by `CodeStepContent` (account, sign-in, reset) and the password dialog of the settings. `AppTextField(code = true)` stays for a field that is not the e-mail code.
