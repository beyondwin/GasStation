# State Concurrency And Responsibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make permission, location, address, search, refresh, watch, and one-shot command transitions deterministic while reducing `StationListViewModel` to lifecycle coordination and action routing.

**Architecture:** Add generation guards to the existing `LocationStateMachine`, narrow `StationSearchOrchestrator` to observation/cache interpretation, store UI commands in an acknowledged FIFO queue, and move refresh work plus pure state projection into focused collaborators. Keep all collaborators inside `feature:station-list`; no new Gradle module or application-wide MVI framework is introduced.

**Tech Stack:** Kotlin, Coroutines/Flow, AndroidX ViewModel, Compose lifecycle collection, Hilt, JUnit4, coroutine test, Roborazzi/Compose semantics

## Global Constraints

- This plan starts only after `2026-08-12-data-correctness-hardening.md` is green.
- Permission denial or GPS disable invalidates obsolete location, address, query, and refresh work.
- Superseded work is silent and never becomes failure copy or analytics.
- UI commands survive collector gaps and recomposition, execute FIFO, and leave state only after exact-ID acknowledgement.
- Command persistence across process death is not promised unless separately documented.
- Watch ON/OFF completion order must preserve the latest user intent and repeated ON must preserve `watchedAt`.
- `StationListStateAssembler` performs no I/O, coroutine launch, or clock read.
- Update `docs/state-model.md`, `docs/architecture.md`, `docs/module-contracts.md`, and test documentation with the code they describe.

---

### Task 1: Guard location and address commits with generations

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/LocationStateMachineTest.kt`
- Modify: deferred fakes in station-list test fixtures

**Interfaces:**
- Adds: `LocationAcquisitionResult.Superseded`
- Changes: `suspend fun resolveAddressLabel(coordinates: Coordinates)` resolves and conditionally commits internally
- Removes: public two-step `onAddressResolved(coordinates, label)`

- [ ] **Step 1: Add RED generation tests**

Use `CompletableDeferred` fakes for these exact cases:

- `precise location completion is superseded after permission downgrades to approximate`
- `older location completion cannot overwrite a newer request`
- `gps disable supersedes an in flight location completion`
- `older address completion for the same coordinates cannot overwrite the latest label`
- `permission change supersedes an in flight address completion`

- [ ] **Step 2: Run RED**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests 'com.gasstation.feature.stationlist.LocationStateMachineTest'`

Expected: old precise/same-coordinate completions still commit or the new `Superseded` type is absent.

- [ ] **Step 3: Add explicit generations**

Add private monotonic permission, location-request, and address-request generations. A real permission precision transition and GPS disable advance the relevant generations. Each request captures its own tuple and verifies it before mutating state.

```kotlin
data object Superseded : LocationAcquisitionResult
```

Make address resolution one atomic operation: capture address generation, call the use case, normalize, then commit only when both generation and coordinates remain current.

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*LocationStateMachineTest'`

```bash
git add feature/station-list
git commit -m "fix: reject obsolete location state completions"
```

### Task 2: Recover search observation without breaking current refresh consumers

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestratorTest.kt`

**Interfaces:**
- Keeps: active query, cached snapshot state, search result, blocking failure
- Adds: `val observationFailed: StateFlow<Boolean>` and `fun retryObservation()`
- Moves in Task 5: `RefreshNearbyStationsUseCase`, `refresh`, `RefreshOutcome`, and criteria-refresh policy out of this class

- [ ] **Step 1: Add RED recovery tests**

Add repository flows that fail once, can be resubscribed, and can be cancelled. Cover failure keeping the outer query collector alive, same-query retry, query-change recovery, and cancellation propagation.

- [ ] **Step 2: Run RED**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*StationSearchOrchestratorTest'`

- [ ] **Step 3: Implement retry generation**

Combine distinct active query with a monotonic retry generation. Catch exceptions inside the per-query branch, rethrow `CancellationException`, preserve the last search result, set `observationFailed = true`, and emit no synthetic cache row. Query change or `retryObservation()` clears failure and creates a new repository subscription.

- [ ] **Step 4: Preserve refresh compatibility until Task 5**

Keep the current refresh use-case dependency, `refresh`, `RefreshOutcome`, and criteria helper temporarily so this commit remains buildable. Task 5 moves those members into `RefreshCoordinator` and deletes them from the orchestrator in the same GREEN commit. Blocking-failure resolution remains based on `hasCachedSnapshot`.

- [ ] **Step 5: Run GREEN and commit**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*StationSearchOrchestratorTest'`

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestratorTest.kt
git commit -m "refactor: narrow station search observation"
```

### Task 3: Preserve latest watch intent and stable ordering

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/LatestWatchIntentGate.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/LatestWatchIntentGateTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt`
- Modify: recording DAO and watch repository/DAO tests

