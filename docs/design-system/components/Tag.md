# Tag

A small label such as "Hamarosan", "Következő", "Kész" or "1 szó".

- **The caller provides:** the text and the style: `NEUTRAL` (`tertiary-container`, a quiet label), `HIGHLIGHT` (`primary-container`, for what is next or a hint) or `SUCCESS` (`success-container`, for what is done).
- `radius-sm` corners, `label-small` text, `space-2` at the sides. The text says what it means; the color only supports it.
- Compose: `AppTag` in `core/presentation/uiComponents/AppTag.kt`; used by the path steps, the progress cards and the short answer of a task.
