# GasStation Codex Agent Setup Design

**Date:** 2026-07-19

**Status:** Approved design

**Scope:** Repository-local agent guidance, executable guardrails, portable Codex hooks, CI checks, and contributor workflow alignment

## Goal

Make Codex consistently effective in GasStation without turning `AGENTS.md` into a handbook or forcing personal model and permission preferences on every contributor.

The setup should help an agent:

1. recover the correct project and worktree state before acting;
2. choose the right live documentation and module owner;
3. preserve user changes and local secrets;
4. select verification proportional to the changed area;
5. detect documentation and agent-configuration drift mechanically;
6. report completion with concrete evidence and clear local-versus-remote scope.

## Success Criteria

- A new Codex session can identify repository, branch, linked-worktree, dirty-state, Java, Gradle, Android SDK, and unfinished-work conditions through one read-only command.
- A linked worktree missing `local.properties` has a safe, non-overwriting bootstrap path.
- Repository-local Codex hooks use only portable, Git-root-resolved paths.
- Obvious destructive shell and Git operations are blocked before execution while ordinary development commands continue to work.
- Verification commands are selected from one executable entry point rather than copied from several documents by hand.
- CI detects live-document link drift, Java/SDK/version/module drift, personal absolute paths in tracked agent configuration, secret-like content, and broken agent scripts before expensive Android jobs run.
- Existing Android architecture, test, screenshot, release, and coverage gates remain authoritative and unchanged unless the implementation explicitly needs to connect the new agent-contract job.
- Final agent reports name changed files, executed commands, results, unverified areas, and local/remote state.

## Current-State Findings

The repository already has a strong documentation hierarchy:

- `AGENTS.md` is a concise operating contract.
- `docs/agent-workflow.md` owns change procedure.
- `docs/module-contracts.md` owns module placement and forbidden dependencies.
- `docs/project-reading-guide.md` routes readers to live documents and code.
- `docs/test-strategy.md` and `docs/verification-matrix.md` own test intent and concrete commands.

The remaining problems are mostly executable-enforcement and portability gaps:

1. `.claude/settings.json` references deleted personal absolute paths under `/Users/kws/.config/superpowers/worktrees/...`; both tracked hooks are currently unavailable on this checkout.
2. `.codex/` is ignored wholesale, so repository-local Codex configuration and hooks cannot be shared.
3. The only tracked shell entry point is `benchmark/run-demo-benchmark.sh`; there is no agent preflight, worktree bootstrap, contract check, or verification router.
4. `CONTRIBUTING.md` says `JDK 17, Android SDK 35`, while live test and verification contracts require Java 21 or newer and the Android build uses the current compile SDK 37 toolchain.
5. Long verification commands are repeated across CI, `CONTRIBUTING.md`, `README.md`, and verification documents, increasing drift risk.
6. The project has 18 active Gradle modules and 27 tracked Roborazzi snapshots, so module ownership, screenshot scope, and device-versus-host verification cannot safely rely on agent memory.
7. The current live-document relative-link audit is clean. The new checks must preserve that state without treating intentionally historical `docs/superpowers/`, `docs/history/`, or `docs/improvements/` content as current contracts.
8. Previous linked-worktree execution required connecting the root `local.properties` for Android SDK discovery. The recovery path exists only as prior-session knowledge, not as a repository command.

## Design Principles

### Keep durable prose small

`AGENTS.md` contains only rules that every worker needs. Specialized constraints live in the nearest high-risk subtree. Long procedures remain in the existing live documents.

### Prefer executable contracts over repeated instructions

Rules that can be checked deterministically belong in scripts and CI. Documentation explains why and when; scripts decide pass, warning, or failure.

### Preserve user state by default

No agent script may automatically stash, reset, clean, delete, overwrite local configuration, switch branches, push, tag, publish, or deploy.

### Keep heavy work explicit

Hooks perform fast discovery and safety checks. Gradle suites, screenshots, connected tests, and benchmarks run only through an explicit verification scope.

### Separate repository policy from personal Codex preferences

The project must not pin model choice, reasoning effort, sandbox mode, approval policy, provider, authentication, telemetry, or global MCP configuration.

### Use expensive analysis conditionally

The default discovery order is live documents, focused symbol search, then actual code and tests. Graph generation or visualization is used only for unknown cross-source relationships or an explicit freshness requirement.

## Responsibility Model

