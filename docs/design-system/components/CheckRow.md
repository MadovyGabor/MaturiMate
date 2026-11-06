# CheckRow

A row with a tick box, for choosing several things from a tree: the practice picker ticks topics, whole big parts or whole subjects (docs/practice/prototype.html).

- **The caller provides:** the title, the state of the tick box (on, off, or partly on for a group whose items are only partly ticked), what a tick does, a label for screen readers ("Szóképek kiválasztása"), and optionally a second line ("42 feladat", "3 / 5 téma kész"), a tag ("2 kiválasztva") and the fold state with its labels.
- **Three looks:**
  - `SUBJECT`: the header of a subject card, the title in `title-large`.
  - `GROUP`: a big part inside the card, the title in `label-small`, all caps, `on-surface-variant`.
  - `ITEM`: one topic, the title in `title-small`, the second line in `body-small`, `on-surface-variant`. A ticked item sits on `secondary-container` with `on-secondary-container` text, `radius-md`.
- **Touch:** an `ITEM` is ticked by a tap anywhere on the row. A `SUBJECT` or `GROUP` row ticks with its box and folds with the rest of the row (the fold arrow turns), so ticking and folding are two separate targets, each at least `touch-target` (48 dp) tall.
- **Disabled** (a topic not finished yet): the row is faded like a "Hamarosan" settings row and cannot be ticked; its second line says why ("Még nincs kész").
- The color is never the only signal: the box shows the state, and screen readers hear it as a checkbox.
- Compose: `AppCheckRow` in `core/presentation/uiComponents/AppCheckRow.kt` (a Material `TriStateCheckbox`); used by the practice picker.
