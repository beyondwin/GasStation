# GasStation Hero Benchmark Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-content startup reporting, expand GasStation hero benchmarks, generate evidence documentation, and document backend proxy escalation without changing the product feature set.

**Architecture:** Keep the current module boundaries. `feature:station-list` owns first usable content policy, `app` owns the final `reportFullyDrawn()` bridge, `benchmark` owns hero performance scenarios, and docs own measured evidence and backend proxy escalation rationale.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, AndroidX Macrobenchmark, UiAutomator, BaselineProfileRule, Room-backed demo seed path, Gradle version catalog.

---

## Scope Notes

This plan implements the approved spec at `docs/superpowers/specs/2026-05-17-gasstation-hero-benchmark-evidence-design.md`.

This is one integrated plan because the work produces one coherent outcome: a measured performance evidence path. Backend proxy work is documentation-only and does not create a server.

### Code-Review Pass (2026-05-18)

After comparing the plan to the current code state, the following corrections are folded in:

- Task 1: added a `first content is ready for stale cache with visible stations` policy test so the spec 5.3 / 8.1 stale path is exercised, not just implied by the `stations.isNotEmpty()` branch.
- Task 5: `launchStationList()` now waits for the cold-start auto-refresh rail (`가격 갱신 중`) to disappear before returning, and `refreshStationList()` reuses the same `waitForRefreshRailGone()` helper. Without this, `refreshFrameTiming` and `openWatchlistFrameTiming` measurements raced against the leftover initial refresh and gave noisy frame timing.
- Task 5: relabeled the save selector log from "first station save action" to "any visible station save action" — `findObject(By.desc("저장"))` returns an arbitrary match because every station card shares the same `contentDescription`, and the misleading "first" wording would hide future debugging of selector ambiguity.
- Task 6: the README placeholder check is now an explicit forbidden-token list (`TBD`, `xxx`, `예시`, `placeholder`, `Record the device`) and a unit requirement on every `p50`/`p95` cell, instead of the prior loose "search for sample words" instruction.
- Spec alignment: the ADR filename in spec section 5.4 was `2026-05-17-backend-proxy-escalation.md` but the plan creates `2026-05-18-backend-proxy-escalation.md`. The spec is updated to the 05-18 name to match the executable plan.
- Spec alignment: spec section 8.3 previously promised JVM-level benchmark helper tests, but the helpers depend on `MacrobenchmarkScope`/UiAutomator, so they cannot run as pure JVM tests. The spec is updated to keep the reliability guarantees as helper-structure rules (single `TARGET_PACKAGE` constant, named scenario functions, descriptive `check(...)` messages, and the refresh-rail quiescence wait) and to mark JVM extraction as a follow-up.

## File Structure

### Create

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFirstContentPolicy.kt`
  - Pure policy for deciding whether the station-list screen has reached first usable content.
- `app/src/main/java/com/gasstation/startup/StartupDrawReporter.kt`
  - App-level one-shot wrapper around `Activity.reportFullyDrawn()`.
- `app/src/test/java/com/gasstation/startup/StartupDrawReporterTest.kt`
  - JVM test for one-shot reporting behavior.
- `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt`
  - Shared package constants, permission setup, UiAutomator wait helpers, and hero journey actions.
- `docs/performance.md`
  - Hero benchmark definitions, physical-device measurement instructions, captured results, and baseline profile workflow.
- `docs/adr/2026-05-18-backend-proxy-escalation.md`
  - ADR explaining when and how to move Opinet API access behind a backend proxy.

### Modify

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  - Add `onFirstContentDrawn` callback and call it after first usable content reaches a frame.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
  - Add first-content policy tests.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
  - Add callback wiring tests.
- `app/src/main/java/com/gasstation/MainActivity.kt`
  - Create `StartupDrawReporter` and pass its callback into navigation.
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
  - Thread `onStationListFirstContentDrawn` into `StationListRoute`.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
  - Thread callback into `StationListScreen`.
- `gradle/libs.versions.toml`
  - Add ProfileInstaller dependency alias.
- `app/build.gradle.kts`
  - Add `implementation(libs.androidx.profileinstaller)`.
- `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`
  - Replace the single mixed benchmark with dedicated startup, scroll, refresh, and watchlist scenarios.
- `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
  - Reuse hero journey helpers for startup, refresh, scroll, and watchlist.
