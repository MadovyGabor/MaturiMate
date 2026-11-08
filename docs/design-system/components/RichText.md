# RichText

The text of the content (lessons, tasks, explanations) in a given type style. One component draws all of it, so the text format of the content looks the same everywhere.

- **The caller provides:** the text and the style (`reading` for lessons, `headline-small` for questions, `body-large` for options); the color is the current content color.
- **The text format:** `**bold**` (semi-bold), `*italic*`, paragraphs separated by a blank line, and lists. The writing rules are in [tools/content-pipeline/README.md](../../../tools/content-pipeline/README.md).
- **Paragraphs:** one under the other, `space-3` between them.
- **Bulleted list** (`- item` lines): a "•" in the text color, then the item; the item text wraps under itself, not under the bullet.
- **Numbered list** (`1. item` lines): the number and a dot, counted by position (the written numbers do not matter), then the item.
- Items of a list are `space-1` apart, the marker and the text `space-2`; the list as a whole is one block of the paragraph spacing. The marker uses the style of the text, so a list in a box keeps the box's look.
- Lists are only in lessons (the pipeline rejects them in tasks); the component draws them wherever it is used.
- Compose: `AppRichText` in `core/presentation/utils/RichText.kt`; `textBlocks()` splits the text into paragraphs and lists.