**Interfaces:**
- Produces: station-ID generation gate shared by `updateWatchState` and `removeWatchedStation`
- Replaces: `REPLACE` upsert with `insertIfAbsent(entity): Long` for idempotent ON

- [ ] **Step 1: Add RED tests**

Cover ON→OFF, OFF→ON, different-station independence, repeated ON timestamp preservation, and DAO ordering.

- [ ] **Step 2: Run RED**

Run: `./gradlew :core:database:testDebugUnitTest --tests '*WatchedStationDaoTest' :data:station:testDebugUnitTest --tests '*WatchlistRepositoryTest' --tests '*LatestWatchIntentGateTest'`

- [ ] **Step 3: Implement the gate and idempotent insert**

Use the same bounded generation/mutex rules as `LatestRefreshGate`, keyed by station ID. Both public repository mutation methods register through the same gate; use a private persistence method to avoid double registration. `watched = true` uses `@Insert(onConflict = IGNORE)` so an existing row keeps its original `watchedAtEpochMillis`.

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew :core:database:testDebugUnitTest :data:station:testDebugUnitTest :feature:watchlist:testDebugUnitTest`

```bash
git add core/database data/station feature/watchlist
git commit -m "fix: preserve latest watch intent"
```

### Task 4: Replace lossy effects with an acknowledged FIFO command queue

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCommand.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCommandQueue.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListCommandQueueTest.kt`
- Modify: `StationListUiState.kt`, `StationListAction.kt`, `StationListRoute.kt`, `StationListViewModel.kt`
- Delete after migration: `StationListEffect.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Modify: `app/src/test/java/com/gasstation/navigation/GasStationTopLevelNavigationTest.kt`

**Interfaces:**

```kotlin
data class StationListUiCommand(
    val id: Long,
    val payload: StationListCommandPayload,
)

sealed interface StationListCommandPayload {
    data class OpenExternalMap(
        val provider: MapProvider,
        val stationName: String,
        val originLatitude: Double?,
        val originLongitude: Double?,
        val latitude: Double,
        val longitude: Double,
    ) : StationListCommandPayload
    data object OpenLocationSettings : StationListCommandPayload
    data class ShowSnackbar(val message: StringResource) : StationListCommandPayload
}
```

`StationListAction.CommandHandled(commandId: Long)` acknowledges only the current head.

- [ ] **Step 1: Add pure queue RED tests**

Prove no-collector retention, FIFO ordering, non-head acknowledgement no-op, head acknowledgement reveal-next, and repeated acknowledgement safety.

- [ ] **Step 2: Implement monotonic queue IDs**

`enqueue(payload)` appends an immutable command; `acknowledge(id)` removes only a matching head. Use a local monotonic `Long` source. Do not write commands to `SavedStateHandle`.

- [ ] **Step 3: Add ViewModel/route RED tests**

Cover a station click without an active collector, acknowledgement preventing repetition, two feedback commands in FIFO order, and analytics logging exactly once.

- [ ] **Step 4: Replace `MutableSharedFlow` collection**

Put `pendingCommands` in `StationListUiState`. In the route, handle only `firstOrNull()` in `LaunchedEffect(command.id)` and acknowledge that ID in `finally`. Remove `collectLatest` and the `effects` property.

- [ ] **Step 5: Run GREEN and commit**

Run: `./gradlew :feature:station-list:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest`

```bash
git add feature/station-list app
git commit -m "refactor: queue station UI commands"
```

### Task 5: Extract refresh lifecycle ownership

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/RefreshCoordinator.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RefreshCoordinatorTest.kt`
- Modify: `StationListViewModel.kt`, `StationSearchOrchestrator.kt`, their tests, and fixtures

**Interfaces:**

```kotlin
data class RefreshCoordinatorState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val activeQuery: StationQuery? = null,
)

sealed interface RefreshRequest {
    data class AcquireLocation(
        val preferences: UserPreferences,
        val showPermissionDeniedFeedback: Boolean,
    ) : RefreshRequest
    data class ActiveQuery(val query: StationQuery) : RefreshRequest
}
```

The coordinator exposes `state`, `request(scope, request, onResult)`, `cancel()`, and pure `requiresRefresh(previous, next)`.

- [ ] **Step 1: Add RED coordinator tests**

Cover latest-query cancellation, permission denial, GPS disable, superseded location, criteria change with same coordinates, different-coordinate exclusion, and cancellation not becoming failure.

- [ ] **Step 2: Implement job/work-ID ownership**

