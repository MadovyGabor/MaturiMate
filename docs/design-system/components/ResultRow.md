# ResultRow

One task in the result of a test round: a check or an X and the question. A wrong task opens on a tap and shows what to learn from it.

- **The caller provides:** whether the answer was right, the question as plain text and, for a wrong task, the lines that open: the right answer, "Miért nem ez?" (the `why` of the option the student chose) and the explanation.
- `surface-container-low` with a `border-thin` `outline-variant` border, `radius-md` corners, at least `touch-target` (48 dp) tall, `space-3` padding. The mark is a `success` check or an `error` X; the lines are `body-medium` in `on-surface-variant`.
- A right task does not open (no chevron); a wrong one shows a chevron that turns up when it is open. The question is cut after two lines while the row is closed.
- Compose: `AppResultRow` in `core/presentation/uiComponents/AppResultRow.kt`.
