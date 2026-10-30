# AnswerOption

One option of a multiple-choice task, with a letter key. After checking it shows a correct or wrong state.

- **The caller provides:** the letter, the answer text, the state (`default`, `selected`, `correct`, `wrong`) and the click handler.
- `default`: `surface-container-lowest` background, `outline` border.
- `selected`: 2 dp `primary` border, `primary-container` background.
- `correct`: 2 dp `success` border, `success-container` background, check icon.
- `wrong`: 2 dp `error` border, `error-container` background, X icon.
- The state is never shown by color alone. After a wrong choice the right option is also shown as `correct`.
- `radius-md` corners, at least 56 dp tall, `body-large` text. Options are `space-3` apart.
