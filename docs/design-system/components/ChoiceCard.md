# ChoiceCard

One answer from a short list, shown as a card: a badge, a title, a one-line description and a mark. Used for the teaching language and the role in the onboarding.

- **The caller provides:** the badge (a short text such as "HU", or an `icon`-sized icon such as the student's cap), the title, the description, whether it is selected, and the click handler.
- Default: `surface-container-lowest` background, `border-thin` `outline` border; the badge is `surface-container-high` with `on-surface-variant` text; the mark is an empty `outline` circle.
- Selected: `primary-container` background, `border-thick` `primary` border, `on-primary-container` text; the badge is `primary` with `on-primary` text; the mark is a filled `primary` circle with a check. The state is never shown by color alone.
- `radius-lg` corners, at least `choice-card-min-height` (64 dp) tall, badge `badge` (44 dp) with `radius-md`. Cards are `space-3` apart.
- Title `title-medium`, description `body-small`.
- Compose: `AppChoiceCard` in `core/presentation/uiComponents/AppChoiceCard.kt`; a group of cards sits in a `selectableGroup()`, each card is a radio button for accessibility.
