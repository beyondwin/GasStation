# Upgrade Risk Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Compose test API and Gradle 10 deprecation risks exposed by the 2026-07-16 dependency refresh, preserve coverage output, and merge the verified result into local `main`.

**Architecture:** Migrate all Compose test-environment factories to the official v2 package and add a source guard to prevent deprecated v1 factories from returning. Replace the per-project Kover plugin with Kover 0.9.8's official settings aggregation plugin so coverage remains multi-module without executing the Gradle-10-incompatible plugin code path.

**Tech Stack:** Gradle 9.6.1, AGP 9.3.0, Kotlin 2.4.10, Compose UI Test 1.11.4, Kover 0.9.8 aggregation plugin, Robolectric 4.16.1.

## Global Constraints

- Preserve production behavior, demo/prod flavor behavior, module boundaries, and the current `build/reports/kover/report.xml` upload path.
- Keep coverage report-only; do not introduce a coverage threshold.
- Do not adopt an unreleased Kover build or vendor a patched third-party binary.
- Keep the current Roborazzi goldens unless the v2 dispatcher creates a reviewed, deterministic renderer change.
- Treat `--warning-mode fail` as the Gradle deprecation regression gate.

---

### Task 1: Guard and migrate deprecated Compose test factories

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/RoborazziDesignSystemTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Consumes: deprecated `androidx.compose.ui.test.junit4.create*Rule` imports.
- Produces: `verifyNoDeprecatedComposeTestApis` and v2 factory imports under `androidx.compose.ui.test.junit4.v2`.

- [ ] **Step 1: Add the source guard and prove RED**

Add a cache-safe root verification task that scans `src/test` and `src/androidTest` Kotlin sources for the three deprecated factory import prefixes and reports each file. Run:

```bash
./gradlew verifyNoDeprecatedComposeTestApis
```

Expected: FAIL and list all seven current files.

- [ ] **Step 2: Migrate the seven imports**

Replace only these factories with their v2 package equivalents:

```kotlin
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
```

Keep the tests and assertions otherwise unchanged until a failing test proves explicit dispatcher synchronization is required.

- [ ] **Step 3: Prove GREEN and exercise dispatcher-sensitive tests**

Run:

```bash
./gradlew verifyNoDeprecatedComposeTestApis \
  :core:designsystem:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  verifyRoborazziDebug \
  :app:compileDemoDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`. If a v2 test remains in a queued state, add the smallest official `waitForIdle` or `runOnIdle` synchronization at that assertion and rerun the owning test first.

- [ ] **Step 4: Add both deprecation gates to CI**

Add `verifyNoDeprecatedComposeTestApis` to `static-analysis` and run that job's Gradle invocation with `--warning-mode fail`.

### Task 2: Replace the Gradle-10-incompatible Kover application path

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build-logic/convention/build.gradle.kts`
- Delete: `build-logic/convention/src/main/kotlin/GasStationKoverConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`
- Modify: `CHANGELOG.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`
- Modify: `docs/deployment.md`
- Modify: `docs/onboarding/developer-onboarding-guide.md`

**Interfaces:**
- Consumes: Kover 0.9.8 settings aggregation DSL and the repository's explicit unit-test task matrix.
- Produces: root `koverXmlReport` at `build/reports/kover/report.xml`, with the existing generated-code exclusions.

- [ ] **Step 1: Capture the Gradle deprecation RED evidence**

Run:

```bash
./gradlew help --warning-mode fail --no-configuration-cache
```

Expected: FAIL with `DependencyProjectNotationConverter`, originating from `kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt.prepare`.

- [ ] **Step 2: Move coverage ownership to settings aggregation**

Apply `org.jetbrains.kotlinx.kover.aggregation` version `0.9.8` in `settings.gradle.kts`, call `enableCoverage()`, exclude `:benchmark`, and preserve the existing Hilt/Compose/preview class filters. Remove the regular Kover plugin alias, convention plugin dependency/registration/file, per-module application calls, and root `kover(...)` dependency graph.

Configure root `koverXmlReport` to depend on the same explicit JVM and Android debug unit-test tasks used by CI so a standalone report command remains complete and deterministic.

- [ ] **Step 3: Prove Gradle warning GREEN**

Run:

```bash
./gradlew help --warning-mode fail --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL` with no deprecation failure.

- [ ] **Step 4: Prove coverage parity**

Run:

```bash
./gradlew clean koverXmlReport --warning-mode fail
```

Expected: `BUILD SUCCESSFUL`, `build/reports/kover/report.xml` exists, and the XML contains classes from `app`, Android core/data/feature modules, and JVM domain/core modules.

- [ ] **Step 5: Synchronize live documentation**

Document that coverage now uses the official settings aggregation path, that `koverXmlReport` still owns the complete report, and that the Gradle 10 deprecation path is no longer applied. Do not rewrite historical release notes or completed plans.

### Task 3: Full verification, commit, and local-main merge

**Files:**
- Verify every file changed in Tasks 1-2.

**Interfaces:**
- Consumes: warning-free build configuration, v2 Compose test factories, aggregated coverage.
- Produces: a clean merge commit or fast-forward on local `main`.

- [ ] **Step 1: Run full regression**

Run the repository static, unit, screenshot, mutation, coverage, debug, benchmark, and release targets with `--warning-mode fail`.

Expected: `BUILD SUCCESSFUL` with zero Gradle deprecations and no test failures.

- [ ] **Step 2: Inspect and commit the branch**

Run `git diff --check`, inspect the complete diff, and commit with:

```bash
git commit -m "chore: close dependency upgrade risks"
```

- [ ] **Step 3: Merge and verify local main**

Switch to `main`, merge `codex/resolve-upgrade-risks`, rerun the warning gate plus representative full regression on the merged path, and confirm `git status --short` is empty.
