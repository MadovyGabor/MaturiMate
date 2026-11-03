# ProgressRing

A circle that fills as the steps of a topic are done, with the count in the middle ("1/3"). Used in the topic list.

- **The caller provides:** the number of steps done, the number of steps, and a description for screen readers ("1 of 3 steps done").
- `progress-ring` (40 dp) wide, a `progress-ring-stroke` (4 dp) round stroke. The filled arc is `primary` and starts at the top; the rest is `surface-container-highest`.
- The count is `label-small` in `on-background`, so the value is always text, not only color.
- Compose: `AppProgressRing` in `core/presentation/uiComponents/AppProgressRing.kt`.