- `README.md`
  - Replace startup metric guidance with measured physical-device results after running benchmarks.
- `docs/verification-matrix.md`
  - Add physical-device hero benchmark and baseline profile commands as opt-in verification.
- `docs/test-strategy.md`
  - Add first-content and hero benchmark reliability coverage.
- `docs/architecture.md`
  - Add a short note that `app` owns only the final fully drawn signal bridge.
- `docs/security-trade-offs.md`
  - Link to the backend proxy escalation ADR.
- `CHANGELOG.md`
  - Add an unreleased entry for the performance evidence pass.

---

### Task 1: First Usable Content Policy

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFirstContentPolicy.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`

- [ ] **Step 1: Write the failing policy tests**

Append these tests inside `StationListRoutePolicyTest`.

```kotlin
    @Test
    fun `first content waits while permission is required`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.Denied,
                hasDeniedLocationAccess = false,
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content waits while gps is disabled`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isGpsEnabled = false,
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content waits during initial loading without cached stations`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready when a station card is visible`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = listOf(testStationUiModel()),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready for a settled successful empty result`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = false,
                isRefreshing = false,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content waits for empty results while refresh is still active`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isRefreshing = true,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready for blocking failure guidance`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                blockingFailure = StationListFailureReason.RefreshFailed,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready for stale cache with visible stations`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.ApproximateGranted,
                isStale = true,
                isRefreshing = true,
                stations = listOf(testStationUiModel()),
            ).hasFirstUsableContent(),
        )
    }
```

Add this helper at the bottom of `StationListRoutePolicyTest`.

```kotlin
private fun testStationUiModel() = StationListItemUiModel(
    id = "station-1",
    name = "테스트 주유소",
    brand = com.gasstation.core.model.Brand.GSC,
    brandLabel = "GS칼텍스",
    priceLabel = "1,689원",
    distanceLabel = "0.3km",
    priceNumberLabel = "1,689",
    priceUnitLabel = "원",
    distanceNumberLabel = "0.3",
    distanceUnitLabel = "km",
    priceDeltaLabel = "-",
    isWatched = false,
    latitude = 37.498095,
    longitude = 127.02761,
)
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListRoutePolicyTest
```

Expected: fails to compile with `Unresolved reference: hasFirstUsableContent`.

- [ ] **Step 3: Implement the policy**

Create `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFirstContentPolicy.kt`.

```kotlin
package com.gasstation.feature.stationlist

internal fun StationListUiState.hasFirstUsableContent(): Boolean = when (toBodyState()) {
    StationListBodyState.PermissionRequired,
    StationListBodyState.GpsRequired,
    StationListBodyState.InitialLoading,
    -> false

    is StationListBodyState.Failure -> true

    StationListBodyState.Results -> stations.isNotEmpty() || (!isLoading && !isRefreshing)
}
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListRoutePolicyTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFirstContentPolicy.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt
git commit -m "feat: define station list first content policy"
```

---

### Task 2: App-Level Fully Drawn Reporter

**Files:**
- Create: `app/src/main/java/com/gasstation/startup/StartupDrawReporter.kt`
- Create: `app/src/test/java/com/gasstation/startup/StartupDrawReporterTest.kt`

- [ ] **Step 1: Write the failing reporter test**

Create `app/src/test/java/com/gasstation/startup/StartupDrawReporterTest.kt`.

