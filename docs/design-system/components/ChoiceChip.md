# ChoiceChip

A short option from a small set, for example the daily goal, the English level or the language.

- **The caller provides:** the label, whether it is selected, and the click handler.
- Default: `outline` border, `on-background` text.
- Selected: `primary-container` background, `on-primary-container` text, check icon.
- Choosing a chip fades its colors over `duration-short`; a press shrinks it to `press-scale`. With reduced motion both are instant.
- `radius-sm` corners, 40 dp tall with a 48 dp touch target. Chips are `space-2` apart.
- Compose: `AppChoiceChip` in `core/presentation/uiComponents/AppChoiceChip.kt` (a radio-button chip at least `touch-target` tall, with draw-phase color/border animations).
