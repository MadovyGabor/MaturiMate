# InfoChip

A small pill with an icon and one short fact, such as "5 napos sorozat" or "Érettségiig 128 nap". Not clickable. For a label without an icon use [Tag](Tag.md).

- **The caller provides:** the text, the icon, and whether it is highlighted: gold (`primary-container`) for a fact that is alive, such as a running streak, otherwise a quiet `tertiary-container`.
- Pill shape, `space-8` (32 dp) tall, `space-3` at the sides, `label-medium` text on one line (cut with an ellipsis if it is too long), an icon of about 18 dp.
- Compose: `AppInfoChip` in `core/presentation/uiComponents/AppInfoChip.kt`; used by the home screen.