```kotlin
package com.gasstation.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDrawReporterTest {
    @Test
    fun `reporter calls fully drawn callback once`() {
        var reportCount = 0
        val reporter = StartupDrawReporter {
            reportCount += 1
        }

        reporter.reportFirstContentDrawn()
        reporter.reportFirstContentDrawn()
        reporter.reportFirstContentDrawn()

        assertEquals(1, reportCount)
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests com.gasstation.startup.StartupDrawReporterTest
```

Expected: fails to compile with `Unresolved reference: StartupDrawReporter`.

- [ ] **Step 3: Implement the reporter**

Create `app/src/main/java/com/gasstation/startup/StartupDrawReporter.kt`.

```kotlin
package com.gasstation.startup

class StartupDrawReporter(private val reportFullyDrawn: () -> Unit) {
    private var reported = false

    fun reportFirstContentDrawn() {
        if (reported) return
        reported = true
        reportFullyDrawn()
    }
}
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests com.gasstation.startup.StartupDrawReporterTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/java/com/gasstation/startup/StartupDrawReporter.kt \
  app/src/test/java/com/gasstation/startup/StartupDrawReporterTest.kt
git commit -m "feat: add startup fully drawn reporter"
```

---

### Task 3: Wire First Content To `reportFullyDrawn()`

**Files:**
- Modify: `app/src/main/java/com/gasstation/MainActivity.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [ ] **Step 1: Write the failing screen callback tests**

Add these imports to `StationListScreenTest`.

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

Append these tests inside `StationListScreenTest`.

```kotlin
    @Test
    fun `first content callback waits during initial loading`() {
        var callbackCount = 0

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    stations = emptyList(),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
                onFirstContentDrawn = { callbackCount += 1 },
            )
        }

        composeRule.waitForIdle()

        assertEquals(0, callbackCount)
    }

    @Test
    fun `first content callback fires once after usable station content appears`() {
        var callbackCount = 0
        var uiState by mutableStateOf(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = emptyList(),
                selectedFuelType = FuelType.GASOLINE,
            ),
        )

        composeRule.setContent {
            StationListScreen(
                uiState = uiState,
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
                onFirstContentDrawn = { callbackCount += 1 },
            )
        }

        composeRule.waitForIdle()
        assertEquals(0, callbackCount)

        composeRule.runOnUiThread {
            uiState = uiState.copy(
                isLoading = false,
                stations = listOf(testStation()),
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            uiState = uiState.copy(isRefreshing = true)
        }
        composeRule.waitForIdle()

        assertEquals(1, callbackCount)
    }
```

- [ ] **Step 2: Run the failing screen tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest
```

Expected: fails to compile because `StationListScreen` does not have an `onFirstContentDrawn` parameter.

- [ ] **Step 3: Add callback support to `StationListScreen`**

In `StationListScreen.kt`, add these imports.

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
```

Update the `StationListScreen` signature.

```kotlin
fun StationListScreen(
    uiState: StationListUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (StationListAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: (() -> Unit)? = null,
    onFirstContentDrawn: () -> Unit = {},
)
```

Add this block immediately after `val bookmarkLabel = stringResource(R.string.station_list_action_bookmark)`.

```kotlin
    val currentOnFirstContentDrawn by rememberUpdatedState(onFirstContentDrawn)
    val hasFirstUsableContent = uiState.hasFirstUsableContent()

    LaunchedEffect(hasFirstUsableContent) {
        if (hasFirstUsableContent) {
            withFrameNanos { }
            currentOnFirstContentDrawn()
        }
    }
```

- [ ] **Step 4: Thread the callback through route and nav host**

Update `StationListRoute` signature.

```kotlin
fun StationListRoute(
    onSettingsClick: () -> Unit,
    onWatchlistClick: (Coordinates) -> Unit,
    onOpenExternalMap: (StationListEffect.OpenExternalMap) -> Unit,
    onFirstContentDrawn: () -> Unit = {},
    viewModel: StationListViewModel = hiltViewModel(),
)
```

Pass the callback to `StationListScreen`.

```kotlin
    StationListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onRequestPermissions = { permissionState.launchMultiplePermissionRequest() },
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        onSettingsClick = onSettingsClick,
        onWatchlistClick = uiState.watchlistCoordinatesOrNull()?.let { coordinates ->
            { onWatchlistClick(coordinates) }
        },
        onFirstContentDrawn = onFirstContentDrawn,
    )
