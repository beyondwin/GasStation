# Build/Test Speed Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GasStation의 반복 빌드, PR static-analysis, unit-test 실행 시간을 줄이되 demo/prod 정식 경로와 현재 테스트 신뢰 범위를 유지한다.

**Architecture:** Gradle 실행 기본값은 root `gradle.properties`와 build-logic convention에서 관리한다. 느린 screenshot test와 route lifecycle test는 별도 실행 표면과 pure policy test로 분리한다. CI는 빠른 PR feedback 경로와 opt-in/full verification 경로를 문서화한다.

**Tech Stack:** Gradle 9.3.1, AGP 9.1.1, Kotlin 2.3.20, KSP, Hilt, Compose compiler, Android Lint, Robolectric, Roborazzi, GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-05-14-build-test-speed-implementation.md`](../specs/2026-05-14-build-test-speed-implementation.md)

---

## Source Of Truth

- Start with `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md`, `docs/test-strategy.md`, and `docs/verification-matrix.md`.
- Use `settings.gradle.kts` as the active module list.
- Keep `demo` and `prod` as first-class runtime paths.
- Preserve accessibility semantics and test tags. Faster tests must still protect price-first station-list behavior.

## File Structure

Modify:

- `gradle.properties`
- `.github/workflows/android.yml`
- `docs/verification-matrix.md`
- `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

Create:

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`

Move:

- `app/src/test/java/com/gasstation/BackupPolicyResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/BackupPolicyResourceTest.kt`
- `app/src/test/java/com/gasstation/AppIconResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/AppIconResourceTest.kt`
- `app/src/test/java/com/gasstation/SplashThemeResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt`
- `app/src/test/java/com/gasstation/NetworkSecurityConfigResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/NetworkSecurityConfigResourceTest.kt`
- `app/src/test/java/com/gasstation/SystemBarPolicyTest.kt` -> `app/src/testDemo/java/com/gasstation/SystemBarPolicyTest.kt`
- `app/src/test/java/com/gasstation/map/ExternalMapLauncherTest.kt` -> `app/src/testDemo/java/com/gasstation/map/ExternalMapLauncherTest.kt`

Delete:

- `app/src/test/java/com/gasstation/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/gasstation/ExampleInstrumentedTest.kt`

---

## Task 0: Baseline And Guardrails

**Files:**
- Read: `AGENTS.md`
- Read: `settings.gradle.kts`
- Read: `docs/verification-matrix.md`
- Read: `docs/superpowers/specs/2026-05-14-build-test-speed-implementation.md`

- [ ] **Step 1: Confirm starting state**

Run:

```bash
git status --short
```

Expected: no output, or only unrelated user changes that must be preserved.

- [ ] **Step 2: Re-run the current baseline**

Run:

```bash
/usr/bin/time -p ./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test --profile
```

Expected: `BUILD SUCCESSFUL`. Save the real time and the generated `build/reports/profile/profile-*.html` path in the PR notes.

- [ ] **Step 3: Re-run the lint baseline**

Run:

```bash
/usr/bin/time -p ./gradlew spotlessCheck lint --profile
```

Expected: `BUILD SUCCESSFUL`. Save the real time and profile path in the PR notes.

---

## Task 1: Enable Gradle Cache And Parallel Defaults

**Files:**
- Modify: `gradle.properties`

- [ ] **Step 1: Validate configuration cache before editing**

Run:

```bash
./gradlew help --configuration-cache
./gradlew :app:assembleDemoDebug --configuration-cache
./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test --configuration-cache
```

Expected: every command stores or reuses configuration cache and ends with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Edit Gradle properties**

In `gradle.properties`, replace the commented parallel note with active defaults:

```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true
```

Keep the existing JVM args line:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED
```

- [ ] **Step 3: Verify defaults without command-line flags**

Run:

```bash
./gradlew help
./gradlew :app:assembleDemoDebug
./gradlew :app:assembleProdDebug :benchmark:assemble
```

Expected:
- `BUILD SUCCESSFUL`.
- Gradle output mentions configuration cache storing or reusing entries.

- [ ] **Step 4: Commit this slice**

Run:

```bash
git add gradle.properties
git commit -m "build: enable Gradle cache defaults"
```

Expected: commit succeeds.

---

## Task 2: Gate Compose Compiler Reports

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`

- [ ] **Step 1: Update application compose convention**

In `GasStationAndroidApplicationComposeConventionPlugin.kt`, replace the unconditional Compose compiler extension block with:

```kotlin
val composeCompilerReportsEnabled = providers
    .gradleProperty("gasstation.composeCompilerReports")
    .map(String::toBoolean)
    .orElse(false)

