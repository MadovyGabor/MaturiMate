# LessonBox

A box inside a lesson page: a small label and a short text. The kind of the box is written in the lesson file with a language-independent keyword (`:::example`, `:::tip`, `:::note`, `:::warning`, `:::quote`); the label is an app string in both languages.

- **The caller provides:** the kind, the label ("Példa", "Tipp", "Jegyzet", "Figyelem", "Idézet") and the text (paragraphs separated by a blank line, lists with `- ` or `1. ` lines, see [RichText](RichText.md)).
- **Example:** `tertiary-container` with `on-tertiary-container` text, no icon.
- **Tip:** `surface-container-low`, a `brand-gold` bar of `accent-bar` (4 dp) at the left edge and a light bulb. The text stays `on-background`, never gold.
- **Note:** `secondary-container` with an info icon.
- **Warning:** `error-container` with a warning icon.
- **Quote:** `surface-container-low`, a `secondary` bar of `accent-bar` (4 dp) at the left edge and a quote icon (`FormatQuote`), with `on-background` text.
- `radius-md` corners, `space-3` padding. The label is `label-small` in capitals; the text is the `reading` style. The kind is shown by the label (and the icon), not by color alone.
- Compose: `AppLessonBox` in `core/presentation/uiComponents/AppLessonBox.kt`; the text is drawn by `AppRichText` (`**bold**`, `*italic*`, lists).
