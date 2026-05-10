# VitePress Documentation Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a VitePress documentation site and reorganize project documentation into active, discoverable sections with historical material archived.

**Architecture:** Keep documentation tooling isolated under `docs/` so it does not affect the existing `web/`, `backend/`, or `python/` build chains. VitePress will render the curated active documentation tree, while archived intermediate artifacts remain in the repository but outside the primary navigation.

**Tech Stack:** VitePress, Markdown, TypeScript config, Git-managed docs-as-code.

---

### Task 1: Add VitePress Site Skeleton

**Files:**
- Create: `docs/package.json`
- Create: `docs/.vitepress/config.ts`
- Modify: `.gitignore`

- [ ] Add docs-local npm scripts for `dev`, `build`, and `preview`.
- [ ] Configure VitePress title, search, nav, and sidebar for the curated documentation sections.
- [ ] Ignore VitePress cache output.

### Task 2: Create Governance Entry Points

**Files:**
- Create: `docs/index.md`
- Create: `docs/contributing.md`
- Create: `docs/adr/README.md`
- Create: `docs/adr/template.md`
- Create: `docs/adr/0001-use-vitepress-for-docs.md`
- Create: section landing pages under `docs/product/`, `docs/architecture/`, `docs/api/`, `docs/data/`, `docs/ai/`, `docs/runbooks/`, and `docs/testing/`

- [ ] Add a documentation home page that points readers to the correct section.
- [ ] Add document status and ownership rules.
- [ ] Add ADR guidance and the first decision record.

### Task 3: Reorganize Existing Documentation

**Files:**
- Move active docs into `docs/product/`, `docs/architecture/`, `docs/api/`, `docs/data/`, `docs/ai/`, `docs/runbooks/`, and `docs/testing/`
- Move intermediate and historical docs into `docs/archive/`

- [ ] Preserve current source content during the first pass.
- [ ] Move obvious process artifacts, old state reports, mockups, and extraction data to archive.
- [ ] Keep active documents reachable from VitePress navigation.

### Task 4: Update Project Entry Points

**Files:**
- Modify: `README.md`

- [ ] Add a short documentation-site section with local commands.
- [ ] Keep the existing project README focused on project onboarding.

### Task 5: Verify

**Commands:**
- Run: `npm install` from `docs/`
- Run: `npm run build` from `docs/`
- Run: `git status --short`

- [ ] Confirm VitePress builds.
- [ ] Confirm no business source files changed.
- [ ] Confirm archive moves are visible in git.
