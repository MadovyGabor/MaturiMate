# GoalRing

The daily goal as a ring on the home screen. The gold arc fills as the minutes studied today add up to the goal; when the goal is reached the ring turns green and shows a check mark.

- **The caller provides:** the fraction done (0 to 1), whether the goal is reached, the minutes studied (the value), a caption under it ("/ 10 perc") and a description for screen readers.
- `goal-ring` (88 dp) wide, a `goal-ring-stroke` (10 dp) round stroke starting at the top. The track is `surface-container-highest`, the arc `brand-gold` (highlights are what the gold is for), `success` once the goal is reached.
- The value is `title-large`, the caption `label-small`; the number is always text, not only color.
- Compose: `AppGoalRing` in `core/presentation/uiComponents/AppGoalRing.kt`; used by the home screen.