extensions.configure<ComposeCompilerGradlePluginExtension> {
    if (composeCompilerReportsEnabled.get()) {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
    }
}
```

- [ ] **Step 2: Update library compose convention**

Apply the same replacement in `GasStationAndroidLibraryComposeConventionPlugin.kt`.

- [ ] **Step 3: Verify default compile**

Run:

```bash
./gradlew :feature:station-list:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify opt-in reports**

Run:

```bash
./gradlew :feature:station-list:compileDebugKotlin -Pgasstation.composeCompilerReports=true
```

Expected: `BUILD SUCCESSFUL`; Compose report/metric directories are produced under the module build directory when compilation runs.

- [ ] **Step 5: Commit this slice**

Run:

```bash
git add build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt
git commit -m "build: make Compose compiler reports opt-in"
```

Expected: commit succeeds.

---

## Task 3: Reduce Default Lint Scope

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`

- [ ] **Step 1: Add lint test-source property in application convention**

In `GasStationAndroidApplicationComposeConventionPlugin.kt`, before `extensions.configure<ApplicationExtension>`, add:

```kotlin
val lintTestSourcesEnabled = providers
    .gradleProperty("gasstation.lintTestSources")
    .map(String::toBoolean)
    .orElse(false)
```

Then set the application `lint` block to:

```kotlin
lint {
    warningsAsErrors = false
    abortOnError = true
    checkDependencies = true
    checkTestSources = lintTestSourcesEnabled.get()
    sarifReport = true
    htmlReport = true
    xmlReport = false
}
```

- [ ] **Step 2: Add lint test-source property in library convention**

In `GasStationAndroidLibraryConventionPlugin.kt`, before `extensions.configure<LibraryExtension>`, add:

```kotlin
val lintTestSourcesEnabled = providers
    .gradleProperty("gasstation.lintTestSources")
    .map(String::toBoolean)
    .orElse(false)
```

Then set the library `lint` block to:

```kotlin
lint {
    warningsAsErrors = false
    abortOnError = true
    checkDependencies = false
    checkTestSources = lintTestSourcesEnabled.get()
    sarifReport = true
    htmlReport = true
    xmlReport = false
}
```

- [ ] **Step 3: Verify default lint**

Run:

```bash
/usr/bin/time -p ./gradlew spotlessCheck lint --profile
```

Expected: `BUILD SUCCESSFUL`; profile no longer shows expensive default `lintAnalyze*UnitTest` tasks unless another Gradle default still requires them.

- [ ] **Step 4: Verify opt-in test-source lint**

Run:

```bash
./gradlew lint -Pgasstation.lintTestSources=true --continue
```

Expected: `BUILD SUCCESSFUL`; test source lint tasks run when the property is present.

- [ ] **Step 5: Commit this slice**

Run:

```bash
git add build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt
git commit -m "build: trim default lint scope"
```

Expected: commit succeeds.

---

## Task 4: Split Roborazzi From Regular Unit Tests

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt`

- [ ] **Step 1: Add Test task imports**

Add imports:

```kotlin
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
```

- [ ] **Step 2: Exclude Roborazzi classes unless a Roborazzi task is requested**

Inside `with(target)`, before `dependencies`, add:

```kotlin
val includeInUnitTests = providers
    .gradleProperty("gasstation.includeRoborazziInUnitTests")
    .map(String::toBoolean)
    .orElse(false)
val roborazziTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("Roborazzi", ignoreCase = true)
}

tasks.withType<Test>().configureEach {
    if (!roborazziTaskRequested && !includeInUnitTests.get()) {
        exclude("**/Roborazzi*Test.class")
    }
}
```

- [ ] **Step 3: Verify normal unit tests skip screenshot tests**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; test result XML for `RoborazziDesignSystemTest` and `RoborazziStationListScreenTest` is not updated by this command.

- [ ] **Step 4: Verify screenshot task still runs screenshot tests**

Run:

```bash
./gradlew verifyRoborazziDebug
```

Expected: `BUILD SUCCESSFUL`; Roborazzi results are produced under `build/test-results/roborazzi`.

- [ ] **Step 5: Verify explicit opt-in**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest -Pgasstation.includeRoborazziInUnitTests=true
```

Expected: `BUILD SUCCESSFUL`; `RoborazziDesignSystemTest` runs.

- [ ] **Step 6: Commit this slice**

Run:

```bash
git add build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt
git commit -m "test: keep Roborazzi out of regular unit tests"
```

Expected: commit succeeds.

---

## Task 5: Extract Station List Route Policy

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

- [ ] **Step 1: Add pure policy helpers**

Create `StationListRoutePolicy.kt`:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState

internal fun StationListUiState.shouldAutoRefreshOnRoute(): Boolean =
    isAvailabilityKnown &&
        isGpsEnabled &&
        (
            currentCoordinates == null ||
                hasDeniedLocationAccess ||
                needsRecoveryRefresh
            )

internal fun StationListUiState.watchlistCoordinatesOrNull(): Coordinates? =
    currentCoordinates?.takeIf {
        isGpsEnabled &&
            (
                permissionState != LocationPermissionState.Denied ||
                    hasDeniedLocationAccess
                )
    }
```

