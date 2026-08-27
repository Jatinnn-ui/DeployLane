# DeployLane design system

## Product character

DeployLane is a premium developer-infrastructure product. The approved landing page is the visual source of truth: near-black canvas, graphite surfaces, electric lime used as a controlled accent, crisp white primary text, muted grey supporting text, thin technical borders, restrained depth, and purposeful motion.

The product must feel technical, modern, calm, and intentional. Avoid gaming/cyberpunk styling, uncontrolled neon, decorative glassmorphism, random gradients, and bright green on every surface.

## Protected approved UI

Do not redesign or geometrically alter the landing hero, 3D server, pipeline routing, feature illustrations, or other approved marketing compositions. Their bespoke SVG colors and geometry are illustrative assets, not general-purpose application tokens.

## Source of truth

Runtime tokens live in `frontend/src/index.css`. Shared primitives live in `frontend/src/components/ui`. Extend these systems rather than creating page-local themes.

## Color roles

- Canvas: `--dl-canvas`, with `--dl-canvas-secondary` for quiet section separation.
- Surfaces: `--dl-surface`, `--dl-surface-alt`, `--dl-surface-raised`, and `--dl-surface-hover`.
- Borders: subtle by default; strong for structural separation; accent only for focus, active, selected, and important technical states.
- Content: primary for headings/data, secondary for descriptions, muted for metadata and disabled context.
- Accent: `--dl-accent` / `#a8f000`. Reserve it for primary actions, active navigation, progress, focus, status, and small technical highlights.
- Status colors: use the semantic success, warning, danger, and info families. Never introduce page-local status shades.

## Typography

- Inter/system sans for interface and display text.
- UI mono stack for identifiers, URLs, logs, commands, hashes, and technical values.
- Marketing display headings may remain expressive and tightly tracked.
- Application pages use the compact roles `page-title`, `page-description`, `section-title`, `body-small`, `meta-label`, `caption`, and `technical-copy`.
- Avoid new arbitrary font sizes when a semantic role already fits.

## Spacing and geometry

- Use the existing 4px spacing rhythm and Tailwind spacing scale.
- Controls use the shared 36/44/48px height rhythm.
- Use `rounded-sm` for compact affordances, `rounded-control` for form controls, `rounded-md`/`rounded-lg` for nested surfaces, `rounded-card` for cards/dialogs, and pill geometry only for badges and established pill buttons.
- Major page content remains constrained by `workspace-view`; avoid page-local max-width systems unless the task demands a focused reading/form width.

## Components

- `Button`: primary, secondary, ghost, danger, outline/link, and icon variants. Lime fill is normally limited to one primary action per decision area.
- `Card`: standard application surface. Use `CardHeader`, `CardBody`, `CardFooter`, and `MetaItem` for consistent internal rhythm.
- `PageHeader`: the standard product-page heading and action layout.
- `MetricCard`: compact operational metric treatment.
- `Pagination`: standard previous/next behavior and page label.
- `Input`, `Textarea`, `Select`, `Switch`, and `Field`: mandatory form primitives with semantic focus and validation treatment.
- `Badge` and status indicators: semantic state communication, not decoration.
- Dialogs, dropdowns, tabs, tooltips, toasts, empty/error/loading states: use the shared primitives rather than one-off overlays.

## Interaction and motion

- Fast micro-interaction: about 140–180ms.
- Standard state transition: about 220–300ms.
- Ambient technical animation: slow, subtle, and subordinate to content.
- Use opacity, transform, border, shadow, and SVG stroke animation. Avoid layout animation, bouncing, elastic scaling, rapid flashing, and simultaneous glow everywhere.
- Respect `prefers-reduced-motion`; the global reduced-motion rule is mandatory.

## Accessibility and responsive behavior

- Preserve semantic HTML, labels, keyboard operation, visible focus, and sufficient contrast.
- Validation must include text or icons, not color alone.
- Mobile layouts reflow rather than shrink: headers stack, actions wrap, lists become compact grids/stacks, tables retain a usable overflow strategy, and tap targets stay at least 36px high.
- Decorative SVGs must remain behind content, non-interactive, and unable to cause overflow.
