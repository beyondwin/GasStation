# Engineering Quality Hardening Program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the approved architecture, code, data, quality-gate, and documentation hardening design as an ordered series of independently reviewable and testable changes.

**Architecture:** This index coordinates four focused implementation plans. Phase 0 establishes measured baselines and documentation validation foundations; data invariants land before feature-state restructuring; quality gates are promoted only after clean report-only evidence; documentation contracts are updated continuously and structurally completed last.

**Tech Stack:** Kotlin/Android/Compose, Coroutines, Hilt, Room, Retrofit, Gradle Kotlin DSL/TestKit, JaCoCo, PIT, Python standard library, GitHub Actions, Markdown/JSON

## Global Constraints

- Design authority: `docs/superpowers/specs/2026-08-12-architecture-code-documentation-quality-hardening-design.md`.
- Preserve all product invariants and the existing 18 active modules.
- Use TDD RED/GREEN for behavior and parser/TestKit fixtures for build/document gates.
- Keep each commit narrow and leave the branch green at every review boundary.
- Update the live document that owns a changed contract in the same phase.
- Do not push, open a PR, release, or deploy without separate authorization.
- Report unavailable device or production-network evidence explicitly.

## Plan Set

1. `2026-08-12-data-correctness-hardening.md`
2. `2026-08-12-state-concurrency-and-responsibility.md`
3. `2026-08-12-quality-gates-and-reproducibility.md`
4. `2026-08-12-documentation-architecture.md`

## Required Execution Order

### Phase 0: Baseline and foundational guards

- [ ] Run `scripts/agent/preflight.sh`; confirm the intended worktree, clean/known dirty state, Java, SDK, and active modules.
- [ ] Execute Quality Task 1 and commit the clean JaCoCo/PIT baseline captured from the execution-start HEAD.
- [ ] Execute Documentation Tasks 1–2 to establish the hub, catalog, validator, and agent-script integration.
- [ ] Run the current static-analysis path and the expected failing test-source lint path; record the four known errors before fixing them in Phase 4.
- [ ] Add each Phase 1/2 P1 regression test immediately before the implementation task that names it and observe RED.

Exit: quality observations are reproducible, live documentation has a validator foundation, and every P1 behavioral change starts from a deterministic failure.

### Phase 1: Data correctness

- [ ] Execute all tasks in `2026-08-12-data-correctness-hardening.md` in order.
- [ ] Obtain a focused review after Tasks 2, 3, and 5 because they change transport, database observation, and persistence concurrency contracts.
- [ ] Run the plan's Phase 1 aggregate verification and commit its live documentation.

Exit: latest-intent persistence, atomic observation, time-driven freshness, fuel parity, typed retry, safe reporting, and exported migration evidence are green.

### Phase 2: State and concurrency integrity

- [ ] Execute State Tasks 1–4.
- [ ] Confirm location/address supersession, search resubscription, watch latest intent, and FIFO command acknowledgement with focused tests.
- [ ] Run feature/app compilation after the command type migration.

Exit: obsolete asynchronous results cannot commit and lifecycle collector gaps cannot lose commands.

### Phase 3: Responsibility split

- [ ] Execute State Tasks 5–8.
- [ ] Review `RefreshCoordinator`, `StationListStateAssembler`, and the final `StationListViewModel` responsibilities independently.
- [ ] Confirm the monolithic ViewModel test has been replaced by behavior-owner tests without losing assertions.

Exit: the ViewModel is a coordinator, policy collaborators are focused and independently tested, and architecture/state documents match the code.

### Phase 4: Quality gates and reproducibility

- [ ] Execute Quality Tasks 2–10.
- [ ] For lint, coverage, dependency/API, PIT, and device gates, keep report-only and blocking promotion in separate commits.
- [ ] Require parser fixtures or TestKit negative tests before promoting a custom gate.
- [ ] Require repeated hosted-device evidence before making the bounded device path blocking.
- [ ] Review wrapper checksums, dependency verification metadata, action SHAs, and runner limitations rather than trusting generated output.

Exit: every blocking gate passes at one HEAD, has a local reproduction command, and has a documented rollback commit.

### Phase 5: Documentation information architecture

- [ ] Resume Documentation Task 3 and execute through Task 8.
- [ ] Preserve stable entry paths while splitting onboarding and verification content.
- [ ] Remove exact module-graph and canonical-command duplication.
- [ ] Generate historical indexes without rewriting historical claims.
- [ ] Run the validator, index, agent-contract, docs, and auto verification gates.

Exit: every live contract is cataloged, reachable within two links, internally linked without error, singly owned, and aligned with the final implementation.

## Review Checkpoints

Use reviewer gates after these commits rather than after arbitrary time intervals:

1. baseline and documentation validator foundation;
2. latest refresh plus atomic snapshot correctness;
3. typed failure/freshness/schema evidence;
4. state generations, search recovery, watch, and commands;
5. coordinator/assembler/thin ViewModel;
6. lint/coverage gate promotion;
7. dependency/API/PIT/device/reproducibility promotion;
8. final documentation architecture and full verification.

Each review checks the approved design invariants, test evidence, module boundaries, documentation ownership, and whether unrelated user changes remain untouched.

## Final Verification At One HEAD

- [ ] Run Python unit suites under `scripts/quality/tests` and `scripts/docs/tests`.
- [ ] Run build-logic TestKit tests.
- [ ] Run all unit-test tasks selected by `scripts/agent/verify.sh auto`.
- [ ] Run explicit demo/prod/test-source lint, module/API/ABI guards, coverage validation, and selected PIT.
- [ ] Run Roborazzi verification and demo/prod/benchmark assembly.
- [ ] Run API 24/28/36 evidence that the current environment supports; attach artifacts and name gaps.
- [ ] Run `scripts/agent/verify.sh docs` and `scripts/agent/verify.sh auto` again after the last documentation change.
- [ ] Run `git diff --check` and inspect the complete branch diff.
- [ ] Request independent code review and address only verified actionable findings.
- [ ] Report changed files, commands/results, unverified areas, branch/worktree state, and local-versus-remote state.

## Rollback Strategy

- Behavioral commits remain valid without later CI gates; revert a noisy gate rather than reverting a correctness fix.
- Every report-only-to-blocking promotion is a separate commit.
- ABI/API baseline updates are reviewed artifacts and can be reverted independently.
- Device jobs remain non-blocking until stable; workflow removal does not affect application behavior.
- Documentation splits preserve the original entry paths, so reverting a structural split does not require restoring external links.
- Database migrations and checked schema history are never rewritten destructively; rollback adds a forward migration if persisted schema has shipped.

## Completion Boundary

Implementation is complete only when all four plans are checked, final verification was executed at the final HEAD, no required work remains, and any unavailable external/device evidence is explicitly scoped. Branch integration, push, PR, release, and deployment remain separate user decisions.
