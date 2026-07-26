# Personal Center Design QA

## Comparison Target

- Source visual truth:
  `C:\Users\Catalina\.codex\generated_images\019f9d24-194a-72f3-9198-696da823116f\call_wpSOWhL5vBzM40aprPH6xVUN.png`
- Rendered implementation:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\implementation-desktop-pass2.png`
- Focused comparison:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\panel-comparison-pass2.png`
- Responsive evidence:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\implementation-mobile-pass5.png`
- Route and state:
  `http://127.0.0.1:4173/dev/personal-center-preview?tab=overview`,
  development-only representative learning data, light theme.

## Viewport and Normalization

- Source pixels: 1910 × 823, component-only design, density 1.
- Desktop CSS viewport: 1600 × 1100; browser device pixel ratio 1.5.
- Desktop screenshot pixels: 1600 × 1100.
- Focused implementation crop: 1238 × 470 CSS-aligned pixels.
- Focused comparison normalization: source and implementation both scaled to
  1200px width, then vertically stacked in a 1200 × 973 comparison image.
- Mobile CSS viewport and screenshot: 390 × 844, device pixel ratio 1.

## Full-view Comparison Evidence

The desktop implementation preserves the product's real PEAI global rail,
uses a single personal-center content hierarchy, and places horizontal personal
tabs above the selected timeline. The timeline remains the dominant
above-the-fold component, followed by real-stat summary and cross-product asset
entry points. The mobile pass keeps the global rail collapsed to 72px, keeps the
tabs horizontally scrollable, and converts the timeline into a readable
vertical sequence.

## Focused Region Comparison Evidence

The focused comparison checks the selected timeline at readable scale. Both
states use the same three-node chronology, thin blue/green line, circular
Lucide icons, centered green current node, primary continue action, secondary
writing/translation actions, and five-step weekly progress. Dynamic product
data intentionally changes the previous activity text and removes an
unsupported fake vocabulary count.

## Findings

No actionable P0, P1, or P2 findings remain.

- [P3] Dynamic copy does not match the mock word-for-word.
  Location: previous and next timeline nodes.
  Evidence: the mock uses a bilingual-reading title and a fake “12 个” count;
  the implementation uses a real writing-history input and a count-free
  vocabulary review prompt.
  Impact: minor visual wrapping difference, but avoids shipping fabricated
  user data.
  Disposition: accepted product constraint.

## Required Fidelity Surfaces

- Fonts and typography: system Chinese font stack, weights, hierarchy, line
  height, truncation, and tab labels are visually consistent with the source.
  Long real titles clamp to two lines instead of widening the panel.
- Spacing and layout rhythm: 24px primary radius, thin border, soft elevation,
  three-column desktop layout, and vertical mobile layout match the selected
  design intent. The primary CTA was narrowed from 278px to 250px after the
  first comparison.
- Colors and visual tokens: brand green, deep navy, pale blue border, white
  surface, and low-opacity glow align with the source. Contrast remains legible.
- Image quality and asset fidelity: the target contains no photographic or
  raster assets. All visible icons use `lucide-vue-next`; no emoji, handwritten
  SVG, placeholder illustration, or CSS-drawn icon replaces a source asset.
- Copy and content: action language is product-specific and all non-preview
  values come from existing APIs or explicit empty states. The development-only
  preview fixture is not reachable in production.

## Interaction and Accessibility Checks

- Switched from “学习概览” to “账号安全” and back; query state and active
  section updated correctly.
- Opened and closed the learning-stage menu.
- Verified real links for learning assistant, writing, translation, vocabulary,
  and account security in the browser DOM.
- Confirmed keyboard focus styles are defined for tabs, buttons, and links.
- Checked browser console after interactions: no warnings or errors.
- Verified final mobile widths: personal page 318px with 303px scroll content;
  timeline 262.67px with 261px scroll content; activity panel 262.67px with
  261px scroll content; no horizontal overflow.

## Comparison History

### Pass 1 — blocked

- [P1] At 390px the global rail stayed 218px wide, leaving only 172px for the
  personal center and clipping the core timeline.
- [P2] The desktop primary CTA was visibly wider than the selected source.

Fixes:

- Added a tested personal-center rail policy: narrow screens default to the
  72px collapsed rail and allow temporary expansion.
- Added grid containment and responsive width rules to the timeline.
- Reduced the desktop CTA maximum width from 278px to 250px.

Post-fix evidence:

- `design-qa-artifacts\implementation-mobile-pass4.png`
- `design-qa-artifacts\panel-comparison-pass2.png`

### Pass 2 — blocked

- [P2] The timeline itself fit after the first fix, but recent-activity content
  still established a 531px min-content width and caused an inner horizontal
  scrollbar.

Fix:

- Constrained the activity grid to `minmax(0, 1fr)`, gave activity rows an
  explicit 100% width and `min-width: 0`, and constrained the activity main
  column.

Post-fix evidence:

- `design-qa-artifacts\implementation-mobile-pass5.png`
- Browser measurements show every final content scroll width within its
  container width.

## Implementation Checklist

- [x] Global navigation retained; duplicate personal sidebar removed.
- [x] Six horizontal personal-center tabs work.
- [x] Selected learning timeline implemented with real routes and safe data.
- [x] Desktop and mobile layouts verified.
- [x] Actionable empty states added.
- [x] Console checked.
- [x] P0/P1/P2 findings resolved.

## Follow-up Polish

- When a unified learning-event API exists, replace the writing-only previous
  node with the latest event across assistant, writing, translation, reading,
  listening, and speaking.

final result: passed
