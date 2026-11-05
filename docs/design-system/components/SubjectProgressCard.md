# SubjectProgressCard

The progress of one subject on the profile, which opens into its big parts.

- **The caller provides:** the subject's name, its share done as a fraction and as text ("48%"), the counts under it ("Leckék 8 / 16", "Tesztek 2 / 5"), the same for each big part (Nyelvtan, Irodalom), whether it is open, and the labels a screen reader says for opening and closing. For a subject without content yet it passes the "Hamarosan" text instead, and the card shows that tag and does not open.
- `surface-container-low` card with a thin `outline-variant` border, `radius-lg`. The name in `title-large`, the share at the end of the row with an arrow that turns when the card opens, a gold [ProgressBar](ProgressBar.md) (the share is always written beside it).
- Open: under a thin line, each part in a small `surface-container-high` card of its own (`radius-md`), so the parts do not run together. The parts have no button; they show the numbers only.
- Compose: `AppSubjectProgressCard` in `core/presentation/uiComponents/AppSubjectProgressCard.kt`; used by the profile.
