# AGENTS and Docs Guardrail Restructure Implementation Plan

> **For agentic workers:** This is a docs-only implementation plan. Do not change Android source, Gradle settings, README product positioning, or existing `docs/superpowers/*` history while executing it. Confirm a clean worktree before starting.

**Design:** `docs/superpowers/specs/2026-04-23-agents-docs-guardrail-restructure-design.md`

**Goal:** Reorganize GasStation's agent-facing documentation around guardrails first: prevent architecture, state, cache, demo/prod, and testing mistakes while keeping onboarding fast and reducing duplicated guidance across docs.

**Architecture:** `AGENTS.md` becomes the short operating contract. `docs/module-contracts.md` owns module placement. `docs/agent-workflow.md` owns step-by-step change procedure. `docs/project-reading-guide.md` owns routing readers to the right file. Topic docs own their specialized source-of-truth areas.

**Tech Stack:** Markdown documentation, shell verification with `git diff --check` and `rg`.

---

## Task 0: Confirm Baseline And Worktree

**Files:**
- Read: `AGENTS.md`
- Read: `docs/project-reading-guide.md`
- Read: `docs/agent-workflow.md`
- Read: `docs/module-contracts.md`
- Read: `docs/architecture.md`
- Read: `docs/state-model.md`
- Read: `docs/offline-strategy.md`
- Read: `docs/test-strategy.md`
- Read: `docs/verification-matrix.md`

- [ ] Run `git status --short` and document whether there are user changes.
- [ ] Read the design spec linked above.
- [ ] Skim the target docs and confirm their current roles still match the spec.
- [ ] If docs changed since the spec was written, adjust this plan before editing.

**Exit criteria:**
- Worktree state is known.
- The docs to edit are confirmed.

---

## Task 1: Restructure `AGENTS.md` Around Guardrails

**Files:**
- Modify: `AGENTS.md`

- [ ] Replace the current section layout with the guardrail-first layout:
  - `Operating Contract`
  - `Product And UI Invariants`
  - `Architecture Guardrails`
  - `Change Guardrails`
  - `Required Reading By Task`
  - `Documentation Ownership`
- [ ] Keep the opening short: this file is the first-read operating contract, not the full workflow.
- [ ] Keep these invariants directly in `AGENTS.md`:
  - price is the first station-card read target
  - `demo` and `prod` are both official runtime paths
  - active modules are defined by `settings.gradle.kts`
  - `app` owns composition and handoff only
  - `feature:*` must not call Room, Retrofit, DataStore, or `core:location` implementation directly
  - `domain:*` must not expose Android, Compose, Room, Retrofit, or DataStore types
  - `data:*` must not create UI state or Compose types
  - location lookup boundary remains `feature:station-list -> domain:location -> core:location`
  - settings writes go through explicit `domain:settings` use cases
  - cache presence follows `StationSearchResult.hasCachedSnapshot`
  - user-flow changes require tests plus README/demo story review
- [ ] Compress module-detail prose into links to `docs/module-contracts.md`.
- [ ] Compress long workflow prose into links to `docs/agent-workflow.md`.
- [ ] Keep the documentation ownership rule that prevents `AGENTS.md` from growing into a handbook.

**Exit criteria:**
- `AGENTS.md` is still short enough to read quickly.
- A new worker can see the non-negotiable boundaries without following links.
- Detailed procedure and module tables are not duplicated in this file.

---

## Task 2: Make `docs/module-contracts.md` The Placement Source Of Truth

**Files:**
- Modify: `docs/module-contracts.md`

- [ ] Update the opening paragraph to say this is the source of truth for module placement and forbidden ownership.
- [ ] Keep the module inventory table as the main reference.
- [ ] Preserve the common rules and "경계가 헷갈릴 때" section.
- [ ] Add a short note that `AGENTS.md` and `docs/agent-workflow.md` intentionally link here instead of repeating the full module table.
- [ ] Do not expand this into a workflow document.

**Exit criteria:**
- A module-placement question clearly resolves here.
- The file still stays compact and table-driven.

---

## Task 3: Trim `docs/agent-workflow.md` To Procedure

**Files:**
- Modify: `docs/agent-workflow.md`

