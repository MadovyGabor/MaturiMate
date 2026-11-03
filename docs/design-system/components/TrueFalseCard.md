# TrueFalseCard

One of the two big answers of a true-or-false task: an icon in a circle and the word ("Igaz", "Hamis"). The two cards sit side by side under the statement, at the bottom of the screen with some space above the check button.

- **The caller provides:** the word, the icon (a check for true, an X for false), the state and the click handler.
- The states are those of [AnswerOption](AnswerOption.md) (`DEFAULT`, `SELECTED`, `CORRECT`, `WRONG`, `DIMMED`).
- The icon circle (`badge`, 44 dp) is neutral (`tertiary-container`) before the student chooses, so the colors do not give the answer away; chosen it is `primary`, and after checking `success` or `error`.
- `radius-lg` corners, at least `true-false-card-min-height` (140 dp) tall, the word in `title-large`.
- Compose: `AppTrueFalseCard` in `core/presentation/uiComponents/AppTrueFalseCard.kt`; a radio button for accessibility.
