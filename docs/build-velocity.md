# Build Velocity

GasStation keeps build-speed decisions tied to correctness checks. `org.gradle.parallel=true`, `org.gradle.caching=true`, and `org.gradle.configuration-cache=true` are currently enabled in `gradle.properties`, so the validation question is whether those defaults remain correct and documented.

## Local Timing Snapshot

Date: 2026-05-31
Machine: local developer machine
Java: 17

| Command | Result | real | user | sys |
| --- | --- | ---: | ---: | ---: |
| fast local check | PASS | 14.22 s | 0.85 s | 0.10 s |
| `:app:assembleProdRelease` | PASS | 28.08 s | 0.83 s | 0.10 s |

## Decisions

- Keep `org.gradle.parallel=true` enabled because the fast local check and release assemble passed with the current module graph.
- Keep `org.gradle.caching=true` enabled because the verification commands passed with the current build cache configuration.
- Keep `org.gradle.configuration-cache=true` enabled only while the documented verification matrix stays green. If a task becomes incompatible, disable configuration cache for the failing command before changing product code.
- Keep `:app:assembleProdRelease` outside the PR default gate. It remains a `main` and `v*` tag gate in GitHub Actions and a release/deployment local check.

## CI Interpretation

GitHub Actions already separates `static-analysis`, `unit-tests`, `screenshot-tests`, `assemble`, `release-assemble`, and `coverage`. The `assemble` job intentionally runs demo debug, prod debug, and benchmark assemble as separate Gradle invocations to avoid a memory peak on hosted runners.

## Mutation cost boundary

Mutation verification is deliberately outside ordinary fast/auto Gradle task arrays. `verifyPitestConfiguration` is the fast agent gate; the routed CI/manual runner owns the real three-module PIT work. Selected modules run sequentially with `--no-parallel`, each PIT process uses exactly two threads, and the hosted job has a 60-minute ceiling. History, retry, task exclusion, dry-run, and automatic rerun are disabled so elapsed time and mutant populations remain honest.

The sealed runner uses configuration cache but disables build cache and reruns tasks. Its isolated proof must show both a stored first run and reused second run without changing the 15-minute convention-suite timeout, retrying failures, reducing test coverage, or increasing worker forks. Weekly `ubuntu-24.04` image rotation can stop before PIT while the reviewed image profile is recaptured; that maintenance cost is intentional fail-closed behavior, not a reason to accept runtime-observed hashes as permanent tool pins.
