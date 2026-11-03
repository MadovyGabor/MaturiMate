# AnswerOption

One option of a multiple-choice task, with a letter key. After checking it shows a correct or wrong state.

- **The caller provides:** the letter, the answer text, the state (`DEFAULT`, `SELECTED`, `CORRECT`, `WRONG`, `DIMMED`) and the click handler.
- `DEFAULT`: `surface-container-lowest` background, `outline` border.
- `SELECTED`: 2 dp `primary` border, `primary-container` background.
- `CORRECT`: 2 dp `success` border, `success-container` background, check icon.
- `WRONG`: 2 dp `error` border, `error-container` background, X icon.
- `DIMMED`: the default look at reduced opacity, for the options that are neither the chosen nor the right one after checking.
- The state is never shown by color alone. After a wrong choice the right option is also shown as `CORRECT`.
- `radius-md` corners, at least `answer-option-min-height` (56 dp) tall, `body-large` text. Options are `space-3` apart.
- Compose: `AppAnswerOption` in `core/presentation/uiComponents/AppAnswerOption.kt` (with `AnswerState`, shared with the true-or-false card); a radio button for accessibility.
