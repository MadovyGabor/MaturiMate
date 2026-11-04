# SegmentedProgress

A progress bar of separate segments, one per page of a lesson, at the top of the lesson screen. The segments up to the page being read are filled.

- **The caller provides:** the number of pages, the index of the current page (from 0) and a description for screen readers ("2. oldal / 3").
- Each segment is `progress-bar-height` (8 dp) tall with `radius-full` ends, `space-1` apart. Filled segments are `primary`, the rest `surface-container-highest`.
- The page count is also written as text next to the bar ("2 / 3"), so the position is never only a color.
- Compose: `AppSegmentedProgress` in `core/presentation/uiComponents/AppSegmentedProgress.kt`.