- [ ] **Step 2: Use helpers in route**

In `StationListRoute.kt`, replace the auto-refresh `LaunchedEffect` with:

```kotlin
LaunchedEffect(uiState.shouldAutoRefreshOnRoute()) {
    if (uiState.shouldAutoRefreshOnRoute()) {
        viewModel.onAction(StationListAction.AutoRefreshRequested)
    }
}
```

Replace the `onWatchlistClick` expression with:

```kotlin
onWatchlistClick = uiState.watchlistCoordinatesOrNull()?.let { coordinates ->
    { onWatchlistClick(coordinates) }
},
```

- [ ] **Step 3: Add policy tests**

Create `StationListRoutePolicyTest.kt`:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationListRoutePolicyTest {
    private val coordinates = Coordinates(37.497927, 127.027583)

    @Test
    fun `auto refresh waits until availability is known`() {
        assertFalse(StationListUiState(isAvailabilityKnown = false).shouldAutoRefreshOnRoute())
    }

    @Test
    fun `auto refresh waits while gps is disabled`() {
        assertFalse(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = false,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh runs when no coordinates are available`() {
        assertTrue(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = null,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh runs for denied demo coordinates`() {
        assertTrue(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = coordinates,
                hasDeniedLocationAccess = true,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh runs for recovery refresh`() {
        assertTrue(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = coordinates,
                needsRecoveryRefresh = true,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh is skipped for stable usable coordinates`() {
        assertFalse(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = coordinates,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `watchlist is hidden when denied permission has stale prod coordinates`() {
        assertNull(
            StationListUiState(
                currentCoordinates = coordinates,
                isGpsEnabled = true,
                permissionState = LocationPermissionState.Denied,
                hasDeniedLocationAccess = false,
            ).watchlistCoordinatesOrNull(),
        )
    }

    @Test
    fun `watchlist is visible for denied demo coordinates`() {
        assertEquals(
            coordinates,
            StationListUiState(
                currentCoordinates = coordinates,
                isGpsEnabled = true,
                permissionState = LocationPermissionState.Denied,
                hasDeniedLocationAccess = true,
            ).watchlistCoordinatesOrNull(),
        )
    }
}
```

- [ ] **Step 4: Reduce route Compose tests**

In `GpsAvailabilityMonitorTest.kt`, keep only route lifecycle tests that require `createAndroidComposeRule`, including:

```kotlin
fun `route ignores availability updates while stopped and resumes collection in foreground`()
```

Move auto-refresh/watchlist condition coverage to `StationListRoutePolicyTest`. Do not remove `StationListViewModelTest` coverage for location acquisition, permission recovery, failure, snackbar, and query refresh behavior.

- [ ] **Step 5: Verify station-list tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; `GpsAvailabilityMonitorTest` time is lower than the baseline 6.602s.

- [ ] **Step 6: Commit this slice**

Run:

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt
git commit -m "test: move station route policy to fast unit tests"
```

Expected: commit succeeds.

---

## Task 6: Remove App Test Duplication

**Files:**
- Move app tests listed in File Structure
- Delete: `app/src/test/java/com/gasstation/ExampleUnitTest.kt`
- Delete: `app/src/androidTest/java/com/gasstation/ExampleInstrumentedTest.kt`

- [ ] **Step 1: Move flavor-invariant resource tests to demo unit source set**

Move the files exactly as listed in File Structure. Keep package declarations unchanged.

- [ ] **Step 2: Keep flavor graph tests common**

Leave these files under `app/src/test/java`:

```text
app/src/test/java/com/gasstation/startup/AppStartupGraphTest.kt
app/src/test/java/com/gasstation/startup/AppStartupRunnerTest.kt
```

Reason: `AppStartupGraphTest` validates flavor-specific Hilt startup hook selection through `BuildConfig.DEMO_MODE`.

- [ ] **Step 3: Delete placeholder tests**

Delete:

```text
app/src/test/java/com/gasstation/ExampleUnitTest.kt
app/src/androidTest/java/com/gasstation/ExampleInstrumentedTest.kt
```

- [ ] **Step 4: Verify app tests**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

Expected:
- `BUILD SUCCESSFUL`.
- demo unit task still runs resource, external map, demo seed, and demo crash reporter tests.
- prod unit task runs prod startup/crash reporter plus shared startup graph/runner tests.

- [ ] **Step 5: Verify connected demo task graph**

Run:

```bash
./gradlew :app:connectedDemoDebugAndroidTest --dry-run
```

Expected: no reference to `ExampleInstrumentedTest`.

- [ ] **Step 6: Commit this slice**

Run:

```bash
git add app/src/test app/src/testDemo app/src/androidTest
git commit -m "test: avoid duplicate app resource tests"
```

Expected: commit succeeds.

---

## Task 7: Update CI And Verification Docs

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `docs/verification-matrix.md`

- [ ] **Step 1: Keep CI command names but document changed meaning**

Leave the existing unit-tests and screenshot-tests jobs as separate jobs:

```yaml
- name: Unit Tests
  run: |
    ./gradlew \
      :domain:location:test \
      :core:model:test \
      :domain:station:test \
      :domain:settings:test \
      :core:database:testDebugUnitTest \
      :core:datastore:testDebugUnitTest \
      :core:designsystem:testDebugUnitTest \
      :core:location:testDebugUnitTest \
      :core:network:test \
      :data:settings:testDebugUnitTest \
      :data:station:testDebugUnitTest \
      :feature:settings:testDebugUnitTest \
      :feature:station-list:testDebugUnitTest \
      :feature:watchlist:testDebugUnitTest \
      :app:testDemoDebugUnitTest \
      :app:testProdDebugUnitTest \
      :tools:demo-seed:test
```

The build-logic Roborazzi exclusion makes this command fast by default.

- [ ] **Step 2: Keep screenshot-tests job as the screenshot owner**

Ensure screenshot-tests still runs:

```yaml
- name: Roborazzi Screenshot Verification
  run: ./gradlew verifyRoborazziDebug
```

- [ ] **Step 3: Document opt-in commands**

In `docs/verification-matrix.md`, add these commands under static analysis/test notes:

```bash
./gradlew lint -Pgasstation.lintTestSources=true --continue
./gradlew :core:designsystem:testDebugUnitTest -Pgasstation.includeRoborazziInUnitTests=true
./gradlew :feature:station-list:compileDebugKotlin -Pgasstation.composeCompilerReports=true
```

- [ ] **Step 4: Document expected faster default**

In `docs/verification-matrix.md`, state:

```text
기본 unit-test 명령은 Roborazzi screenshot class를 제외한다. Screenshot 회귀는 `verifyRoborazziDebug`가 소유한다.
기본 lint 명령은 production source 중심으로 돌고, test source lint는 `-Pgasstation.lintTestSources=true`로 명시한다.
```

- [ ] **Step 5: Commit this slice**

Run:

```bash
git add .github/workflows/android.yml docs/verification-matrix.md
git commit -m "docs: clarify fast and full verification paths"
```

Expected: commit succeeds.

---

## Task 8: Final Verification And Metrics

**Files:**
- Read: generated profile reports under `build/reports/profile/`
- Read: generated test results under `**/build/test-results/`

- [ ] **Step 1: Run final static analysis**

Run:

```bash
/usr/bin/time -p ./gradlew spotlessCheck lint --profile --continue
```

Expected: `BUILD SUCCESSFUL`; record real time and profile path.

- [ ] **Step 2: Run final unit test suite**

Run:

```bash
/usr/bin/time -p ./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test --profile
```

Expected: `BUILD SUCCESSFUL`; record real time and profile path.

- [ ] **Step 3: Run screenshot verification**

Run:

```bash
./gradlew verifyRoborazziDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run assemble checks**

Run:

```bash
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run documentation diff check**

Run:

```bash
git diff --check -- gradle.properties .github/workflows/android.yml build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt docs/verification-matrix.md docs/superpowers/specs/2026-05-14-build-test-speed-implementation.md docs/superpowers/plans/2026-05-14-build-test-speed-improvements.md
```

Expected: no output.

- [ ] **Step 6: Summarize before/after**

In the PR description, include:

```text
Before:
- unit-test suite: 66.45s real on 2026-05-14 baseline
- spotlessCheck lint: 19.56s real on 2026-05-14 baseline
- slowest test class: GpsAvailabilityMonitorTest, 6.602s

After:
- unit-test suite: value from Task 8 Step 2 time output
- spotlessCheck lint: value from Task 8 Step 1 time output
- slowest test class: value from Task 8 test-results XML scan
- Roborazzi owner: verifyRoborazziDebug
```

Use measured numbers from Step 1 and Step 2.