```

Update `GasStationNavHost` signature.

```kotlin
fun GasStationNavHost(
    externalMapLauncher: ExternalMapLauncher,
    onStationListFirstContentDrawn: () -> Unit = {},
)
```

Pass it into `StationListRoute`.

```kotlin
            StationListRoute(
                onSettingsClick = { navController.navigate(GasStationDestination.Settings.route) },
                onWatchlistClick = { coordinates ->
                    navController.navigate(GasStationDestination.Watchlist.createRoute(coordinates))
                },
                onOpenExternalMap = { effect ->
                    externalMapLauncher.open(
                        provider = effect.provider,
                        stationName = effect.stationName,
                        originLatitude = effect.originLatitude,
                        originLongitude = effect.originLongitude,
                        latitude = effect.latitude,
                        longitude = effect.longitude,
                    )
                },
                onFirstContentDrawn = onStationListFirstContentDrawn,
            )
```

Update `MainActivity` imports.

```kotlin
import com.gasstation.startup.StartupDrawReporter
```

Add the reporter property inside `MainActivity`.

```kotlin
    private val startupDrawReporter = StartupDrawReporter(::reportFullyDrawn)
```

Pass it into `GasStationNavHost`.

```kotlin
                GasStationNavHost(
                    externalMapLauncher = externalMapLauncher,
                    onStationListFirstContentDrawn = startupDrawReporter::reportFirstContentDrawn,
                )
```

- [ ] **Step 5: Run the focused feature and app tests**

Run:

```bash
./gradlew \
  :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest \
  :app:testDemoDebugUnitTest --tests com.gasstation.startup.StartupDrawReporterTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run app assemble to verify navigation signature wiring**

Run:

```bash
./gradlew :app:assembleDemoDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add \
  app/src/main/java/com/gasstation/MainActivity.kt \
  app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt
git commit -m "feat: report fully drawn after station list content"
```

---

### Task 4: Add ProfileInstaller Runtime Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version catalog entries**

In `gradle/libs.versions.toml`, add this version near the AndroidX versions.

```toml
profileInstaller = "1.4.1"
```

Add this library alias near the AndroidX library entries.

```toml
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileInstaller" }
```

- [ ] **Step 2: Add app dependency**

In `app/build.gradle.kts`, add this dependency inside `dependencies`.

```kotlin
    implementation(libs.androidx.profileinstaller)
```

- [ ] **Step 3: Run assemble**

Run:

```bash
./gradlew :app:assembleDemoDebug :benchmark:assemble
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: include profile installer runtime"
```

---

### Task 5: Expand Hero Benchmarks

**Files:**
- Create: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt`
- Modify: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`
- Modify: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`

- [ ] **Step 1: Create shared benchmark actions**

Create `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt`.

```kotlin
package com.gasstation.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.gasstation.demo"

private const val WAIT_TIMEOUT_MS = 5_000L
private const val COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"
private const val FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
private const val STATION_TEXT_FRAGMENT = "주유소"
private const val REFRESH_ACTION_DESCRIPTION = "새로고침"
private const val BOOKMARK_ACTION_DESCRIPTION = "북마크"
private const val SAVE_ACTION_DESCRIPTION = "저장"
private const val WATCHLIST_CARD_DESCRIPTION = "관심 주유소 카드"
private const val REFRESH_RAIL_TITLE = "가격 갱신 중"

internal fun MacrobenchmarkScope.launchStationList() {
    grantLocationPermissions()
    pressHome()
    startActivityAndWait()
    waitForStationListContent()
    waitForRefreshRailGone()
}

