# Design

## Context

See `proposal.md` for motivation. The frontend is a Vue 3 + Vite + TypeScript SPA that imports Element Plus wholesale in `src/main.ts` and adds only a small global stylesheet. Screens rely on Element Plus defaults, with a handful of scoped rules for layout. There is no theme layer, no shared page primitives, and no design tokens. The router currently exposes `/about`; the header renders a technology-stack footer.

Constraints:

- Keep Vue 3 + Element Plus; no new UI dependency and no build-pipeline change.
- Keep the current routes, stores, APIs, role checks, and information architecture; only the About entry point is removed.
- No backend, database, or API changes.
- No external font or image CDN; the deployment may be offline or on a campus intranet.
- The application is served over both HTTPS and plain HTTP; visual work must not depend on secure-context-only APIs.
- Existing `npm test` (clipboard) and `npm run build` must stay green.

## Goals / Non-Goals

**Goals:**

- Establish one Academic Clarity token set and apply it to every screen.
- Create a clear visual hierarchy for the chat workspace: steps, answer, SQL, result, composer.
- Make the app shell feel like a campus product rather than a framework demo.
- Remove the About entry point without leaving a dead route.
- Keep the diff reviewable: global tokens first, shared shell second, screens third.
- Meet WCAG AA contrast for text and controls and keep keyboard focus visible.

**Non-Goals:**

- Dark mode, theme switching, or user-selectable themes.
- Replacing Element Plus, adding Tailwind/UnoCSS, or introducing a component library.
- Changing page structure, routes (other than About), stores, APIs, or permissions.
- Adding a logo asset, illustration library, or icon pack.
- Rewriting copy beyond the shell/footer and screen titles needed for the new tone.
- Automated visual regression testing; verification is manual at defined viewports.

## Decisions

### 1. Token-first CSS variables, not a SCSS theme build

Define the palette, type scale, spacing, radii, shadows, and motion in `src/style.css` as `:root` custom properties, and map them onto Element Plus's published variables (`--el-color-primary`, `--el-color-primary-light-3/5/7/8/9`, `--el-color-primary-dark-2`, `--el-text-color-*`, `--el-border-color-*`, `--el-fill-color-*`, `--el-border-radius-*`, `--el-bg-color*`). Screens consume either the Element Plus variables or a small set of `--ac-*` aliases.

Why: no build change, hot-reloadable, and every Element Plus component inherits the theme automatically. Alternative considered: SCSS theme compilation (`@use "element-plus/theme-chalk/src/index.scss" with (...)`), which requires a Sass dependency and a Vite CSS preprocessor change. Rejected for a visual-only change. Alternative: per-component overrides, rejected because it drifts and multiplies maintenance.

### 2. Palette and type scale

| Role | Token | Value |
| --- | --- | --- |
| Primary / action | `--el-color-primary` | `#1E5AA8` |
| Primary hover/active tints | `--el-color-primary-light-3/5/7/8/9` | `#628CC2` / `#8FADD4` / `#BCCEE5` / `#D2DEEE` / `#E9EFF6` |
| Primary pressed | `--el-color-primary-dark-2` | `#184886` |
| Data / insight / success | `--el-color-success` | `#0F9D8A` |
| Warning | `--el-color-warning` | `#D97706` |
| Danger | `--el-color-danger` | `#DC2626` |
| Info / muted | `--el-color-info` | `#64748B` |
| Page canvas | `--el-bg-color-page` | `#F6F7F9` |
| Surface | `--el-bg-color` / `--el-bg-color-overlay` | `#FFFFFF` |
| Ink text | `--el-text-color-primary` | `#1F2A37` |
| Body text | `--el-text-color-regular` | `#475467` |
| Secondary text | `--el-text-color-secondary` | `#667085` |
| Placeholder | `--el-text-color-placeholder` | `#98A2B3` |
| Border | `--el-border-color` / `--el-border-color-light` | `#E4E7EC` / `#EEF0F3` |
| Radius | `--el-border-radius-base` / small | `10px` / `8px` |
| Shadow | `--ac-shadow-sm` / `--ac-shadow-md` | `0 1px 2px rgba(16,24,40,.04)` / `0 8px 24px rgba(16,24,40,.06)` |

Typography:

- Body: `"PingFang SC", "Microsoft YaHei", "Noto Sans SC", "Helvetica Neue", Arial, sans-serif`.
- Display (brand wordmark and page titles only): `"Noto Serif SC", "Source Han Serif SC", "Songti SC", "SimSun", serif`; keep it to short headings so a fallback face cannot hurt long prose.
- Code/SQL: `"Cascadia Code", "JetBrains Mono", Consolas, "Courier New", monospace`.
- Scale: 12px meta, 14px body/controls, 16px lead, 20px page title, 28px brand/hero; line height 1.6 for prose, 1.4 for controls.

Data-visualization palette (categorical, used by `ResultChart`): `#1E5AA8`, `#0F9D8A`, `#D97706`, `#7C6BC4`, `#4B8BBE`, `#94A3B8`. Teal is reserved for data/insight, not for primary actions.

### 3. Keep the information architecture, restyle the shell

Keep `App.vue`'s header/body/footer structure and the chat sidebar. Visual changes only:

- Header: 64px white surface with a bottom border; a CSS-only brand mark (rounded square with a simple “T2S” monogram) plus the product name and a small “智慧校园数据问答” descriptor; primary navigation with an active underline/pill; account menu on the right.
- Sidebar: white/paper surface, a prominent “新建对话” primary button, date groups, active row with a 3px primary accent and a light primary tint; row actions remain hover/focus revealed.
- Main canvas: `#F6F7F9` page background, page content centered with a max-width of 1200px and 24px gutters; chat uses a wider 1280px workspace and a full-height surface.
- Footer: replace the technology stack with user-facing copy: “智慧校园数据问答 · 结果仅限当前账号的数据权限范围”.
- Remove the About menu item and route; redirect `/about` to `/`.

Alternative considered: a dark sidebar for a “portal” look. Rejected because it fights the chosen light academic direction and makes the chat transcript feel cramped. Alternative: a left rail with icons only. Rejected because it changes navigation behavior and the current sidebar carries conversation history.

### 4. Chat workspace hierarchy

- User messages: right-aligned, primary-tinted pill, maximum 78% width.
- Assistant turns: a full-width surface card with a small agent identity row, a numbered/step list, the answer prose, then SQL and results. The card uses a light border and `--ac-shadow-sm`, not a heavy shadow.
- SQL: a dedicated ink surface (`#0F172A`) with muted blue-grey text (`#D6E2F0`), 13px monospace, horizontal scroll, copy button in the block header. The copy control reuses the existing `copyText` fallback.
- Results: table header tinted with `--el-fill-color-light`, zebra rows, sticky header where the table is tall; charts use the categorical palette above; the chart card sits below the answer with consistent 16px spacing.
- Steps: teal dots for completed steps, a pulsing teal dot for the live step; step text stays 12px secondary.
- Composer: white rounded surface, 12px radius, focus ring using `--el-color-primary-light-5`; example chips become outlined neutral chips with a hover tint.

Alternative considered: keep the existing grey bubble for assistant answers. Rejected because it flattens the SQL/result hierarchy and wastes the full-width layout the chat already uses.

### 5. Login as a two-panel academic card

Desktop: a centered card with a left brand panel (deep primary gradient with a subtle CSS grid/line pattern and a short campus-data value statement) and a right form panel. Mobile: the brand panel collapses to a compact header above the form. No image asset; the pattern is CSS gradients only.

Alternative considered: keep the single centered card. Rejected because the login screen is the product's first impression and currently has no campus identity.

### 6. Shared page primitives instead of copy-pasted page styles

Introduce a small set of global classes in `style.css` for page header, page description, action bar, surface card, table wrapper, empty state, and focus ring. Each screen keeps its scoped layout rules but uses these primitives for surfaces and spacing.

Why: the data screens (`DataSourcesView`, `ModelSettingsView`, `DataSourceAccessView`) currently duplicate near-identical header/footer/muted styles; consolidating them prevents drift. Alternative: a full component library (`AcPage`, `AcCard`, ...). Rejected for this change because it expands scope and requires touching every screen's API; global classes deliver most of the consistency with less churn.

### 7. Responsive behavior

- `>= 1200px`: full shell, 260px sidebar, 24px gutters.
- `768–1199px`: sidebar 232px, tighter gutters, chat controls wrap when needed.
- `< 768px`: sidebar becomes an off-canvas panel toggled by the existing collapse control with a translucent backdrop; navigation remains reachable; chat and tables keep their own internal horizontal scroll. The page must not scroll horizontally.

Alternative considered: shrink the sidebar to an icon rail. Rejected because conversation titles carry the history and icons alone would lose meaning.

### 8. About removal

Delete `src/views/AboutView.vue`, remove the `/about` route and the menu item, and add a redirect from `/about` to `/` so old links do not land on a blank router outlet. No backend or docs dependency on the route.

Alternative considered: leave the route but hide the tab. Rejected because the user explicitly does not need the page and a hidden route is dead code.

## Risks / Trade-offs

- [Element Plus internal DOM changes across versions] → Pin the current Element Plus version; keep overrides at the CSS-variable level and limit `:deep` rules to a small, documented set; re-check on upgrade.
- [Chinese serif fallback differs between Windows and macOS] → Use the serif stack only for the brand and page titles; keep body text sans-serif; verify on the target Windows environment.
- [Teal on white can fail contrast for small text] → Use teal for dots, large numerals, chart series, and tinted backgrounds; use ink/secondary text for small labels.
- [Off-canvas sidebar regressions on chat] → Test the toggle, backdrop dismissal, route changes, and keyboard focus at 375px, 768px, and 1024px.
- [Large visual diff obscures functional regressions] → Land in three commits: tokens + shell, chat + login, data screens + About removal; run `npm test` and `npm run build` after each.
- [System fonts differ from the design mock] → Design for the stack, not a single font; avoid fixed-width text blocks that assume exact glyph widths.
- [No school logo yet] → The wordmark is CSS/text only; the token block allows swapping the primary hue and dropping in a logo later without structural changes.

## Migration Plan

1. Land the token layer and shell first; verify all routes still render with the new variables.
2. Restyle chat and login; verify streaming, copy fallback, and the empty state.
3. Restyle data screens; remove About and add the redirect; verify admin-only navigation.
4. Run `npm test` and `npm run build`; perform the manual viewport/contrast checklist.
5. Rollback is a single frontend revert; no data or backend migration is involved.

## Open Questions

- If the school provides an official brand color or logo, the primary token and brand mark can be swapped in one place. This does not change the approach, specs, or task breakdown.
