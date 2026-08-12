# Documentation Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every current GasStation contract reachable, singly owned, and mechanically checked while preserving stable paths and historical evidence.

**Architecture:** `docs/README.md` becomes the human hub, `docs/documentation-catalog.json` becomes the live-document registry, and standard-library validators enforce catalog, navigation, links, code/task references, and deterministic historical indexes. Oversized live documents are split behind their existing stable entry paths; historical text is classified and indexed, not rewritten.

**Tech Stack:** Markdown, JSON, Python 3 standard library, Bash agent verification scripts, GitHub Actions

## Global Constraints

- Preserve existing live entry paths and historical file contents unless a navigation-only fix is explicitly required.
- Root README owns product overview and quick start; `docs/architecture.md` alone owns the exact active module graph.
- `docs/verification-matrix.md` owns change-to-command selection; specialist runbooks own execution details.
- Korean prose is the default for live documents; identifiers and official technology names keep source spelling.
- Every live fact has one owner; other documents link instead of copying long commands or exact graphs.
- Live documents must be reachable from `docs/README.md` within two links and have zero broken internal links.
- External network links are scheduled/non-blocking; historical placeholder links are reported separately.
- Do not infer approval or completion state from a historical filename.

---

### Task 1: Create the documentation catalog and human hub

**Files:**
- Create: `docs/README.md`
- Create: `docs/documentation-catalog.json`
- Modify: `docs/AGENTS.md`
- Modify: `docs/project-reading-guide.md`

**Interfaces:**
- Catalog fields: `path`, `kind`, `owner`, `authoritativeSources`, `reviewTriggers`, `verificationScope`
- Kinds: `product`, `contract`, `runbook`, `decision`, `evidence`, `history`

- [ ] **Step 1: Resolve the current live-set inconsistency**

Reconcile `docs/AGENTS.md` with `scripts/agent/check_contracts.py`: explicitly classify `docs/build-velocity.md` and the current ADR. Keep `docs/superpowers`, `history`, `improvements`, `compose-metrics`, and past release notes historical/evidence.

- [ ] **Step 2: Write the complete catalog**