internal fun MacrobenchmarkScope.waitForRefreshRailGone() {
    device.wait(Until.gone(By.text(REFRESH_RAIL_TITLE)), WAIT_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.grantLocationPermissions() {
    device.executeShellCommand("pm grant $TARGET_PACKAGE $COARSE_LOCATION_PERMISSION")
    device.executeShellCommand("pm grant $TARGET_PACKAGE $FINE_LOCATION_PERMISSION")
}

internal fun MacrobenchmarkScope.waitForStationListContent(): UiObject2 =
    waitForObject(
        selector = By.textContains(STATION_TEXT_FRAGMENT),
        label = "station list content containing '$STATION_TEXT_FRAGMENT'",
    )

internal fun MacrobenchmarkScope.refreshStationList() {
    waitForObject(
        selector = By.desc(REFRESH_ACTION_DESCRIPTION),
        label = "refresh action '$REFRESH_ACTION_DESCRIPTION'",
    ).click()
    waitForRefreshRailGone()
    waitForStationListContent()
}

internal fun MacrobenchmarkScope.scrollStationList() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.28f).toInt(),
        16,
    )
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openWatchlistWithSavedStation() {
    waitForObject(
        selector = By.desc(SAVE_ACTION_DESCRIPTION),
        label = "any visible station save action '$SAVE_ACTION_DESCRIPTION'",
    ).click()
    waitForObject(
        selector = By.desc(BOOKMARK_ACTION_DESCRIPTION),
        label = "watchlist action '$BOOKMARK_ACTION_DESCRIPTION'",
    ).click()
    waitForObject(
        selector = By.desc(WATCHLIST_CARD_DESCRIPTION),
        label = "watchlist card '$WATCHLIST_CARD_DESCRIPTION'",
    )
}

private fun MacrobenchmarkScope.waitForObject(selector: BySelector, label: String): UiObject2 {
    check(device.wait(Until.hasObject(selector), WAIT_TIMEOUT_MS)) {
        "Timed out after ${WAIT_TIMEOUT_MS}ms waiting for $label"
    }
    return requireNotNull(device.findObject(selector)) {
        "UiAutomator reported $label but findObject returned null"
    }
}
```

- [ ] **Step 2: Replace benchmark scenarios**

Replace `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt` with:

```kotlin
package com.gasstation.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationListBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupToFirstContent() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = {
            grantLocationPermissions()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForStationListContent()
    }

    @Test
    fun listScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        setupBlock = {
            launchStationList()
        },
    ) {
        scrollStationList()
    }

    @Test
    fun refreshFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        setupBlock = {
            launchStationList()
        },
    ) {
        refreshStationList()
    }

    @Test
    fun openWatchlistFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        setupBlock = {
            launchStationList()
        },
    ) {
        openWatchlistWithSavedStation()
    }
}
```

- [ ] **Step 3: Reuse helpers in baseline profile generation**

Replace `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt` with:

```kotlin
package com.gasstation.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun collectHeroJourney() = rule.collect(
        packageName = TARGET_PACKAGE,
    ) {
        grantLocationPermissions()
        pressHome()
        startActivityAndWait()
        waitForStationListContent()
        refreshStationList()
        scrollStationList()
        openWatchlistWithSavedStation()
    }
}
```

- [ ] **Step 4: Run benchmark assemble**

Run:

```bash
./gradlew :benchmark:assemble
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run physical-device benchmark**

Run on a connected physical device with sufficient battery:

```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL` and benchmark output under `benchmark/build/outputs/connected_android_test_additional_output`.

- [ ] **Step 6: Locate benchmark JSON and trace outputs**

Run:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
find benchmark/build/outputs/connected_android_test_additional_output -name '*.perfetto-trace' -print
```

Expected: at least one benchmark JSON file and one Perfetto trace path are printed.

- [ ] **Step 7: Commit benchmark changes**

```bash
git add \
  benchmark/src/androidTest/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt \
  benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt \
  benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt
git commit -m "test: expand gasstation hero benchmarks"
```

---

### Task 6: Performance Evidence Documentation

**Files:**
- Create: `docs/performance.md`
- Modify: `README.md`
- Modify: `docs/verification-matrix.md`

- [ ] **Step 1: Create the performance document after the physical-device benchmark run**

Use the measured values from the successful Task 5 physical-device run. Create `docs/performance.md` with this structure and concrete measured values from the benchmark output.

````markdown
# Performance

GasStation measures performance through the deterministic `demo` flavor. The `prod` flavor is not used for committed performance numbers because real server, network, and live location state would make results environment-dependent.

## Hero Journeys

| Journey | What It Measures | Metric |
| --- | --- | --- |
| Startup to first content | Cold app launch until the first usable station-list content is visible and `reportFullyDrawn()` is reached | `StartupTimingMetric` |
| List scroll | Frame stability while scrolling the price-first station list | `FrameTimingMetric` |
| Refresh | Frame stability while refreshing seeded nearby station data | `FrameTimingMetric` |
| Open watchlist | Frame stability while saving a station and opening the watchlist comparison screen | `FrameTimingMetric` |

## Latest Physical Device Run

Record the device model, Android version, build variant, iteration count, and measurement date exactly as used for the benchmark run. Use the JSON file paths printed by:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
```

## Results

Write a results table with one row per benchmark method. Use the metric names emitted by Macrobenchmark. Do not commit emulator numbers as README performance evidence.

## Baseline Profile Journey

The baseline profile generator covers:

- App startup
- First station-list content
- Seeded refresh
- Station-list scroll
- Watchlist entry after saving a station

## Commands

```bash
./gradlew :benchmark:assemble
./gradlew :benchmark:connectedDebugAndroidTest
```

## Result Interpretation

- Startup numbers are used to replace the README startup metric table.
- Frame timing numbers are used to explain scroll, refresh, and watchlist smoothness.
- Perfetto traces are local diagnostic artifacts and are not committed unless a future investigation needs a small excerpt or screenshot.
````

Then replace the "Latest Physical Device Run" and "Results" prose with the concrete measured table from the successful run before committing this task. If no physical device run is available, stop this task and report the blocker instead of committing a performance document with invented numbers.

- [ ] **Step 2: Update README startup metric section**

Replace the current `Startup metric (참고)` section with a concise physical-device summary. Use the same measured values from `docs/performance.md`.

Required README structure:

- Heading: `## Performance Snapshot`
- First paragraph: explain that GasStation uses deterministic `demo` flavor hero benchmarks and physical-device numbers only.
- Table columns: `Hero journey`, `Primary metric`, `p50`, `p95`
- Required row: `Startup to first content`, `startup`, followed by the measured p50 and p95 values from the physical-device run.
- Final sentence: link to `[Performance](docs/performance.md)` for scenario definitions, device information, and benchmark commands.
- Before committing, grep the README performance section for placeholder tokens and confirm none remain. The forbidden tokens are: `TBD`, `xxx`, `예시`, `placeholder`, and `Record the device`. Every `p50` and `p95` cell must contain a measured number with millisecond unit (for startup) or an ms/frame unit (for frame timing).

- [ ] **Step 3: Update verification matrix**

In `docs/verification-matrix.md`, add this section near the existing performance/benchmark section.

````markdown
## Hero Benchmark Evidence

Hero benchmarks require a physical device for committed performance numbers. Emulator runs are allowed only as smoke checks.

```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

After a successful run, inspect generated JSON and trace artifacts:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
find benchmark/build/outputs/connected_android_test_additional_output -name '*.perfetto-trace' -print
```

Do not add this command to the default PR gate. It depends on a connected physical device and is part of release or portfolio evidence collection.
````

- [ ] **Step 4: Run document checks**

Run:

```bash
git diff --check -- README.md docs/performance.md docs/verification-matrix.md
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/performance.md docs/verification-matrix.md
git commit -m "docs: publish hero benchmark evidence"
```

---

### Task 7: Backend Proxy Escalation ADR

**Files:**
- Create: `docs/adr/2026-05-18-backend-proxy-escalation.md`
- Modify: `docs/security-trade-offs.md`
- Modify: `docs/project-reading-guide.md`

- [ ] **Step 1: Create ADR directory and document**

Create `docs/adr/2026-05-18-backend-proxy-escalation.md`.

```markdown
# ADR: Backend Proxy Escalation for Opinet API Access

Date: 2026-05-18

## Status

Accepted as a future escalation path. Not implemented in the current Android app.

## Context

GasStation currently injects the Opinet API key into the Android client through Gradle property `opinet.apikey` and `BuildConfig.OPINET_API_KEY`. This keeps the Android app simple and supports the current portfolio scope, but it is not a server-side secret boundary. A released APK can be inspected and the key can be extracted.

Opinet returns public gas station price data. The current risk is quota exhaustion and key abuse, not exposure of private user data. The app also whitelists cleartext HTTP only for `www.opinet.co.kr` because the upstream API endpoint does not provide HTTPS.

## Decision

Do not implement a backend proxy in the current Android-focused scope. Document the conditions that require escalation and keep Android module contracts ready for an endpoint swap.

## Escalation Conditions

Move Opinet access behind a backend proxy when any of these becomes true:

- The app is publicly distributed beyond portfolio or controlled demo use.
- API quota cost or abuse risk becomes material.
- Key rotation or revocation must happen without shipping a new Android build.
- Monitoring must alert on unusual traffic patterns.
- The upstream API begins carrying data with higher sensitivity than public station prices.
- The product needs server-side caching, normalization, or policy enforcement.

## Target Proxy Responsibilities

The proxy owns:

- Opinet API key storage and rotation
- HTTPS edge exposed to Android clients
- Request rate limiting
- Opinet HTTP cleartext interaction inside the server boundary
- Response normalization for app-ready station payloads
- Optional short-lived server-side cache
- Metrics and alerting for quota and upstream errors

The Android app keeps:

- Location permission and current-coordinate acquisition
- Local Room cache and stale fallback behavior
- User settings and watchlist state
- UI state, retry presentation, and external map handoff
- Domain contracts for station search and refresh

## Android Code Impact

The expected Android change is limited to `core:network` runtime configuration and the remote station source. `feature:*`, `domain:*`, `data:station` cache policy, and `core:database` schema should not need product-level rewrites.

The endpoint swap should preserve:

- `StationQuery`
- `StationRepository`
- `StationRefreshException`
- `StationSearchResult`
- demo seed behavior

## Consequences

Current app remains focused on Android architecture and performance evidence. Future public deployment has a documented security path that does not require reworking feature or domain contracts.
```

- [ ] **Step 2: Link ADR from security trade-offs**

Append this section to `docs/security-trade-offs.md`.

```markdown
## Backend Proxy Escalation

Backend proxy implementation is not part of the current Android-focused scope. The accepted escalation path is documented in [`docs/adr/2026-05-18-backend-proxy-escalation.md`](adr/2026-05-18-backend-proxy-escalation.md).
```

- [ ] **Step 3: Link ADR directory from project reading guide**

In `docs/project-reading-guide.md`, add this bullet near the docs map language.

```markdown
- `docs/adr/`: Architecture decision records for accepted trade-offs and future escalation paths.
```

- [ ] **Step 4: Run document checks**

Run:

```bash
git diff --check -- docs/adr/2026-05-18-backend-proxy-escalation.md docs/security-trade-offs.md docs/project-reading-guide.md
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add docs/adr/2026-05-18-backend-proxy-escalation.md docs/security-trade-offs.md docs/project-reading-guide.md
git commit -m "docs: record backend proxy escalation path"
```

---

