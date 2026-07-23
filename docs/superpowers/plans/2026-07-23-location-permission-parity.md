# Location Permission Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make denied permission a hard gate in both demo and prod so demo fixed coordinates are available only after a real grant and the permission screen never flashes into content.

**Architecture:** Enforce permission at two boundaries: `core:location` rejects denied requests before flavor overrides, and `feature:station-list` refuses acquisition/query/rendering while denied. Remove the demo-only denied-coordinate state, keep the OS dialog user-initiated, and add an explicit app-settings fallback policy after repeated denial.

**Tech Stack:** Kotlin, Coroutines/Flow, Accompanist Permissions, Jetpack Compose, Hilt, Robolectric, Android instrumentation, UI Automator

## Global Constraints

- Preserve `feature:station-list -> domain:location -> core:location`.
- `DemoLocationOverride` remains an internal `core:location` implementation detail.
- Denied permission must prevent location acquisition, `StationQuery`, refresh, watchlist coordinates, and Nearby content in both flavors.
- The Android permission dialog opens only after the user presses the permission action.
- After grant, demo uses the approved fixed Gangnam Station Exit 2 coordinates; prod uses Android foreground location.
- Keep GPS-disabled guidance separate from permission guidance.
- Do not change cache, stale, retry, seed, or preference-reset policy.
- Preserve stable Compose test tags and accessibility semantics.
- Plan 1 (`2026-07-23-settings-state-integrity.md`) is applied first; this plan consumes `StationListUiState.preferences`.
- Preserve the user's existing `settings.gradle.kts` and `gradle/gradle-daemon-jvm.properties` changes.

---

## File Structure

- Modify `core:location` provider and override contracts for permission-first resolution.
- Remove `hasDeniedLocationAccess` from station-list state, ViewModel projection, body policy, route policy, and tests.
- Add a small pure permission-action policy in `StationListRoutePolicy.kt`.
- Extend permission guidance with an app-settings action after repeated denial.
- Add a dedicated demo connected permission class that starts with permissions revoked.
- Update live architecture, state, test, verification, and README contracts.

---

### Task 1: Reject Denied Permission Before Demo Override

**Files:**
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/DemoLocationOverride.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt`
- Modify: `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- Modify: `core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderSurfaceTest.kt`
- Modify: `core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderTest.kt`
- Modify: `app/src/demoAndroidTest/kotlin/com/gasstation/DemoLocationHookIntegrationTest.kt`

**Interfaces:**
- Consumes: domain/core `LocationPermissionState`.
- Produces: `DemoLocationOverride.currentLocation(): Coordinates?`.
- Produces: denied permission always returns `LocationLookupResult.PermissionDenied`.

- [ ] **Step 1: Replace the existing bypass tests with permission-first RED tests**

In `AndroidForegroundLocationProviderSurfaceTest`, replace `demo override wins before denied permission result` with:

```kotlin
@Test
fun `denied permission wins before demo override`() = runBlocking {
    val provider = AndroidForegroundLocationProvider(
        context = ContextWrapper(null),
        demoLocationOverride = Optional.of(
            DemoLocationOverride {
                throw AssertionError("Demo override must not run while permission is denied")
            },
        ),
        currentLocationClient = unusedCurrentLocationClient(),
    )

    assertEquals(
        LocationLookupResult.PermissionDenied,
        provider.currentLocation(LocationPermissionState.Denied),
    )
}
```

Add:

```kotlin
@Test
fun `granted permission uses demo override without Android client`() = runBlocking {
    val expected = Coordinates(37.497927, 127.027583)
    val provider = AndroidForegroundLocationProvider(
        context = ContextWrapper(null),
        demoLocationOverride = Optional.of(DemoLocationOverride { expected }),
        currentLocationClient = unusedCurrentLocationClient(),
    )

    assertEquals(
        LocationLookupResult.Success(expected),
        provider.currentLocation(LocationPermissionState.PreciseGranted),
    )
}
```

In `DemoLocationHookIntegrationTest`, change the denied assertion to:

```kotlin
assertEquals(
    LocationLookupResult.PermissionDenied,
    foregroundLocationProvider.currentLocation(LocationPermissionState.Denied),
)
```

- [ ] **Step 2: Run the focused tests and capture RED**

Run:

```bash
./gradlew \
  :core:location:testDebugUnitTest \
  :app:compileDemoDebugAndroidTestKotlin \
  --warning-mode fail
```

Expected: compilation fails after the zero-argument override test is added, or the denied assertion fails against the current override-first implementation.

- [ ] **Step 3: Simplify the override contract and reorder the provider**

Use:

```kotlin
fun interface DemoLocationOverride {
    fun currentLocation(): Coordinates?
}
```

Keep the demo binding:

```kotlin
@Provides
fun provideDemoLocationOverride(): DemoLocationOverride =
    DemoLocationOverride { DemoSeedOrigin.coordinates }
```

At the beginning of `AndroidForegroundLocationProvider.currentLocation`:

```kotlin
if (permissionState == LocationPermissionState.Denied) {
    return LocationLookupResult.PermissionDenied
}

if (demoLocationOverride.isPresent) {
    return demoLocationOverride.get().currentLocation()
        ?.let(LocationLookupResult::Success)
        ?: LocationLookupResult.Unavailable
}
```

The priority `when` retains only granted branches plus the exhaustive denied return.

- [ ] **Step 4: Run location and demo graph tests**

Run:

```bash
./gradlew \
  :core:location:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:compileDemoDebugAndroidTestKotlin \
  --warning-mode fail
```

Expected: PASS.

- [ ] **Step 5: Commit the core permission gate**

```bash
git add core/location app/src/demo app/src/demoAndroidTest
git commit -m "fix: gate demo location behind permission"
```

### Task 2: Remove Denied-Coordinate Bypass From Station List

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBodyState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFirstContentPolicy.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

**Interfaces:**
- Consumes: Task 1 permission-first provider.
- Consumes: Plan 1 `StationListUiState.preferences`.
- Produces: `LocationState` without `hasDeniedLocationAccess`.
- Produces: route auto-refresh requires granted permission.

- [ ] **Step 1: Write station-list permission hard-gate tests**

Add to `LocationStateMachineTest`:

```kotlin
@Test
fun `denied permission clears retained coordinates and never calls repository`() = runTest {
    val repository = RecordingPermissionLocationRepository()
    val machine = createMachine(repository)
    machine.onPermissionChanged(LocationPermissionState.PreciseGranted)
    assertTrue(machine.acquireLocation() is LocationAcquisitionResult.Success)

    machine.onPermissionChanged(LocationPermissionState.Denied)
    val result = machine.acquireLocation()

    assertEquals(LocationAcquisitionResult.PermissionDenied, result)
    assertNull(machine.state.value.currentCoordinates)
    assertEquals(1, repository.locationRequests)
}
```

Add a fake that increments `locationRequests` and returns success.

Replace denied-demo route tests with:

```kotlin
@Test
fun `auto refresh is skipped while permission is denied`() {
    assertFalse(
        StationListUiState(
            preferences = UserPreferences.default(),
            isAvailabilityKnown = true,
            isGpsEnabled = true,
            permissionState = LocationPermissionState.Denied,
            currentCoordinates = null,
        ).shouldAutoRefreshOnRoute(),
    )
}
```

```kotlin
@Test
fun `watchlist coordinates are hidden whenever permission is denied`() {
    assertNull(
        StationListUiState(
            preferences = UserPreferences.default(),
            currentCoordinates = coordinates,
            isGpsEnabled = true,
            permissionState = LocationPermissionState.Denied,
        ).watchlistCoordinatesOrNull(),
    )
}
```

Add to `StationListViewModelTest`:

```kotlin
@Test
fun `denied auto refresh never asks location or station repository`() = runTest(dispatcher) {
    var locationRequests = 0
    val stationRepository = FakeStationRepository(emptySearchResult())
    val viewModel = stationListViewModel(
        repository = stationRepository,
        settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
        locationRepository = FakeLocationRepository(
            resultForPermission = {
                locationRequests += 1
                LocationLookupResult.Success(Coordinates(37.497927, 127.027583))
            },
        ),
    )

    viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
    viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
    viewModel.onAction(StationListAction.AutoRefreshRequested)
    advanceUntilIdle()

    assertEquals(0, locationRequests)
    assertTrue(stationRepository.refreshedQueries.isEmpty())
    assertEquals(StationListBodyState.PermissionRequired, viewModel.uiState.value.toBodyState())
}
```

- [ ] **Step 2: Run focused station-list tests and capture RED**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest \
  --tests '*LocationStateMachineTest*denied permission clears*' \
  --tests '*StationListRoutePolicyTest*permission is denied*' \
  --tests '*StationListViewModelTest*denied auto refresh never*'
```

Expected: tests fail because the denied-access bypass and provider call still exist.

- [ ] **Step 3: Remove the bypass state and enforce permission in feature code**

Remove `hasDeniedLocationAccess` from `LocationState`, `StationListUiState`, all copies, body policy, route policy, first-content policy, and tests.

In `LocationStateMachine.onPermissionChanged`:

```kotlin
fun onPermissionChanged(permissionState: LocationPermissionState) {
    mutableState.update { current ->
        if (permissionState == LocationPermissionState.Denied) {
            current.copy(
                permissionState = permissionState,
                currentCoordinates = null,
                currentAddressLabel = null,
                needsRecoveryRefresh = false,
            )
        } else {
            current.withLocationRecoveryState(permissionState = permissionState)
        }
    }
}
```

At the top of `acquireLocation`:

```kotlin
if (state.value.permissionState == LocationPermissionState.Denied) {
    return LocationAcquisitionResult.PermissionDenied
}
```

On success store coordinates without denied flags.

Use:

```kotlin
private fun LocationState.isLocationUsable(): Boolean =
    isGpsEnabled && permissionState != LocationPermissionState.Denied
```

Use:

```kotlin
internal fun StationListUiState.shouldAutoRefreshOnRoute(): Boolean =
    preferences != null &&
        permissionState != LocationPermissionState.Denied &&
        isAvailabilityKnown &&
        isGpsEnabled &&
        (currentCoordinates == null || needsRecoveryRefresh)
```

Use:

```kotlin
internal fun StationListUiState.watchlistCoordinatesOrNull(): Coordinates? =
    currentCoordinates?.takeIf {
        permissionState != LocationPermissionState.Denied && isGpsEnabled
    }
```

In `StationListViewModel.refresh`, order guards exactly:

```kotlin
val location = locationStateMachine.state.value
if (location.permissionState == LocationPermissionState.Denied) {
    if (showPermissionDeniedFeedback) {
        mutableEffects.emit(
            StationListEffect.ShowSnackbar(
                StringResource.fromId(R.string.station_list_permission_denied),
            ),
        )
    }
    return@launch
}
val preferences = readyPreferencesOrNull() ?: return@launch
if (!location.isGpsEnabled) {
    mutableEffects.emit(StationListEffect.OpenLocationSettings)
    return@launch
}
```

Use `preferences` when building the query. `usableCoordinates` requires granted permission and GPS.

Delete tests that assert denied demo coordinates remain visible, denied bypass recovery, or denied refresh success. Replace them with the hard-gate tests above.

- [ ] **Step 4: Run the full station-list suite**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --warning-mode fail
```

Expected: PASS.

- [ ] **Step 5: Commit bypass removal**

```bash
git add feature/station-list
git commit -m "fix: remove denied location bypass"
```

### Task 3: Add Stable Permission Request And App-Settings UX

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStates.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

**Interfaces:**
- Produces: pure `PermissionAction` policy.
- Produces: `Permission request` for initial/retryable denial and `Open app settings` after repeated non-rationale denial.

- [ ] **Step 1: Write failing pure policy tests**

Add:

```kotlin
@Test
fun `permission action requests initially and opens settings after repeated terminal denial`() {
    assertEquals(
        PermissionAction.Request,
        permissionAction(deniedRequestCount = 0, shouldShowRationale = false),
    )
    assertEquals(
        PermissionAction.Request,
        permissionAction(deniedRequestCount = 1, shouldShowRationale = false),
    )
    assertEquals(
        PermissionAction.Request,
        permissionAction(deniedRequestCount = 2, shouldShowRationale = true),
    )
    assertEquals(
        PermissionAction.OpenAppSettings,
        permissionAction(deniedRequestCount = 2, shouldShowRationale = false),
    )
}
```

Add a Compose test that passes `PermissionAction.OpenAppSettings`, asserts the `앱 설정에서 허용` copy, clicks, and records the callback.

- [ ] **Step 2: Run policy and screen tests and capture RED**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest \
  --tests '*StationListRoutePolicyTest*permission action*' \
  --tests '*StationListScreenTest*앱 설정에서 허용*'
```

Expected: compilation fails because the policy and screen parameter do not exist.

- [ ] **Step 3: Implement the route-local action policy**

In `StationListRoutePolicy.kt`:

```kotlin
internal enum class PermissionAction {
    Request,
    OpenAppSettings,
}

internal fun permissionAction(
    deniedRequestCount: Int,
    shouldShowRationale: Boolean,
): PermissionAction =
    if (deniedRequestCount >= 2 && !shouldShowRationale) {
        PermissionAction.OpenAppSettings
    } else {
        PermissionAction.Request
    }
```

In `StationListRoute`, remember denial count and receive permission results:

```kotlin
var deniedRequestCount by rememberSaveable { mutableIntStateOf(0) }
val permissionState = rememberLocationPermissionsState { results ->
    if (results.values.none { granted -> granted }) {
        deniedRequestCount += 1
    }
}
val permissionAction = permissionAction(
    deniedRequestCount = deniedRequestCount,
    shouldShowRationale = permissionState.shouldShowRationale,
)
```

Change the helper signature:

```kotlin
@Composable
private fun rememberLocationPermissionsState(
    onPermissionsResult: (Map<String, Boolean>) -> Unit,
): MultiplePermissionsState = rememberMultiplePermissionsState(
    permissions = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ),
    onPermissionsResult = onPermissionsResult,
)
```

Pass one callback to the screen:

```kotlin
onPermissionAction = {
    when (permissionAction) {
        PermissionAction.Request -> permissionState.launchMultiplePermissionRequest()
        PermissionAction.OpenAppSettings -> context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }
}
```

Pass `permissionAction` into `PermissionRequired`. Choose its label:

```kotlin
val actionLabel = when (permissionAction) {
    PermissionAction.Request -> stringResource(R.string.station_list_permission_action)
    PermissionAction.OpenAppSettings -> stringResource(R.string.station_list_permission_settings_action)
}
```

Add Korean `앱 설정에서 허용` and English `Allow in app settings`.

- [ ] **Step 4: Run feature tests and screenshots**

Run:

```bash
./gradlew \
  :feature:station-list:testDebugUnitTest \
  :feature:station-list:verifyRoborazziDebug \
  --warning-mode fail
```

Expected: PASS with the existing initial permission snapshot unchanged.

- [ ] **Step 5: Commit permission UX**

```bash
git add feature/station-list
git commit -m "feat: add permission settings recovery"
```

### Task 4: Add Demo Permission Connected Coverage

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/demoAndroidTest/kotlin/com/gasstation/DemoPermissionFlowTest.kt`
- Modify: `app/src/demoAndroidTest/kotlin/com/gasstation/DemoLocationHookIntegrationTest.kt`

**Interfaces:**
- Consumes: `station-list-permission-guidance` and existing `station-list-watch-toggle` tags.
- Consumes: Android permission-controller resource IDs through UI Automator.
- Produces: device evidence that denied demo does not reveal content and grant reveals fixed-location content.

- [ ] **Step 1: Add UI Automator to app instrumentation tests**

Add:

```kotlin
androidTestImplementation(libs.androidx.uiautomator)
```

- [ ] **Step 2: Add the connected permission flow class**

