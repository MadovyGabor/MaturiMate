# ChoiceChip

A short option from a small set, for example the daily goal, the English level or the language.

- **The caller provides:** the label, whether it is selected, and the click handler.
- Default: `outline` border, `on-background` text.
- Selected: `primary-container` background, `on-primary-container` text, check icon.
- `radius-sm` corners, 40 dp tall with a 48 dp touch target. Chips are `space-2` apart.
- Compose: `AppChoiceChip` in `core/presentation/uiComponents/AppChoiceChip.kt` (a `FilterChip`, at least `touch-target` tall, a radio button for accessibility).
