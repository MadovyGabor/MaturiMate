# MatchPair

An item of a matching task and the answer matched to it. A tap on the item makes it the active one; the answer is then picked from the chips under the list, and the next item without an answer becomes active.

- **The caller provides:** the item text, the answer matched to it (null while it has none) and the placeholder ("válassz…"), whether it is the active item, the state and the click handler.
- `surface-container-low` with a `border-thin` `outline-variant` border; the active item has a `border-thick` `primary` border. The answer sits in a small box: empty `surface-container-lowest` with an `outline` border, filled `primary-container`.
- After checking a wrong pair also shows what belongs to it ("Helyes: metafora", `on-background` (neutral, not green)).
- After checking the whole card is `success-container` with a `success` border and a check (right), or `error-container` with an `error` border and an X (wrong).
- `radius-md` corners, at least `choice-card-min-height` (64 dp) tall, `space-3` padding.
- Compose: `AppMatchPair` in `core/presentation/uiComponents/AppMatchPair.kt`; the answer chips are `AppChoiceChip`.
