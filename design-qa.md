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

## Site-wide AI Usage Activity Extension

### Scope

- Route: `http://127.0.0.1:4173/app/me?tab=subscription`
- Replaces the single quota progress bar with an AI Token activity surface.
- Keeps current entitlement, redemption code, and paid-plan cards intact.

### Automated Design Checks

- The daily model always emits 53 × 7 cells and keeps out-of-range padding inert.
- Non-zero days use quantile levels, so one extreme day cannot flatten all other
  activity to the empty color.
- Weekly and monthly totals are derived from the same daily buckets and preserve
  the daily total.
- The activity surface uses native CSS grids and bars; no chart dependency was
  added to the personal-center entry.
- Calendar cells and bars are keyboard focusable and expose numeric aria labels.
- Narrow layouts scroll inside the chart instead of widening the personal page.

### Manual Browser Status

- Verified against the branch backend with the signed-in personal-center page.
- The page loaded 18,298 recorded Tokens and grouped them into assistant,
  writing, and vocabulary products without falling back to the error state.
- Daily mode rendered 371 cells (365 in-range days plus calendar padding);
  weekly mode rendered 53 intersecting calendar-week buckets; cumulative mode
  rendered 13 intersecting natural-month buckets.
- Weekly and monthly bucket totals both summed to the same 18,298 Tokens shown
  by the daily range, including partial boundary periods.
- Selecting an active day exposed the exact date, total, and product
  composition through the accessible detail region.
- At the default 1043px viewport, the document had no horizontal overflow.
- At a 390px viewport, the document remained 390px wide while the calendar kept
  its 844px content inside the component's own horizontal scroller.
- The temporary narrow viewport was reset after verification.

## Weekly Square Columns

### Comparison Evidence

- Weekly reference:
  `C:\Users\Catalina\AppData\Local\Temp\codex-clipboard-963acb70-1428-4e8f-b7c4-1797969eb686.png`
- Final desktop state:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\weekly-square-desktop-final.png`
- Reference and implementation comparison:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\weekly-square-comparison.png`
- Responsive chart state:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\weekly-square-390-chart.png`

### Verification

- The weekly selected state uses 53 columns with 7 square cells per column,
  matching the reference's bottom-up weekly activity structure.
- Zero weeks remain neutral; every non-zero week fills at least one cell, and
  the peak week fills all 7 cells.
- The signed-in page rendered 371 weekly cells: 50 empty columns and 3 active
  columns for the current 18,298 Token dataset.
- Hover, click, and keyboard focus continue to expose the selected week range,
  total, and product composition. The checked `5/18–5/24` period displayed
  `655 Token` from the assistant product.
- Crowded partial-month labels are suppressed, leaving the same 8月–7月 rhythm
  shown by the reference.
- Daily mode still renders 371 calendar cells with 365 in-range days.
- Cumulative mode still renders 13 intersecting natural-month bars.
- At 390px, the weekly matrix keeps its 794px width inside its own horizontal
  scroller while the document remains exactly 390px wide.
- Browser console checks returned no warnings or errors.

final result: passed

## Subscription Header And Dynamic Usage Summary

### Comparison Evidence

- Previous signed-in state:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\subscription-header-before.png`
- Final desktop state:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\subscription-header-desktop.png`
- Previous and final state comparison:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\subscription-header-comparison.png`
- Responsive state:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\subscription-header-390.png`
- Responsive badge detail:
  `F:\personalenglishai\.worktrees\personal-center-1237\design-qa-artifacts\subscription-badge-390.png`

### Viewport And Visual Findings

- Desktop source and implementation were captured at the same 1043 × 1272
  viewport. The removed entitlement card made the final page shorter, so the
  final state naturally returns to the top of the page and exposes the profile
  header.
- The large `Free / 0 / 10K` entitlement card is gone. A restrained `Free`
  pill now sits beside the nickname without competing with the profile
  hierarchy.
- Clicking, hovering, or keyboard-focusing the pill reveals the plan and its
  effective period. The verified Free account displayed
  `2026-05-15 — 长期有效`.
- The activity header now contains only `AI Token 活动` and the selected
  period's numeric summary. The previous eyebrow, site-wide qualifier, and
  explanatory paragraph were removed.
- At 390 × 844, the nickname safely truncates, the plan badge remains visible,
  and its detail popover stays inside the viewport. The existing tab and chart
  scrollers continue to contain their own horizontal overflow.

### Behavior And Accessibility Verification

- Daily selected state displayed `0 / 今日 Token`, matching the signed-in
  account's 2026-07-27 bucket.
- Weekly selected state displayed `0 / 本周 Token`, covering the current
  natural week from Monday 2026-07-27 through today.
- Cumulative selected state displayed `18,298 / 累计 Token`, matching the
  complete 365-day activity response.
- The plan badge exposes one descriptive accessible name and a tooltip role;
  Escape dismisses its focus-triggered detail.
- A fresh signed-in browser tab loaded the final page with no console warnings
  or errors.

No actionable P0, P1, or P2 findings remain.

final result: passed
