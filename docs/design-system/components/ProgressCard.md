# ProgressCard

A big card of a list the student picks from: a subject, a part, a topic. It shows what it is and how far the student has got, so the list tells where to go on.

- **The caller provides:** the name, an optional line about it (what the part is about), a line about what is in it ("2 téma · 8 lépés", "2 lecke · 3 teszt"), the progress (the bar and its value as text, "2 / 8"), and the click handler. A card that cannot be opened (a subject without content) gets a tag ("Hamarosan") instead of the arrow and no click handler.
- `surface-container-low` with a `border-thin` `outline-variant` border, `radius-lg` corners, `space-4` padding, at least `choice-card-min-height` (64 dp) tall. The name is `headline-small` (the serif), the lines under it `body-medium` in `on-surface-variant`.
- The bar is `brand-gold` and always has its value as text beside it (`label-large`), because gold on the light track has low contrast.
- A card without a click handler is dimmed (`on-surface-variant` text).
- Compose: `AppProgressCard` in `core/presentation/uiComponents/AppProgressCard.kt`.