| Surface | Responsibility |
| --- | --- |
| Root `AGENTS.md` | Product invariants, architecture boundaries, state-preservation rules, authority order, completion evidence, and external-action scope |
| `docs/AGENTS.md` | Live-versus-history ownership, link/path/command validation, and documentation-only verification rules |
| `core/database/AGENTS.md` | Schema and migration safety, destructive-migration prohibition, snapshot semantics, and database verification |
| `benchmark/AGENTS.md` | Physical-device evidence, emulator limits, stable selectors, measurement metadata, and performance-document updates |
| `.codex/config.toml` | Minimal repository-local Codex feature and hook activation only |
| `.codex/hooks.json` | Portable lifecycle-hook wiring resolved from the Git root |
| `scripts/agent/*` | Read-only preflight, safe worktree bootstrap, executable contract checks, command policy, and verification routing |
| `.github/workflows/android.yml` | Fast agent-contract enforcement before the existing Android build matrix |
| `.github/PULL_REQUEST_TEMPLATE.md` | Human- and agent-readable change classification and verification evidence |

## Root `AGENTS.md` Additions

The existing product, UI, architecture, and change guardrails stay intact. Add only the following missing global rules:

- Authority order is actual code plus `settings.gradle.kts`, then live contract documents, then design/plan/history documents.
- Before resuming prior work, inspect `git worktree list`, `git status --short`, the relevant diff, and an existing `.superpowers/sdd/progress.md` ledger before creating a branch or worktree.
- Treat diagnose/review/report requests as read-only unless the user also asks for implementation.
- A completion claim must include changed files, executed commands, command results, unverified areas, and local/remote state.
- Push, PR, release, tag, publish, and deploy actions require explicit task scope; implementation or local completion does not imply them.
- Use Graphify or equivalent generated analysis only when direct documents, focused search, and code/test tracing cannot answer the relationship question efficiently.
- Use `scripts/agent/preflight.sh` before non-trivial changes and `scripts/agent/verify.sh` before completion.

## Nested `AGENTS.md` Files

### `docs/AGENTS.md`

- Classify `README.md`, `AGENTS.md`, root contributor files, and the existing live documents as current contracts.
- Treat `docs/superpowers/`, `docs/history/`, `docs/improvements/`, compose metrics, and past release notes as historical evidence unless the task explicitly targets them.
- Do not rewrite historical paths or results merely to match the present repository.
- When a live document claims a file, task, module, version, or CI job, verify the actual surface.
- Use the documentation verification scope and avoid unrelated Gradle suites for history-only changes.

### `core/database/AGENTS.md`

- Preserve `StationSearchResult.hasCachedSnapshot` semantics and the distinction between a successful empty snapshot and no snapshot.
- Require a deliberate Room schema-version decision for entity or DAO schema changes.
- Require migration tests and schema export review for schema changes.
- Prohibit destructive fallback as a shortcut unless an explicitly approved product decision changes the data-loss contract.
- Verify cache pruning, atomic snapshot replacement, and watchlist fallback when related tables or queries change.

### `benchmark/AGENTS.md`

- Treat emulator runs as smoke evidence only; committed performance numbers require a physical device.
- Require explicit `ANDROID_SERIAL` when more than one device is available.
- Preserve resource-exposed Compose selectors used by benchmark journeys.
- Record device model, OS/API, variant, date, scenario, and output artifact paths when updating committed metrics.
- Do not overwrite `docs/performance.md` numbers after a failed, partial, warm-state-only, or emulator run.

No nested file is added to `app` or `feature:*` in this change. Their current responsibilities are already explicit in the root contract and live architecture documents; duplicating them would increase context and drift.

## Executable Agent Tools

### `scripts/agent/preflight.sh`

This is read-only and safe to run at session start.

It reports:

- Git root and current working directory;
- branch, detached HEAD, HEAD commit, and linked-worktree status;
- concise dirty and untracked paths without displaying file contents;
- `git worktree list` entries;
- Java version with a Java 21 minimum check;
- Gradle wrapper availability and version;
- Android SDK discovery through `local.properties`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT` without printing secret or machine-local values unnecessarily;
- active module count from `settings.gradle.kts`;
- presence of an unfinished `.superpowers/sdd/progress.md`;
- optional device state only when a device-oriented flag is supplied.

Exit behavior:

- Dirty state and an unfinished ledger are warnings, not failures.
- Missing Java, Gradle wrapper, or Android SDK is a failure only when the requested operation needs it.
- The hook mode produces concise output suitable for agent context.

### `scripts/agent/bootstrap-worktree.sh`

This script prepares an already-created linked worktree. It does not create or delete worktrees.

Behavior:

1. Confirm the current directory is a linked Git worktree.
2. Resolve the primary worktree through `git worktree list --porcelain`.
3. Confirm the primary `local.properties` is a regular file or safe symlink.
4. If the current worktree has no `local.properties`, create a relative or resolved symlink to the primary file.
5. If any file or symlink already occupies the target, stop without changing it.
6. Run the lightweight preflight after linking.

It never prints the contents of `local.properties` and never copies it into Git tracking.

### `scripts/agent/check-contracts.sh`

This is the fast, CI-safe contract checker.

Checks:

- shell syntax for `scripts/agent/*.sh`;
- relative Markdown links in root and live documents;
- referenced live files and directories;
- active-module count and module names against machine-readable documentation assertions;
- Java/SDK/version assertions against build configuration and the verification contract;
- personal absolute paths in tracked agent configuration and hook commands;
- common committed secret assignments without echoing matched values;
- tracked debug artifacts, crash dumps, local properties, keystores, and agent runtime state;
- whitespace errors with `git diff --check` when a diff exists.

Historical documents are excluded from current-contract assertions unless passed explicitly.

### `scripts/agent/verify.sh`

Supported scopes:

| Scope | Intent |
| --- | --- |
| `docs` | Contract checks plus diff validation for documentation-only changes |
| `fast` | Existing quick local host-side checks and demo assembly |
| `ui` | Affected design-system/feature unit tests plus Roborazzi verification |
| `data` | Relevant model/domain/data/database tests and module-boundary guard |
| `app` | Demo/prod app unit tests and required assembly paths |
| `release` | Existing merge/release verification contract without device benchmarks |
| `auto` | Classify changed tracked and untracked paths, print the selected scopes, then run their union |

`auto` classification rules are conservative:

- docs-only paths select `docs`;
- feature or design-system UI paths add `ui`;
- model, domain, data, database, datastore, network, or location paths add `data` or the corresponding focused host-side tests;
- app, flavor, manifest, navigation, startup, build-logic, version catalog, or Gradle configuration paths add `app` and contract guards;
- release, signing, deployment, CI release jobs, or version changes recommend or require `release`;
- unknown production paths fall back to at least `fast`.

The script prints every command before execution, stops with the failing exit code, and never records or claims a pass for commands that were not run.

## Codex Project Configuration and Hooks

### `.codex/config.toml`

Keep the configuration minimal. It may activate stable repository-local hooks and point to repository-owned hook files. It must not set personal model, provider, reasoning, approval, sandbox, authentication, telemetry, notification, or MCP choices.

Codex loads project configuration and hooks only for a trusted repository. Hook definitions require trust review when first added or when their content changes.

### `.codex/hooks.json`

Use one hook representation only; do not duplicate the same hooks inline in TOML.

Events:

- `SessionStart` for `startup|resume`: run `preflight.sh --hook`.
- `PreToolUse` matching shell execution: run a small policy script that parses hook JSON input and blocks only clearly destructive commands.
- `Stop`: run a cheap completion check that reports contract drift, dirty paths, and a suggested verification scope. It must not run Gradle suites or create an infinite continuation loop.

All commands resolve the repository through `git rev-parse --show-toplevel`; no personal or worktree-specific absolute path is allowed.

### Shell Command Policy

The pre-tool policy blocks:

- recursive deletion aimed at `/`, a home directory, or the repository root;
- `git reset --hard`, `git clean -fd/-fdx`, or equivalent destructive restoration without a narrowly designed safe wrapper;
- force-push to `main`, `master`, or `trunk`;
- destructive database commands when passed through detected shell commands;
- obvious attempts to print tracked local secret files.

It permits ordinary builds, tests, formatting, Git inspection, normal branch operations, and explicit non-force pushes. Authorization-dependent remote actions remain governed by the user request and root `AGENTS.md`; the hook does not pretend to infer user intent.

## Claude Compatibility

Keep `.claude/settings.json`, but replace its missing personal hook paths with the same Git-root-resolved repository scripts used by Codex.

The repository scripts are the behavior source of truth. Surface-specific configuration only adapts event names and input format. This avoids separate safety policies for Codex and Claude.

## Ignore Rules

Replace the blanket `.codex/` ignore with allowlisting for tracked project configuration and hooks while continuing to ignore runtime state.

Tracked:

- `.codex/config.toml`
- `.codex/hooks.json`
- repository-owned hook helper scripts if stored below `.codex/`

Ignored:

- session state;
- hook trust cache;
- logs and traces;
- generated prompts or transcripts;
- local task state;
- temporary files.

Existing `.superpowers/`, `.gstack/`, `.orchestrator/`, `.worktrees/`, `.kotlin/`, `local.properties`, keystores, and environment files remain ignored.

## CI Design

Add an `agent-contracts` job near the start of `.github/workflows/android.yml`.

The job runs without Android compilation where possible:

1. checkout;
2. shell syntax checks;
3. fixture-based agent-script tests;
4. `scripts/agent/check-contracts.sh --ci`;
5. worktree-bootstrap test in a temporary Git repository;
6. dangerous-command policy allow/deny fixtures.

The job must finish quickly and provide actionable file and line output. The existing `static-analysis`, `unit-tests`, `screenshot-tests`, `assemble`, `release-assemble`, and `coverage` jobs retain their present responsibilities.

No new external CI dependency such as Bats or ShellCheck is required for the first version. Tests use POSIX/Bash primitives and temporary directories. A dedicated linter can be proposed later if script complexity grows enough to justify it.

## Pull Request Contract

Update `.github/PULL_REQUEST_TEMPLATE.md` to capture:

- goal and user/developer impact;
- affected modules and architecture-boundary changes;
- `demo` and `prod` impact;
- database schema or migration impact;
- UI semantics, accessibility, and stable test-tag impact;
- snapshot changes and direct image-inspection evidence;
- actual verification scopes and commands;
- skipped checks with reasons;
- live-document updates;
- remote push, release, or deploy status.

Checkboxes support classification, but free-text command and result evidence remains mandatory for meaningful verification.

## Documentation Updates

Implementation updates these live documents:

- `CONTRIBUTING.md`: replace stale JDK 17/SDK 35 setup with Java 21+ and the current Android SDK/toolchain contract.
- `docs/agent-workflow.md`: add continuation/worktree recovery, preflight, verification evidence, and local-versus-remote completion rules.
- `docs/verification-matrix.md`: document agent script scopes and the `agent-contracts` CI job.
- `docs/project-reading-guide.md`: route agents to nested contracts and the fast preflight path.
- Root `AGENTS.md`: add only the global rules described above.

Existing design, plan, history, improvement, compose-metric, and past release-note documents are not rewritten to mirror current paths or commands.

## Error Handling

| Condition | Behavior |
| --- | --- |
| Dirty worktree | Warn, list paths, preserve changes, continue only within non-overlapping scope |
| Detached HEAD | Warn that branch/push/PR completion may be unavailable; local edits and tests may continue if safe |
| Existing worktree ledger | Surface it before planning or creating another worktree |
| Missing Java 21+ | Block Gradle verification and print the required runtime |
| Missing Android SDK | Print discovery options; linked worktree points to bootstrap command |
| Existing worktree `local.properties` | Never overwrite it |
| Missing `opinet.apikey` | Continue deterministic demo and host-side checks; block only real prod execution that requires it |
| No connected device | Continue host-side checks; block only connected or physical-device evidence scopes |
| Multiple devices | Require explicit `ANDROID_SERIAL` for device scopes |
| Hook unavailable or untrusted | Warn and provide the equivalent manual script command |
| Test failure | Preserve the command, exit code, and first actionable failure; never claim completion |

## Agent Script Testing

Add a lightweight test runner covering at least:

- preflight on a clean repository;
- preflight on a dirty repository;
- detached HEAD detection;
- linked-worktree detection;
- safe `local.properties` linking;
- refusal to overwrite an existing worktree file or symlink;
- missing primary `local.properties` behavior;
- dangerous-command blocks;
- ordinary-command allows;
- live-link success and broken-link fixture failure;
- personal absolute hook-path fixture failure;
- Java/SDK/version drift fixture failure;
- `verify.sh auto` scope classification for docs, UI, data, app/build, and release fixtures.

Tests use temporary repositories and files. They must not operate destructive commands against the real workspace.

## Verification Plan

After implementation:

```bash
scripts/agent/check-contracts.sh
scripts/agent/test.sh
scripts/agent/preflight.sh
scripts/agent/verify.sh docs
./gradlew verifyModuleBoundaries verifyNoDeprecatedComposeTestApis verifyCiRobolectricRuntime
git diff --check
```

Because the change touches root build/CI/document contracts but not Android production behavior, the implementation plan must decide whether `verify.sh fast` is necessary based on the final diff. Any Gradle build-logic change requires the existing focused build and contract gates.

The final review also manually confirms:

- hook commands contain no personal absolute paths;
- hooks do not launch heavy Gradle work automatically;
- dirty files are never stashed, reset, or overwritten;
- no secret value appears in script output or fixtures;
- `.codex` runtime data remains ignored;
- Codex and Claude configurations call the same repository-owned policy scripts;
- the existing live-document relative-link audit remains clean.

## Rollout Sequence

1. Add executable scripts and their fixture tests.
2. Add nested `AGENTS.md` files and root contract additions.
3. Add `.codex` configuration and portable hooks.
4. Migrate `.claude/settings.json` to shared scripts.
5. Update ignore rules.
6. Connect `agent-contracts` CI.
7. Update PR template and live documents.
8. Run contract, script, Gradle guard, and diff verification.
9. Review final paths, hook trust behavior, and local/remote state.

This order ensures configuration never points at scripts that have not been added yet.

## Risks and Mitigations

### Hook friction

Project hooks require trust review and can become noisy.

Mitigation: keep only session preflight, narrowly scoped destructive-command blocking, and a cheap stop check. Do not run Gradle from hooks.

### Script becomes a second build system

A large verification router could duplicate Gradle and CI behavior.

Mitigation: scripts orchestrate existing authoritative tasks; they do not reimplement Android compilation or test logic.

### Documentation checks overreach into history

Historical plans intentionally contain old paths, superseded examples, and completed-state evidence.

Mitigation: current-contract checks target root and live documents by default. History is checked only for syntax or when explicitly requested.

### Cross-platform shell limitations

The first version targets the repository's current macOS development and Ubuntu CI environments.

Mitigation: use portable Bash, avoid GNU-only behavior where practical, and keep Windows support as an explicit future decision rather than claiming unverified compatibility.

### False confidence from automatic scope selection

Changed-file mapping cannot understand every semantic impact.

Mitigation: print selected scopes, use conservative escalation, allow explicit overrides, and keep the final human/agent diff review mandatory.

### Duplicated Codex and Claude behavior

Surface-specific hook formats can drift.

Mitigation: both configurations call the same repository scripts, and CI rejects personal absolute paths and missing hook targets.

## Non-Goals

- Installing or creating a plugin.
- Adding MCP servers or external connectors.
- Changing global `~/.codex` configuration.
- Pinning a Codex model or reasoning level.
- Weakening or broadening sandbox and approval policy.
- Automatically creating branches, commits, pushes, pull requests, tags, releases, or deployments.
- Rewriting Android product architecture or UI.
- Replacing Gradle, Roborazzi, connected tests, or benchmark infrastructure.
- Guaranteeing Windows support in the first implementation.

## Definition of Done

The setup is complete only when:

1. clean, dirty, detached, primary, and linked-worktree cases are covered by tests;
2. worktree bootstrap links `local.properties` without overwriting existing state;
3. dangerous commands are blocked and ordinary development commands are allowed by fixtures;
4. no tracked Codex or Claude hook contains a personal absolute path;
5. live-document links and Java/SDK/version/module assertions match the repository;
6. `verify.sh auto` selects conservative scopes for representative changes;
7. existing Gradle contract guards pass;
8. no secret, debug artifact, or local runtime state is added;
9. final documentation names the new commands and CI behavior;
10. the completion report states verification evidence and local/remote status.

## References

- [Codex best practices](https://learn.chatgpt.com/guides/best-practices)
- [AGENTS.md guidance](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [Codex project configuration](https://learn.chatgpt.com/docs/config-file/config-basic)
- [Codex hooks](https://learn.chatgpt.com/docs/hooks)
- `AGENTS.md`
- `docs/agent-workflow.md`
- `docs/module-contracts.md`
- `docs/project-reading-guide.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`

## Approval Record

The user approved the balanced approach and each of the three design sections:

1. responsibility structure;
2. executable flow and automation boundary;
3. CI, review contract, documentation alignment, and completion criteria.
