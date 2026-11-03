# PathStep

One step of a topic's learning path: a node on a vertical line and a card. A column of steps, with no gap between the rows, shows the progress: the line is `brand-gold` down to the current step and `surface-container-highest` below it.

- **The caller provides:** the style (`LESSON`, `MINI`, `TEST`), the state (`DONE`, `CURRENT`, `TODO`), the title, a meta line ("3 perc", "5 feladat"), an optional state label ("Kész", "Következő"), whether it is the first or the last step, and the click handler. A final test of a parent topic uses the `TEST` style.
- **Node.** A lesson is a circle (`path-node`, 42 dp) with a book icon; a mini test a smaller circle (`path-node-small`, 34 dp) with a quiz icon; a test a rounded square (`radius-md`, `path-node`) with a trophy icon. Done: `primary` with a check. Current: `brand-gold` with a `primary` border and a `primary-container` halo. Todo: `surface-container-high` with an `outline` border. The state is never shown by color alone: the check, the halo and the label say it too.
- **Card.** Lesson: `surface-container-low`, `border-thin` `outline-variant`, title `title-medium`. Mini test: `tertiary-container`, a smaller padding and `title-small`. Test: `primary-container`, no border. The current step has a `border-thick` `primary` border. `radius-md` corners, `space-3` from the node.
- The line is `path-line` (4 dp) thick and is drawn by the steps themselves, so rows must not be separated by a gap.
- Compose: `AppPathStep` in `core/presentation/uiComponents/AppPathStep.kt`; the card is clickable as a button.