Register root `AGENTS.md`, `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `.impeccable.md`, `docs/AGENTS.md`, workflow/reading/architecture/module/state/offline/test/verification/security/deployment/performance/build-velocity documents, and the current ADR exactly once. `authoritativeSources` must point to real repository files such as `settings.gradle.kts`, source packages, Gradle build files, or workflows.

- [ ] **Step 3: Add the four-lane documentation hub**

`docs/README.md` provides paths for new contributors, architecture/feature changes, testing/release operations, and decisions/evidence/history. Link every live document directly or through one specialist hub.

- [ ] **Step 4: Narrow the reading guide**

Remove the duplicated global document catalog from `project-reading-guide.md`. Retain question-to-code, feature-to-owner, and change-to-contract reading routes.

- [ ] **Step 5: Verify links manually and commit**

Run: `scripts/agent/verify.sh docs`

```bash
git add docs/README.md docs/documentation-catalog.json docs/AGENTS.md docs/project-reading-guide.md
git commit -m "docs: add live documentation catalog"
```

### Task 2: Build a deterministic documentation validator

**Files:**
- Create: `scripts/docs/validate.py`
- Create: `scripts/docs/tests/test_validate.py`
- Create: test fixtures under `scripts/docs/tests/fixtures/`
- Modify: `scripts/agent/check_contracts.py`
- Modify: `scripts/agent/test.sh`
- Modify: `scripts/agent/verify.sh`

**Interfaces:**
- Default: fast, offline validation
- Optional: `--check-gradle-tasks` resolves canonical Gradle task references once
- Exit code 0 only when all live catalog invariants pass

- [ ] **Step 1: Add RED fixture tests**

Cover duplicate/missing catalog entries, absent fields, broken relative links, valid anchors, fenced-code false positives, unreachable live docs, nonexistent module/path/CI job, personal home path, likely secret assignment, duplicate command-owner ID, and valid two-link navigation.

- [ ] **Step 2: Implement catalog and link validation**

Use `json`, `pathlib`, `re`, and `urllib.parse`. Strip fenced and inline code before scanning Markdown links. Resolve relative links from the containing file, decode anchors, and reject missing files or headings only for live documents.

- [ ] **Step 3: Implement repository-reference validation**

Parse active module names from `settings.gradle.kts`, CI job keys from `.github/workflows/android.yml`, and repository paths relative to root. Reject `/Users/`, `/home/`, and drive-letter home paths in live prose. Secret detection targets assignments containing names such as `API_KEY`, `TOKEN`, or `SECRET` with non-example values; it must not reject variable names alone.

- [ ] **Step 4: Implement navigation and command ownership**

Build a graph of live Markdown links and breadth-first search from `docs/README.md`; maximum depth is two. Recognize `<!-- command-owner: id -->` markers and fail duplicate IDs. Do not attempt semantic natural-language duplicate detection.

- [ ] **Step 5: Add the optional Gradle task check**

Collect task tokens from catalog-designated canonical commands, run one `./gradlew tasks --all` subprocess, and compare task names. Keep this out of the default fast path; CI and `verify.sh docs` invoke it when Gradle is available.

- [ ] **Step 6: Integrate and commit**

Run: `python3 -m unittest discover -s scripts/docs/tests`

Run: `python3 scripts/docs/validate.py --check-gradle-tasks`

Run: `scripts/agent/test.sh`

```bash
git add scripts/docs scripts/agent
git commit -m "test: validate live documentation contracts"
```

### Task 3: Split onboarding behind its stable entry path

**Files:**
- Modify: `docs/onboarding/developer-onboarding-guide.md`
- Create: `docs/onboarding/getting-started.md`
- Create: `docs/onboarding/architecture-tour.md`
- Create: `docs/onboarding/change-playbook.md`
- Create: `docs/onboarding/verification-and-delivery.md`
- Modify: catalog and hub

- [ ] **Step 1: Create four focused documents**

Move local setup and first run to `getting-started`; architectural reasoning and module tour to `architecture-tour`; first bug/feature and ownership workflow to `change-playbook`; testing, evidence, merge, and handoff to `verification-and-delivery`.

- [ ] **Step 2: Convert the 685-line entry into a router**

Keep its existing path and title. Add reader profiles, prerequisites, the four links, expected completion outcomes, and escalation links. Remove content only after it exists in exactly one destination.

- [ ] **Step 3: Check command ownership**

Replace copied long Gradle commands with links to `verification-matrix.md` or a runbook. Short first-run commands may remain when onboarding owns them.

- [ ] **Step 4: Validate and commit**

Run: `python3 scripts/docs/validate.py`

```bash
git add docs/onboarding docs/README.md docs/documentation-catalog.json
git commit -m "docs: split developer onboarding paths"
```

### Task 4: Refocus verification and create device runbooks

**Files:**
- Modify: `docs/verification-matrix.md`
- Create: `docs/runbooks/README.md`
- Modify: `docs/runbooks/device-verification.md`
- Modify: `docs/deployment.md`
- Modify: `docs/performance.md`
- Modify: catalog and hub

- [ ] **Step 1: Keep only selector semantics in the matrix**

Retain change type, risk, local/CI scope, command owner link, expected evidence, and limitation. Remove procedural device, release, and benchmark detail.

- [ ] **Step 2: Move procedures to their owners**

Device/API/permission/Geocoder/artifact procedures go to `device-verification.md`; release/tag steps to `deployment.md`; physical benchmark and baseline profile procedures to `performance.md`.

- [ ] **Step 3: Mark canonical commands**

Assign stable command-owner IDs such as `verification.fast`, `verification.data`, `verification.device`, `release.assemble`, and `performance.hero`. Other documents link to these sections.

- [ ] **Step 4: Validate and commit**

Run: `python3 scripts/docs/validate.py --check-gradle-tasks`

```bash
git add docs/verification-matrix.md docs/runbooks docs/deployment.md docs/performance.md docs/README.md docs/documentation-catalog.json
git commit -m "docs: separate verification selectors and runbooks"
```

### Task 5: Remove exact graph and command duplication from the root README

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: catalog/hub references

- [ ] **Step 1: Replace the exact module graph**

Keep a stable layer-level product diagram in README and link to `docs/architecture.md` for the exact 18-module graph. Do not duplicate module-by-module edges.

- [ ] **Step 2: Replace specialist command copies**

Keep quick start only. Link verification, deployment, performance, and device details to their canonical owners.

- [ ] **Step 3: Verify product invariants remain visible**

README must still state first-class demo/prod paths, price-first identity, quick start, screenshots, and the document hub.

- [ ] **Step 4: Validate and commit**

Run: `scripts/agent/verify.sh docs`

```bash
git add README.md docs/architecture.md docs/README.md docs/documentation-catalog.json
git commit -m "docs: make README a stable product landing"
```

### Task 6: Add historical hubs and deterministic indexes

**Files:**
- Create: `scripts/docs/generate_indexes.py`
- Create: generator tests
- Create: `docs/adr/README.md`
- Create: `docs/superpowers/README.md`
- Create: `docs/superpowers/INDEX.md`
- Create: `docs/history/README.md`
- Create: `docs/improvements/README.md`
- Create: `docs/compose-metrics/README.md`
- Create: `docs/release-notes/README.md`

- [ ] **Step 1: Add generator RED tests**

Assert deterministic lexical/date ordering, exclusion of README/INDEX from generated entries, path-only metadata, `--write` idempotence, and `--check` failure on drift. Assert no inferred `approved`, `complete`, or `current` status.

- [ ] **Step 2: Implement the generator**

Use filenames and relative paths only. `docs/superpowers/INDEX.md` groups specs and plans by filename date; undated entries remain in an explicit undated section.

- [ ] **Step 3: Add directory classification hubs**

Each README explains whether content is decision, dated evidence, release record, or historical plan and links back to the live hub. Do not edit the bodies of the existing historical files.

- [ ] **Step 4: Generate, check, and commit**

Run: `python3 scripts/docs/generate_indexes.py --write`

Run: `python3 scripts/docs/generate_indexes.py --check`

```bash
git add scripts/docs docs/adr docs/superpowers docs/history docs/improvements docs/compose-metrics docs/release-notes
git commit -m "docs: index historical evidence safely"
```

### Task 7: Add style guidance and reusable templates

**Files:**
- Create: `docs/documentation-style.md`
- Create: `docs/templates/README.md`
- Create: `docs/templates/live-contract.md`
- Create: `docs/templates/adr.md`
- Create: `docs/templates/runbook.md`
- Create: `docs/templates/evidence-report.md`
- Modify: hub/catalog

- [ ] **Step 1: Define enforceable style rules**

Document answer-first structure, language convention, single fact owner, command prerequisites/expected result/failure action/depth, evidence date/commit/environment/source, and historical classification.

- [ ] **Step 2: Normalize intentional examples**

Replace ambiguous angle-bracket placeholders in live commands with declared variables such as `$ANDROID_SERIAL`, `$TAG`, and `$OUTPUT_DIR`. Keep explanatory examples executable or explicitly labeled non-executable.

- [ ] **Step 3: Add complete templates**

Every template includes required metadata, owner/source/review triggers, validation command, and limitations. The ADR template includes status/supersedes; runbook includes rollback; evidence includes commit/environment.

- [ ] **Step 4: Validate and commit**

Run: `python3 scripts/docs/validate.py`

```bash
git add docs/documentation-style.md docs/templates docs/README.md docs/documentation-catalog.json
git commit -m "docs: standardize contract and evidence writing"
```

### Task 8: Integrate document impact review and close Phase 5

**Files:**
- Modify: `.github/PULL_REQUEST_TEMPLATE.md`
- Modify: `.github/workflows/android.yml`
- Modify: `scripts/agent/verify.sh`
- Modify: relevant live workflow documentation

- [ ] **Step 1: Add PR document-impact fields**

Require yes/no, affected catalog owner, updated paths, and an explicit reason when no live document changes. This is review metadata, not an automatic claim that documentation is current.

- [ ] **Step 2: Run the same gate locally and in CI**

The `agent-contracts` job runs validator unit tests, index check, fast validator, and CI Gradle-task validation. `verify.sh docs` invokes the same commands in the same order.

- [ ] **Step 3: Run final documentation verification**

Run: `python3 -m unittest discover -s scripts/docs/tests`

Run: `python3 scripts/docs/generate_indexes.py --check`

Run: `python3 scripts/docs/validate.py --check-gradle-tasks`

Run: `scripts/agent/test.sh`

Run: `scripts/agent/verify.sh docs`

Run: `git diff --check`

- [ ] **Step 4: Commit and run the full repository gate**

```bash
git add .github scripts/agent docs README.md
git commit -m "ci: enforce documentation architecture"
```

Run: `scripts/agent/verify.sh auto`

Expected: every cataloged current document is reachable within two links, live internal link failures are zero, generated indexes are current, and historical text remains unchanged except new navigation hubs.
