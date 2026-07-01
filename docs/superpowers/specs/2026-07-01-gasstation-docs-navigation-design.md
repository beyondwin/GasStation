# GasStation Documentation Navigation Design

## Summary

Improve the GasStation documentation set so both human developers and coding agents can understand the project quickly, choose the right source-of-truth document, and avoid mistaking historical design records for current contracts.

This is a documentation navigation and ownership improvement. It does not change app behavior, architecture, Gradle modules, tests, or runtime flows.

## Problem

GasStation already has strong live documentation:

- `AGENTS.md`
- `README.md`
- `docs/project-reading-guide.md`
- `docs/agent-workflow.md`
- `docs/module-contracts.md`
- `docs/architecture.md`
- `docs/state-model.md`
- `docs/offline-strategy.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`
- `docs/onboarding/developer-onboarding-guide.md`

The issue is not missing documentation. The issue is navigation cost and role ambiguity.

Current symptoms:

- `README.md`, `docs/project-reading-guide.md`, `docs/architecture.md`, and `docs/onboarding/developer-onboarding-guide.md` all explain parts of the big picture.
- A new developer can learn a lot, but may not know which document is current authority when descriptions overlap.
- A coding agent may read too broadly, miss the minimal live-doc set for the task, or treat `docs/superpowers/` plans as current contracts.
- Historical documents under `docs/superpowers/`, `docs/history/`, and `docs/improvements/` are valuable evidence, but they need clearer separation from live source-of-truth docs.

## Goals

- Make `docs/project-reading-guide.md` the shared router for humans and agents.
- Keep `AGENTS.md` short and focused on always-on operating rules.
- Keep `README.md` useful as the first public/project overview without turning it into a full maintainer handbook.
- Make `docs/onboarding/developer-onboarding-guide.md` clearly a learning handbook, not a replacement for live contracts.
- Make `docs/agent-workflow.md` more explicit about live-doc priority and documentation update decisions.
- Make `docs/verification-matrix.md` clearer about document-only verification.
- Reduce the chance that historical specs, plans, or analysis documents are used as current architectural truth.

## Non-Goals

- Do not rewrite every documentation page.
- Do not move, delete, or archive the existing `docs/superpowers/` history in this change.
- Do not add frontmatter to every document.
- Do not add a new documentation validation script in this pass.
- Do not change app code, Gradle wiring, module boundaries, tests, or runtime behavior.
- Do not expand `AGENTS.md` with long procedural material.

## Audience

This design balances two readers.

### Human Developers

The human developer should be able to answer:

- What does this product do?
- What should I read first?
- Which document is authoritative for architecture, state, offline behavior, module ownership, and verification?
- Where should I change code for a common task?
- How do I make a first safe change without reading every historical plan?

### Coding Agents

The coding agent should be able to answer:

- What are the required pre-change rules?
- Which live docs must be read for this task type?
- Which documents are historical context only?
- Which tests or verification commands are appropriate for the change?
- Which document must be updated when behavior, module ownership, state, offline policy, or verification changes?

## Documentation Roles

The improved documentation set should make these roles explicit.

| Document or directory | Role |
| --- | --- |
| `AGENTS.md` | Short operating contract for every worker. It owns always-on rules and links out to longer docs. |
| `README.md` | Project landing page: product purpose, preview, quick structure, run modes, documentation map, performance snapshot, and verification summary. |
| `docs/project-reading-guide.md` | Shared router for humans and agents. It answers what to read first for each purpose. |
| `docs/onboarding/developer-onboarding-guide.md` | Narrative learning handbook for first-time developers. It teaches the project but does not override live contracts. |
| `docs/agent-workflow.md` | Practical change workflow: pre-change checks, owner lookup, task-specific flow, documentation updates, final checklist. |
| `docs/module-contracts.md` | Single source of truth for module placement, ownership, and forbidden dependencies. |
| `docs/architecture.md` | Single source of truth for current module graph and runtime/data flow. |
| `docs/state-model.md` | Single source of truth for state ownership and lifecycle. |
| `docs/offline-strategy.md` | Single source of truth for cache, stale, refresh failure, and watchlist fallback semantics. |
| `docs/test-strategy.md` | Single source of truth for what each test layer protects. |
| `docs/verification-matrix.md` | Single source of truth for actual verification commands and when to run them. |
| `docs/security-trade-offs.md` and `docs/adr/` | Security and accepted architectural decision records. |
| `docs/deployment.md`, `docs/performance.md`, `docs/release-notes/` | Release, deployment, and performance evidence. |
| `docs/superpowers/`, `docs/history/`, `docs/improvements/` | Historical design, plan, analysis, and evidence. Useful for rationale, not current authority by default. |

## Proposed Changes

### 1. Strengthen `docs/project-reading-guide.md`

Make this document the first navigation layer after `AGENTS.md`.

Add or refine sections:

- Current contract docs.
- Learning docs.
- Historical and evidence docs.
- Agent fast path.
- New developer fast path.
- Task-specific reading paths.

The agent fast path should be concise:

1. Read `AGENTS.md`.
2. Confirm active modules from `settings.gradle.kts`.
3. Pick the task path from `docs/project-reading-guide.md`.
4. Read only the required live docs for that task first.
5. Read related tests before changing behavior.
6. Treat `docs/superpowers/`, `docs/history/`, and `docs/improvements/` as historical context unless the task explicitly asks for history.

The new developer fast path should be readable:

1. Read `README.md`.
2. Read the opening sections of `docs/onboarding/developer-onboarding-guide.md`.
3. Run or inspect the `demo` path.
4. Follow the station-list code tour.
5. Use live contract docs when making a change.

For task-specific entries, split long file lists into:

- First read.
- Then inspect if needed.

This keeps the router useful without making every task feel like a full repository audit.

### 2. Lightly Refine `README.md`

Keep `README.md` as the project-facing overview. Do not turn it into the main maintainer handbook.

Refine the documentation map into groups:

- Start here and learning.
- Current contracts.
- Operations, release, and performance.
- History, plans, and evidence.

Make the current-authority rule explicit:

- Current structure and commands are defined by live docs plus actual code.
- Historical specs and plans explain why decisions happened, but should not override live docs.

Keep the existing preview, user flow, module graph, performance snapshot, and verification summary.

### 3. Clarify `docs/onboarding/developer-onboarding-guide.md`

Keep this as a long-form human learning document.

Improve the opening:

- State that the guide is meant to be read as a handbook.
- State that it does not replace live contract docs.
- Add a small "before your first change" live-doc table near the top.

The guide can continue to explain technology choices, runtime flows, first bug fix procedure, first feature procedure, and interview talking points. The important change is making authority boundaries obvious before the long explanation starts.

### 4. Refine `docs/agent-workflow.md`

Add live-doc priority to the pre-change checklist:

- Use `settings.gradle.kts` for active module truth.
- Use live docs for current behavior and ownership.
- Use `docs/superpowers/`, `docs/history/`, and `docs/improvements/` only as rationale unless the task asks for historical analysis.

Make the documentation update section more actionable by mapping change type to live docs:

| Change type | Docs to inspect or update |
| --- | --- |
| Module ownership or dependency direction | `docs/module-contracts.md`, `docs/architecture.md` |
| Runtime/data flow | `docs/architecture.md`, possibly `docs/project-reading-guide.md` |
| State ownership or lifecycle | `docs/state-model.md` |
| Cache, stale, refresh failure, watchlist fallback | `docs/offline-strategy.md` |
| UI information hierarchy or shared primitives | `README.md`, `.impeccable.md`, `docs/architecture.md`, related feature docs if present |
| Test meaning or command surface | `docs/test-strategy.md`, `docs/verification-matrix.md` |
| Release, deployment, performance | `docs/deployment.md`, `docs/performance.md`, `docs/release-notes/`, `CHANGELOG.md` |
| New learning path or onboarding flow | `docs/project-reading-guide.md`, `docs/onboarding/developer-onboarding-guide.md` |

### 5. Clarify `docs/verification-matrix.md`

Separate documentation-only verification into three cases:

1. Historical or evidence docs only.
   - Run `git diff --check -- <changed files>`.
   - Do not require Gradle tests unless claims about current behavior are changed.
2. Live contract docs.
   - Run the current document diff check.
   - Select tests for any described behavior or contract that changed.
   - Reconfirm paths, modules, and Gradle tasks if the doc names them.
3. README, demo story, release, deployment, or performance docs.
   - Run diff check.
   - Run or cite the relevant assemble, benchmark, release, or verification command if the changed text claims a current result.

This keeps documentation work lightweight while preserving trust when docs make current technical claims.

## Expected Result

After implementation:

- A new developer can start at `README.md`, follow onboarding, and know exactly when to switch to live contract docs.
- A coding agent can start at `AGENTS.md`, use `project-reading-guide.md` as a task router, and avoid reading historical plans as current contracts.
- `README.md` remains a concise, useful landing page.
- `docs/onboarding/developer-onboarding-guide.md` remains valuable as a learning path without competing with source-of-truth docs.
- `docs/agent-workflow.md` gives enough process detail for real changes without bloating `AGENTS.md`.
- `docs/verification-matrix.md` makes document-only verification decisions more explicit.

## Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| The router becomes another long document to maintain. | Keep it focused on reading paths and links, not full explanations. |
| README loses useful portfolio value if too much is removed. | Only group and clarify; avoid large deletions. |
| Onboarding and architecture still duplicate some concepts. | Allow light duplication for teaching, but mark architecture/module/state/offline docs as authority. |
| Agents still read historical docs too early. | Repeat the live-vs-history rule in `project-reading-guide.md`, `agent-workflow.md`, and README documentation map. |
| Verification becomes too heavy for docs-only changes. | Keep `git diff --check` as the default and require tests only when current technical claims change. |

## Verification Plan

Primary verification for the implementation:

```bash
git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md
```

Additional checks while editing:

```bash
find docs -maxdepth 3 -type f | sort
sed -n '1,220p' settings.gradle.kts
rg -n "docs/superpowers|docs/history|docs/improvements|single source|단일 출처|현재 계약|live" README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md
```

Gradle tests are not required for the documentation navigation implementation unless the final edits change current behavior claims, module names, task names, or verification command semantics.

## Implementation Boundary

The implementation plan should edit only documentation unless a broken link or incorrect command requires a narrow correction.

Expected files:

- `README.md`
- `docs/project-reading-guide.md`
- `docs/onboarding/developer-onboarding-guide.md`
- `docs/agent-workflow.md`
- `docs/verification-matrix.md`

Do not edit:

- App source code.
- Gradle build logic.
- `AGENTS.md`, unless implementation planning identifies a missing always-on rule that is short enough for the operating contract.
- Historical specs and plans under `docs/superpowers/`.
