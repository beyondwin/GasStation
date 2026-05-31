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
