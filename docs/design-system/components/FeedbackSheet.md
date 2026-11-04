# FeedbackSheet

The sheet at the bottom of a task after the answer is checked.

- **The caller provides:** whether the answer is right, the title ("Helyes!", "Nem egészen."), the lines (an optional bold label and a text: the right answer, "Miért nem ez?", the explanation) and the button ("Tovább", "Eredmény") with its handler.
- Right: `success-container` with `on-success-container` text and a `success` button. Wrong: `error-container` with `on-error-container` text and an `error` button. A check or an X before the title says it too.
- `radius-xl` on the top corners, no radius at the bottom (it sits on the edge of the screen), `space-4` padding, `space-3` between the lines. Lines are `body-medium`; the texts are the text format of the content (`**bold**`, `*italic*`).
- Compose: `AppFeedbackSheet` in `core/presentation/uiComponents/AppFeedbackSheet.kt`.
