# 빌드 속도

속도 설정은 결과가 맞을 때만 유지한다. 지금 `gradle.properties`는 `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`다. 검증이 깨지면 제품 코드보다 이 경계를 먼저 본다.

## Local Timing Snapshot

Date: 2026-05-31
Machine: local developer machine
Java: 17

| Command | Result | real | user | sys |
| --- | --- | ---: | ---: | ---: |
| fast local check | PASS | 14.22 s | 0.85 s | 0.10 s |
| `:app:assembleProdRelease` | PASS | 28.08 s | 0.83 s | 0.10 s |

## Convention Quality-Gate Timing Snapshot

At implementation commit `12e619b8...`, the final local convention run completed 52 suites / 90 tests with 0 failures, 0 errors, and 0 skipped in 11m46s. The later `4173dd05...` fix introduced the governed 35-minute marker; subsequent hosted main CI runs demonstrated that both 27- and 35-minute ceilings interrupt the unchanged inventory, so the current blocking ceiling is 50 minutes. Scoped review at the exact final HEAD reported SPEC PASS and QUALITY PASS with no findings.

The single governed Linux invocation at `4173dd05...` exited 2 after 0.602362792s because inherited `SSH_AUTH_SOCK` was rejected before attempt allocation. That duration is infrastructure preflight latency, not convention-suite build time: the status is attempt 0 and `NOT_MEASURED`, with no same-code retry. The exact invocation and evidence interpretation live in the [verification matrix](verification-matrix.md#build-input-provenance와-unsigned-release-재현성).

## Decisions

- Keep `org.gradle.parallel=true` enabled because the fast local check and release assemble passed with the current module graph.
- Keep `org.gradle.caching=true` enabled because the verification commands passed with the current build cache configuration.
- Keep `org.gradle.configuration-cache=true` enabled only while the documented verification matrix stays green. If a task becomes incompatible, disable configuration cache for the failing command before changing product code.
- Keep `:app:assembleProdRelease` outside the PR default gate. It remains a `main` and `v*` tag gate in GitHub Actions and a release/deployment local check.

## CI Interpretation

GitHub Actions already separates `static-analysis`, `unit-tests`, `screenshot-tests`, `assemble`, `release-assemble`, and `coverage`. The `assemble` job intentionally runs demo debug, prod debug, and benchmark assemble as separate Gradle invocations to avoid a memory peak on hosted runners.

## Mutation cost boundary

Mutation verification is deliberately outside ordinary fast/auto Gradle task arrays. The routed CI/manual runner creates the required route evidence, runs `verifyPitestConfiguration`, and owns the real three-module PIT work. Selected modules run sequentially with `--no-parallel`, each PIT process uses exactly two threads, and the hosted job has a 60-minute ceiling. History, retry, task exclusion, dry-run, and automatic rerun are disabled so elapsed time and mutant populations remain honest.

The sealed mutation runner uses configuration cache but disables build cache and reruns tasks. Its isolated proof must show both a stored first run and reused second run. The outer convention TestKit suite keeps the reviewed duration ledger as its five-lane planning input but uses a 50-minute blocking timeout because hosted main CI exhausted both 27- and 35-minute ceilings; it runs with `--no-configuration-cache` because its script listeners and dispatch staging are not cache-serializable. Weekly `ubuntu-24.04` image rotation can stop before PIT while the reviewed image profile is recaptured; that maintenance cost is intentional fail-closed behavior, not a reason to accept runtime-observed hashes as permanent tool pins.

## Build-input cost boundary

Governed jobs use pinned `setup-gradle` with the basic cache provider, but each job still owns a fresh `GRADLE_USER_HOME`. Configuration-cache proof is a separate same-job two-run check: first run must store and second run must reuse. It does not share cache evidence with the two-copy reproducibility probe.

The reproducibility probe is intentionally an expensive release-quality gate. Each copy has separate Gradle/project/Kotlin cache roots and disables build cache and configuration cache, so cache reuse cannot masquerade as independent artifact generation. Dependency verification metadata is intentionally not operated for this sample project; wrapper checksum, pinned inputs, configuration-cache checks, and ordinary build/test/lint remain. Timing claims are added only from measured final runs; hosted execution that did not occur remains `NOT RUN`. The exact commands live in [검증 매트릭스](verification-matrix.md).
