# GapSlot

A gap in a sentence that is filled from a word list. Empty it shows its number; a tap on a word chip under the sentence fills the first empty gap, and a tap on a filled gap empties it again.

- **The caller provides:** the number of the gap, the word in it (null while empty), the state and the click handler.
- Empty: `surface-container-lowest`, `border-thin` `outline`, the number in `on-surface-variant`. Filled: `primary-container` with a `primary` border and the word. After checking: `success-container` with a `success` border (right) or `error-container` with an `error` border (wrong).
- `radius-sm` corners, `gap-slot-height` (40 dp) tall, at least `gap-slot-min-width` (96 dp) wide, `label-large` text.
- Compose: `AppGapSlot` in `core/presentation/uiComponents/AppGapSlot.kt`; the gaps and the words of the sentence are laid out by the screen in a flowing row.