Move `activeRefreshJob`, `refreshWorkId`, indicator finalization, query revalidation, location acquisition, criteria-refresh policy, and `RefreshNearbyStationsUseCase` invocation out of the ViewModel/orchestrator. Delete `refresh` and `RefreshOutcome` from `StationSearchOrchestrator` in this same GREEN step. Results return through the supplied suspend callback; do not introduce another event flow.

- [ ] **Step 3: Route results in the ViewModel**

The ViewModel turns coordinator results into orchestrator blocking failure, analytics, or queued commands. `LocationAcquisitionResult.Superseded` returns silently. `RetryClicked` first retries a failed search observation; otherwise it requests refresh.

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*RefreshCoordinatorTest' --tests '*StationListViewModelTest'`

```bash
git add feature/station-list
git commit -m "refactor: extract station refresh coordinator"
```

### Task 6: Project screen state with a pure assembler

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStateAssembler.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStateInputs.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListStateAssemblerTest.kt`
- Modify: `StationListViewModel.kt`, `StationListBodyState.kt`, related tests

**Interfaces:**
- Produces: `fun assemble(inputs: StationListStateInputs): StationListUiState`
- Consumes immutable preference, location, refresh, search, blocking-failure, and command inputs

- [ ] **Step 1: Add RED transition-table tests**

Cover permission priority, GPS priority, preference failure, initial loading, cached content during refresh/failure, successful empty snapshot, stale content, and pending command immutability.

- [ ] **Step 2: Implement immutable inputs and assembler**

Move `PreferenceLoadState`, transient refresh state, and search projection into `StationListStateInputs.kt`. The assembler contains only deterministic mapping. Preserve list-instance reuse in a separate pure search projection before assembly.

- [ ] **Step 3: Bind assembled state**

The ViewModel combines collaborator state and calls the assembler. Delete inline construction of `StationListUiState`; retain lifecycle collection only.

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew :feature:station-list:testDebugUnitTest`

```bash
git add feature/station-list
git commit -m "refactor: assemble station state purely"
```

### Task 7: Finish the thin ViewModel and split behavioral tests

**Files:**
- Modify: `StationListViewModel.kt`
- Create: `StationListPreferencesTest.kt`
- Create: `StationListRefreshIntegrationTest.kt`
- Create: `StationListCommandIntegrationTest.kt`
- Create: `StationListWatchMutationTest.kt`
- Modify: `StationListViewModelTestFixtures.kt`
- Delete after migration: monolithic `StationListViewModelTest.kt`

- [ ] **Step 1: Move tests by behavior owner without changing assertions**

Preferences, refresh composition, commands, and watch behavior each get one file. Detailed location, coordinator, orchestrator, and assembler policies remain in their dedicated tests.

- [ ] **Step 2: Add final composition RED tests**

Add `retry after observation failure resubscribes before requesting refresh`, `view model publishes assembler output`, `permission change routes cancellation to refresh coordinator`, and `station click queues one external map command and logs once`.

- [ ] **Step 3: Remove residual policy from the ViewModel**

Delete direct refresh jobs/work IDs, direct address jobs, inline UI-state construction, `SharedFlow`, and criteria-refresh policy. Retain settings observation/writes, lifecycle binding, action routing, and collaborator composition.

- [ ] **Step 4: Run phase regression**

Run: `./gradlew :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest verifyModuleBoundaries`

- [ ] **Step 5: Commit**

```bash
git add feature/station-list feature/watchlist app
git commit -m "refactor: narrow station list view model"
```

### Task 8: Synchronize state contracts and close Phases 2–3

**Files:**
- Modify: `docs/state-model.md`
- Modify: `docs/architecture.md`
- Modify: `docs/module-contracts.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

- [ ] **Step 1: Document ownership and transitions**

Describe generation invalidation, observation retry, acknowledged command queue, watch latest intent, coordinator ownership, pure assembler, and thin ViewModel. State explicitly that command process-death persistence is not promised.

- [ ] **Step 2: Run UI/state regression**

Run: `./gradlew :data:station:testDebugUnitTest :core:database:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest verifyRoborazziDebug verifyModuleBoundaries`

- [ ] **Step 3: Verify documentation and final diff**

Run: `scripts/agent/verify.sh docs`

Run: `git diff --check`

- [ ] **Step 4: Commit and run the repository gate**

```bash
git add docs/architecture.md docs/module-contracts.md docs/state-model.md docs/test-strategy.md docs/verification-matrix.md
git commit -m "docs: define deterministic station state ownership"
```

Run: `scripts/agent/verify.sh auto`

Expected: all selected scopes pass at the same HEAD before the quality-gate plan begins.