Create a Hilt Android test with ordered rules:

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoPermissionFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val revokePermissionRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val packageName = instrumentation.targetContext.packageName
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ).forEach { permission ->
                    runCatching {
                        instrumentation.uiAutomation.revokeRuntimePermission(
                            packageName,
                            permission,
                        )
                    }
                }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deniedDemoFirstEntry_staysOnPermissionGuidance() {
        rule.onNodeWithText("위치 권한이 필요합니다.").assertExists()
        SystemClock.sleep(1_500)
        rule.onAllNodesWithTag(
            "station-list-watch-toggle",
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun grantingPermission_revealsDemoContent() {
        rule.onNodeWithText("권한 요청").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val allow = device.wait(
            Until.findObject(
                By.res(
                    "com.android.permissioncontroller",
                    "permission_allow_foreground_only_button",
                ),
            ),
            5_000,
        )
        checkNotNull(allow) { "Foreground location allow button was not shown" }
        allow.click()

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(
                "station-list-watch-toggle",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("서울특별시 역삼동").assertExists()
    }
}
```

Required imports are Android `Manifest`, `SystemClock`, Compose test APIs, AndroidX Test, UI Automator `By`, `UiDevice`, `Until`, JUnit rules, and Hilt test APIs.

- [ ] **Step 3: Compile and run demo connected tests**

Run:

```bash
./gradlew :app:compileDemoDebugAndroidTestKotlin
ANDROID_SERIAL=emulator-5554 ./gradlew \
  :app:connectedDemoDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.DemoPermissionFlowTest
```

Expected: PASS on the configured emulator. If the emulator exposes an OEM permission-controller package, use `By.res("permission_allow_foreground_only_button")` without a package rather than matching visible localized copy.

- [ ] **Step 4: Verify runtime revocation manually**

An app-target instrumentation test cannot safely `pm revoke` its own target while executing because Android may kill that process. Use device evidence instead:

```bash
ADB=/Users/kws/Library/Android/sdk/platform-tools/adb
"$ADB" shell pm revoke com.gasstation.demo android.permission.ACCESS_FINE_LOCATION
"$ADB" shell pm revoke com.gasstation.demo android.permission.ACCESS_COARSE_LOCATION
"$ADB" shell am force-stop com.gasstation.demo
"$ADB" shell monkey -p com.gasstation.demo -c android.intent.category.LAUNCHER 1
```

Expected: stable permission guidance and no station rows. Grant from the UI and confirm fixed demo content; revoke again and confirm the next process start returns to guidance. Record this as device smoke evidence, not as an automated pass.

- [ ] **Step 5: Commit connected permission coverage**

```bash
git add app/build.gradle.kts app/src/demoAndroidTest
git commit -m "test: cover demo permission first entry"
```

### Task 5: Synchronize Permission Contracts And Verify Plan 2

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: Tasks 1–4 permission behavior.
- Produces: live flavor and verification contracts.

- [ ] **Step 1: Update live documentation**

Document:

```text
demo and prod share the same permission state machine. Denied permission wins
before demo override, retained coordinates, cache, and refresh. Demo fixed
coordinates are supplied only after approximate or precise permission grant.

The permission dialog is explicit-action only. Repeated terminal denial changes
the guidance action to Android app settings.
```

Add the connected class command and the manual runtime-revocation boundary to the verification matrix.

- [ ] **Step 2: Run host, flavor, UI, and app verification**

Run:

```bash
./gradlew \
  :domain:location:test \
  :core:location:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:station-list:verifyRoborazziDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  --warning-mode fail
```

Run:

```bash
scripts/agent/verify.sh auto
```

Expected: PASS. Prod assembly does not require a key. A real prod runtime launch
requires a user-local `opinet.apikey`; do not add a placeholder to tracked files
or claim a live Opinet lookup.

- [ ] **Step 3: Review and commit live contracts**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors and no user-owned files staged.

Commit:

```bash
git add README.md docs/architecture.md docs/state-model.md docs/test-strategy.md docs/verification-matrix.md
git commit -m "docs: define permission parity contract"
```