- [ ] Update the opening paragraph to say this is the source of truth for change procedure.
- [ ] Keep:
  - working model
  - before-any-change checklist
  - adding-feature flow
  - modifying-existing-behavior guidance
  - UI/design workflow
  - settings, location, station search/cache, watchlist, demo/prod sections
  - testing selection
  - documentation updates
  - final review checklist
- [ ] Replace or shorten the detailed `Module Placement` section so it points to `docs/module-contracts.md` for full ownership tables.
- [ ] Keep only enough module-placement summary to support the procedure.
- [ ] Ensure the final checklist still asks whether `AGENTS.md` content is truly global.

**Exit criteria:**
- `docs/agent-workflow.md` reads as a sequence of work decisions, not a second module contract document.
- No essential change procedure is lost.

---

## Task 4: Refocus `docs/project-reading-guide.md` As A Router

**Files:**
- Modify: `docs/project-reading-guide.md`

- [ ] Update the opening paragraph to say this file routes readers to the fastest starting point.
- [ ] Keep:
  - first-read sequence
  - question-by-question entry table
  - recommended code reading order
  - change-purpose file list
  - "길을 잃었을 때" anchors
- [ ] Remove or compress any prose that repeats `AGENTS.md` guardrails or `docs/module-contracts.md` ownership details.
- [ ] Ensure the file points to `AGENTS.md` for operating contract and `docs/agent-workflow.md` for change procedure.

**Exit criteria:**
- A new worker can use the guide as a router without reading duplicated policy.

---

## Task 5: Add Source-Of-Truth Headers To Topic Docs

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/offline-strategy.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

- [ ] Add or adjust the first paragraph of `docs/architecture.md` so it owns current module graph and runtime flow explanation.
- [ ] Add or adjust the first paragraph of `docs/state-model.md` so it owns state source and lifecycle decisions.
- [ ] Add or adjust the first paragraph of `docs/offline-strategy.md` so it owns cache, stale, refresh failure, and watchlist fallback meaning.
- [ ] Add or adjust the first paragraph of `docs/test-strategy.md` so it owns test philosophy and layer-level confidence.
- [ ] Add or adjust the first paragraph of `docs/verification-matrix.md` so it owns concrete verification commands.
- [ ] Keep these edits small. Do not rewrite the topic docs unless a sentence directly contradicts the new ownership model.

**Exit criteria:**
- Each topic doc states what decision area it owns.
- No topic doc gets pulled into the AGENTS/workflow/module-contract responsibility split.

---

## Task 6: Check Cross-Document Duplication And Links

**Files:**
- Verify: `AGENTS.md`
- Verify: `docs/*.md`

- [ ] Run targeted duplicate scans:

```bash
rg -n "Price is the hero|hasCachedSnapshot|settings.gradle.kts|feature:station-list -> domain:location -> core:location|DataStore|Room|Retrofit" AGENTS.md docs/*.md
```

- [ ] Confirm repeated phrases are intentional:
  - exact guardrails may appear in `AGENTS.md`
  - detailed explanations should live in topic docs
  - module ownership details should live in `docs/module-contracts.md`
  - procedural steps should live in `docs/agent-workflow.md`
- [ ] Check all changed markdown links point to existing files.
- [ ] Check no changed doc points to an obsolete source of truth.

**Exit criteria:**
- Repetition is either removed or has a clear reason.
- The source-of-truth ownership model is internally consistent.

---

## Task 7: Static Verification

**Files:**
- Verify all changed docs.

- [ ] Run:

```bash
git diff --check
```

- [ ] Run:

```bash
git status --short
```

- [ ] Review the final diff manually.
- [ ] Confirm no Android source, Gradle, README, spec, or old plan files were changed outside the intended scope.
- [ ] Do not run Gradle by default. This is docs-only and does not change build behavior.

**Exit criteria:**
- Static markdown diff is clean.
- Changed files match the plan.

---

## Task 8: Commit The Docs Restructure

**Files:**
- Stage only the docs changed by this plan.

- [ ] Stage the intended files.
- [ ] Commit with:

```bash
git commit -m "docs: clarify agent documentation guardrails"
```

**Exit criteria:**
- The implementation commit contains only the intended documentation restructure.