### Task 8: Architecture And Test Strategy Documentation

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/test-strategy.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update architecture startup flow**

In `docs/architecture.md`, add this paragraph to the runtime flow section for the station list.

```markdown
첫 usable content가 렌더링되면 `feature:station-list`가 순수 policy로 이 상태를 판단하고, `app`의 Compose host가 그 신호를 받아 `reportFullyDrawn()`을 한 번 호출합니다. 이 연결은 startup metric 보고용이며, 검색 정책이나 cache/stale 판단은 계속 feature/data/domain 경계에 남습니다.
```

- [ ] **Step 2: Update test strategy table**

In `docs/test-strategy.md`, extend the `feature:station-list` row to mention first usable content policy.

Use this row text:

```markdown
| `feature:station-list` | `feature:station-list/LocationStateMachineTest`, `feature:station-list/StationSearchOrchestratorTest`, `StationListViewModelTest`, `StationListScreenTest`, `StationListRoutePolicyTest`, `StationListBannerModelTest`, `StationListItemUiModelTest`, `GpsAvailabilityMonitorTest` | 위치 상태 전이, query/cache/failure orchestration, extraction 이후 UI state composition/effect/action dispatch, stale/approximate guidance, 주소 컨텍스트 표시, 가격 우선 카드, 긴 역명/가격/유종 clipping 방지, route lifecycle 기반 availability 관찰과 권한/GPS recovery, first usable content 기준 |
```

Add this bullet under the regression-risk section.

```markdown
- First usable content policy
  Startup metric은 첫 frame이 아니라 사용 가능한 목록/empty/failure content 기준으로 보고합니다. `StationListFirstContentPolicy`와 `StartupDrawReporter` 테스트가 이 기준을 보호합니다.
```

- [ ] **Step 3: Add changelog entry**

At the top of `CHANGELOG.md`, above `## 1.1.2 - 2026-05-14`, add:

```markdown
## Unreleased

### 개발자 영향

- Hero benchmark evidence: station-list first usable content 기준으로 `reportFullyDrawn()`을 연결하고, startup/list scroll/refresh/watchlist macrobenchmark 경로를 분리합니다.
- Baseline profile: 앱 시작, 목록 표시, refresh, watchlist 진입을 포함하는 baseline profile journey를 문서화합니다.
- Security operations: Opinet API key를 backend proxy로 승격해야 하는 조건과 Android 영향 범위를 ADR로 기록합니다.

### 문서와 검증

- README와 `docs/performance.md`는 실기기 hero benchmark 결과를 기준으로 성능 증거를 설명합니다.
- `docs/verification-matrix.md`는 physical-device benchmark를 PR gate가 아닌 opt-in evidence collection으로 분리합니다.
```

- [ ] **Step 4: Run document checks**

Run:

```bash
git diff --check -- docs/architecture.md docs/test-strategy.md CHANGELOG.md
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add docs/architecture.md docs/test-strategy.md CHANGELOG.md
git commit -m "docs: document startup performance evidence flow"
```

---

### Task 9: Final Verification

**Files:**
- Verify all modified files

- [ ] **Step 1: Run fast verification**

Run:

```bash
./gradlew \
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :benchmark:assemble
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run screenshot regression**

Run:

```bash
./gradlew verifyRoborazziDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run physical-device benchmark once more**

Run on a connected physical device:

```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL` and benchmark JSON files exist under `benchmark/build/outputs/connected_android_test_additional_output`.

- [ ] **Step 4: Check working tree**

Run:

```bash
git status --short
```

Expected: no output.

- [ ] **Step 5: Record final verification in PR summary**

Use this summary shape in the PR body or final handoff:

```markdown
Verification:
- `./gradlew :core:model:test :domain:location:test :core:observability:test :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDemoDebug :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :benchmark:assemble`
- `./gradlew verifyRoborazziDebug`
- `./gradlew :benchmark:connectedDebugAndroidTest` on physical device
```
