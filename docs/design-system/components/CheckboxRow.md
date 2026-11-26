# CheckboxRow

A check box with its text for one yes or no, for example a consent or "I want news". For choosing from a tree use [CheckRow](CheckRow.md).

- **The caller provides:** the text, the state, and optionally an error text ("Ez kötelező").
- The whole row toggles the box; at least `touch-target` (48 dp) tall. Text in `body-medium` (a consent text runs to several lines, and `body-large` made the form too tall), `on-background`; unchecked box `outline`.
- The error text sits under the row in `body-medium`, `error`.
- Disabled: the box and the row do not react (while the screen is busy).
- Compose: `AppCheckboxRow` in `core/presentation/uiComponents/AppCheckboxRow.kt`; used by the account form.
