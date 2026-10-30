# MaturiMate design system

The look of the app: cream paper background, the logo's gold and graphite, with coffee brown as a companion color. Elegant and calm, not gloomy. The system is built on the **Material 3** color roles, so every color token maps one to one to a field of the Compose `MaterialTheme.colorScheme`.

Files in this folder:

| File | Content |
| :--- | :--- |
| [tokens.json](tokens.json) | **The source of truth**: every color (light and dark), type style, spacing step, corner radius and shadow, each with a usage note. |
| [components/](components) | One page per component: what it is for, what the caller provides, which tokens it uses. |
| [screens.html](screens.html) | Sample screens (onboarding, practice, home) in both themes, with a contrast table. Open it in a browser. |

A change starts in `tokens.json`. The Compose theme (`composeApp/.../core/presentation/theme/`) follows it.

## Voice and copy

- Address the user informally (Hungarian *tegeződés*, Slovak *tykanie*), in short sentences: "Mikor érettségizel?", not "Kérjük, adja meg az érettségi dátumát."
- A button says what happens: "Tovább", "Kezdjük", "Fiók létrehozása". Never "OK".
- An error message says what is wrong and how to fix it: "A dátum már elmúlt. Válassz egy későbbi napot."
- No emoji in the interface. Praise is given in words ("Szép munka!"), not with stickers.
- Every text exists in Hungarian and Slovak. Leave room: Slovak text is often longer.

## Color

- The screen background is `background` (cream paper) with `on-background` text. Secondary text is `on-surface-variant`.
- Cards are `surface-container-low`, highlighted ones `surface-container-high`. Raise with surface color rather than shadow.
- One main button per screen, filled with `primary`, text in `on-primary`.
- `brand-gold` is the logo's yellow. Use it only for highlights: progress bars, the streak, illustrations. Never color text with it. Text on it is `on-brand-gold`.
- `secondary` (coffee brown) is for secondary actions, `tertiary` (graphite) for neutral icons and tags.
- Correct answer: `success-container` with a check icon. Wrong answer: `error-container` with an X icon. Color is never the only signal.
- In the dark theme `primary` is a light gold with dark `on-primary` text. White text on gold is not allowed.

## Typography

- Two families: **Literata** (serif) for headings and reading, **Manrope** (sans) for the interface. Both cover Hungarian (ő, ű) and Slovak (č, ľ, ô, ŕ).
- `headline-medium` for onboarding questions, `headline-small` for lesson titles, `reading` for lesson text.
- Buttons and chips: `label-large`. Metadata ("4 PERC OLVASÁS"): `label-small`, all caps, with a little letter spacing.
- A `reading` line is at most about 65 characters. On tablet and desktop the text is centered instead of stretching.

## Shape and spacing

- Corners follow the Material 3 scale: `radius-sm` chip, `radius-md` answer option and text field, `radius-lg` card, `radius-xl` bottom sheet and dialog, `radius-full` button.
- Screen side margin and card padding are `space-4`. Answer options are `space-3` apart, sections `space-6`.
- Touch targets are at least 48 dp tall.

## Icons and logo

- Material Symbols, **Rounded** style, 24 dp, in the color of the surrounding text.
- Do not redraw or recolor the logo. On a cream background use `logo.png`, everywhere else `logoTransparent.png` (both in `composeApp/src/commonMain/composeResources/drawable/`).

## Accessibility

- Every text has at least 4.5:1 contrast on its background, in both themes. Borders, icons and the focus ring have at least 3:1. The measured values are in [screens.html](screens.html).
- Known exception: in the light theme the `brand-gold` progress fill has only 1.4:1 against its empty track. A gold progress bar therefore always shows its value as text too ("7 / 10 perc").
- Keyboard focus is a 2px `focus-ring` outline, 2px away from the element.
- Animations respect the reduced-motion setting.

## Mapping to Compose

- Each color token is the `ColorScheme` field of the same name in camelCase: `primary-container` → `primaryContainer`, `on-surface-variant` → `onSurfaceVariant`. The `background` value also goes into `surface`.
- `brand-gold`, the `success` family and `focus-ring` are not M3 roles. They go into an `ExtendedColors` class provided through a `CompositionLocal`.
- Type styles are the `Typography` fields of the same name (`headline-medium` → `headlineMedium`). `reading` is an extension.
- Radii are the `small`, `medium`, `large` and `extraLarge` fields of `Shapes`.
- Screens use theme values only. No `Color(0xFF...)`, font size or corner radius is written directly in a screen.
