---
kind: frontend_style
name: Static Site CSS with Design Tokens + Android Material DayNight Theme
category: frontend_style
scope:
    - '**'
source_files:
    - site/style.css
    - site/guide.css
    - app/src/main/res/values/themes.xml
---

## What system/approach is used

The repository has two distinct frontend styling surfaces:

1. **`site/` — static product website** built with plain HTML + vanilla CSS (no framework, no build step). Styling lives in `site/style.css` (~718 lines) and a smaller `site/guide.css` for the generated guide pages. The site uses a hand-authored design-token system via CSS custom properties on `:root`, with separate light/dark palettes driven by both `prefers-color-scheme: dark` and an explicit `[data-theme="dark"]` override toggled by JS.
2. **`app/` — Android app** styled through Android's native theming. `app/src/main/res/values/themes.xml` declares `Theme.JevProbe` as a thin wrapper around `Theme.MaterialComponents.DayNight.NoActionBar`. There is no Jetpack Compose code in this branch (grep finds zero `@Composable` / Compose imports); UI is XML-based Activity layout using Material Components.

No Tailwind, SCSS, Sass, PostCSS, or component library is present. The site also ships its own small JavaScript (`main.js`, `i18n.js`) for theme toggle, demo animation, and i18n switching, but styling itself is pure CSS.

## Key files and packages

- `site/style.css` — primary stylesheet; defines all tokens, layout, components, animations, responsive breakpoints, print/reduced-motion rules.
- `site/guide.css` — shared styles for the auto-generated Chinese/English guides under `site/guides/`.
- `site/index.html`, `site/en.html`, `site/privacy.html` — HTML entry points that load `style.css` (and `guide.css` for guides).
- `app/src/main/res/values/themes.xml` — Android theme declaration.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/xml/config_disguised.xml` — Android resources consumed by the Activities.

## Architecture and conventions

### Design tokens (site)
All colors, spacing, radii, fonts, shadows, and gradients are declared as CSS custom properties at the top of `style.css` inside `:root` (light) and duplicated under `@media (prefers-color-scheme: dark)` plus `:root[data-theme="dark"]`. Token categories observed:

- Backgrounds: `--bg`, `--bg-tint`, `--card`, `--card-2`
- Lines/borders: `--line`, `--line-2`
- Text: `--ink`, `--ink-2`, `--ink-3`
- Brand: `--blue`, `--blue-ink`, `--blue-soft`, `--blue-line`
- Semantic risk: `--safe`, `--warn`, `--risk` plus their background variants
- Buttons: `--btn-bg`, `--btn-ink`, `--btn-hover`, `--btn-shadow`
- Decorative: `--star`, `--glow-a`, `--glow-b`, `--band`
- Phone mockup: `--ph-frame`, `--ph-bg`, `--ph-head`, `--ph-them`, `--ph-input`, `--ph-field`, `--ph-ink-2`
- Spacing/radius: `--r-sm`, `--r`, `--r-lg`
- Typography: `--font`, `--ui`, `--mono`

Typography tokens use a deliberate font stack prioritizing system fonts plus CJK fallbacks (`PingFang SC`, `HarmonyOS Sans SC`, `MiSans`, `Microsoft YaHei UI`, `Noto Sans SC`, `Source Han Sans SC`).

### Responsive strategy
- Mobile-first utility classes (`.wrap`, `.btn`, `.sec`, etc.) with progressive enhancement via `@media (min-width: ...)` breakpoints at ~520/640/720/800/900/960/980/1024/1100/1120/1270px.
- Fluid typography via `clamp()` on headings and section sizes.
- Grid layouts switch from single-column to multi-column grids at media queries.
- Navigation collapses to a burger menu below ~520–599px depending on language.
- `@media (prefers-reduced-motion: reduce)` disables all transitions/animations.
- `@media print` hides nav/demo/footer and forces scroll animations to be visible.

### Component model
Classes follow a flat BEM-like naming without a preprocessor: semantic block names (`.hero`, `.nav`, `.demo`, `.mm-grid`, `.jq`, `.cmp`, `.flow-wrap`, `.apps-grid`, `.steps`, `.faq-grid`, `.contact-grid`, `.end`, `.foot`, `.policy`) with nested modifiers (`.btn-primary`, `.btn-ghost`, `.mm-risk`, `.fn.local`, `.state.ocr`, etc.). No CSS-in-JS, no scoped styles, no shadow DOM.

### Animation model
Animations are state-driven via class toggles applied by `main.js` (e.g., `.demo-anim.is-msg`, `.is-panel`, `.is-badge`, `.is-fill`, `.is-send`). A `demo-instant` class disables all transitions globally. Keyframes include `caret`, `spin`, `ping`, `tap`, `packet`.

### Android theming
The app uses Material Components' DayNight theme family. The custom `Theme.JevProbe` is intentionally minimal — it inherits everything from `Theme.MaterialComponents.DayNight.NoActionBar`, so the app automatically adapts to system light/dark mode without additional style overrides in this branch.

### Cross-app visual consistency
The comment header in `style.css` explicitly states the design intent: the website's visual language mirrors the floating overlay panel from the Android app (`OverlayController.kt`). The phone mockup, chat bubbles, risk meter (0–9 scale), and annotation card structure in the site are described as "grown out of" the app's overlay UI.

## Conventions and constraints

- All colors and sizing go through CSS custom properties on `:root`; hard-coded color literals are avoided outside token declarations and the phone mockup's internal elements.
- Dark mode is supported in two ways: automatic via `prefers-color-scheme: dark` and manual override via `[data-theme="dark"]` on `:root` (controlled by JS).
- Language-specific behavior is gated on `html[lang="en"]` vs. default Chinese, including different navigation breakpoints and line-height adjustments.
- Accessibility: skip-link (`.skip`), focus-visible outlines, screen-reader-only text (`.sr`), reduced-motion support, and semantic HTML (`details/summary` for FAQ).
- Print stylesheet hides interactive chrome and forces animated content to render statically.
- The Android app does not define any custom colors/styles beyond inheriting Material Components DayNight — there are no additional `values/colors.xml` or `values/styles.xml` overrides in this branch.
- No CSS framework or build toolchain is configured; styles are served directly from the static `site/` directory.