# Settings State Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Nearby and Settings from rendering or acting on fabricated defaults, make preference writes acknowledge the persisted value, and protect bidirectional cross-screen synchronization.

**Architecture:** Keep DataStore behind `core:datastore -> data:settings -> domain:settings`. Change mutation contracts to return the value committed by `DataStore.updateData`, add an atomic sort-toggle use case, and represent preference readiness independently in each feature. Nearby creates queries only from a ready preference snapshot; Settings navigates back only after a successful write.

**Tech Stack:** Kotlin, Coroutines/Flow, AndroidX DataStore, Hilt, Jetpack Compose, Compose UI tests, Robolectric, Android instrumentation

## Global Constraints

- `SettingsRepository` remains the only domain settings boundary; feature modules must not call DataStore or `data:settings`.
- No feature may consume `UserPreferences.default()` as if it were a loaded preference.
- UI selection is derived from a value returned by persistence or observed from DataStore, never from an optimistic duplicate.
- Search radius, fuel type, brand filter, and sort order affect Nearby; map provider is consumed only by external handoff.
- Preserve the yellow, black, and white Urban Signal visual system, accessibility semantics, and existing stable test tags.
- Do not change deterministic demo startup reset in this plan.
- Do not add dependencies.
- Preserve the user's existing `settings.gradle.kts` and `gradle/gradle-daemon-jvm.properties` changes.

---

## File Structure

### Domain and persistence contract

- Modify `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt` so `update` returns `StoredUserPreferences`.
- Modify `core/datastore/src/main/kotlin/com/gasstation/core/datastore/AndroidUserPreferencesDataSource.kt` to return `DataStore.updateData`.
- Modify `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt` so mutation returns the committed `UserPreferences`.
- Modify `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt` and the five explicit update use cases to return `UserPreferences`.
- Create `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/TogglePreferredSortOrderUseCase.kt` for repository-atomic sort toggling.

### Nearby preference readiness

- Modify `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt` to hold nullable ready preferences and a read-failure flag instead of selected defaults.
- Modify `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt` to own `Loading/Ready/Failed`, retry observation, serialize writes, and gate query/action/map consumption.
- Modify `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBodyState.kt`, `StationListScreen.kt`, `StationListFilterRail.kt`, `StationListQuerySummary.kt`, and `StationListDecisionSummary.kt` to render preference loading/failure without reading null preferences.
- Modify station-list test fixtures and tests to control delayed, failed, and successful preference flows.

### Settings readiness and write acknowledgement

- Replace `SettingsUiState` with `Loading`, `LoadFailed`, and `Ready`.
- Create `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsEffect.kt`.
- Create `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsStateScreens.kt` for loading and retryable failure surfaces.
- Modify `SettingsViewModel`, routes, screens, option models, strings, and tests.

### Cross-screen contract and live documentation

- Modify `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`.
- Modify `docs/state-model.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`, `docs/architecture.md`, and `README.md`.

---

### Task 1: Return Committed Preferences And Add Atomic Sort Toggle

**Files:**
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/AndroidUserPreferencesDataSource.kt`
- Modify: `core/datastore/src/test/kotlin/com/gasstation/core/datastore/AndroidUserPreferencesDataSourceTest.kt`
- Modify: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt`
- Modify: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateBrandFilterUseCase.kt`
- Modify: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateFuelTypeUseCase.kt`
- Modify: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateMapProviderUseCase.kt`
- Modify: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdatePreferredSortOrderUseCase.kt`
- Modify: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateSearchRadiusUseCase.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/TogglePreferredSortOrderUseCase.kt`
- Modify: `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UpdateSettingsUseCasesTest.kt`
- Modify: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- Modify: `data/settings/src/test/kotlin/com/gasstation/data/settings/DefaultSettingsRepositoryTest.kt`
- Modify: `app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/SettingsUseCaseTestFixture.kt`

**Interfaces:**
- Consumes: `DataStore<StoredUserPreferences>.updateData`.
- Produces: `suspend fun UserPreferencesDataSource.update(...): StoredUserPreferences`.
- Produces: `suspend fun SettingsRepository.updateUserPreferences(...): UserPreferences`.
- Produces: every explicit update use case returns `UserPreferences`.
- Produces: `TogglePreferredSortOrderUseCase.invoke(): UserPreferences`.

- [ ] **Step 1: Write failing committed-value and atomic-toggle tests**

Add these tests:

```kotlin
@Test
fun `update returns the value committed by datastore`() = runBlocking {
    val file = createTempStoreFile()
    val scope = testScope()
    val dataSource = AndroidUserPreferencesDataSource(
        DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            scope = scope.scope,
            produceFile = { file },
        ),
    )

    val committed = dataSource.update { current ->
        current.copy(sortOrderName = "PRICE")
    }

    assertEquals("PRICE", committed.sortOrderName)
    assertEquals(committed, dataSource.userPreferences.first())
    scope.job.cancelAndJoin()
}
```

```kotlin
@Test
fun `toggle preferred sort order transforms the repository value atomically`() = runTest {
    val repository = FakeSettingsRepository(
        UserPreferences.default().copy(sortOrder = SortOrder.PRICE),
    )

    val committed = TogglePreferredSortOrderUseCase(repository)()

    assertEquals(SortOrder.DISTANCE, committed.sortOrder)
    assertEquals(committed, repository.current)
}
```

```kotlin
@Test
fun `repository update returns mapped committed preferences and preserves siblings`() = runBlocking {
    val dataSource = InMemoryUserPreferencesDataSource(
        StoredUserPreferences.Default.copy(
            fuelTypeName = "DIESEL",
            mapProviderName = "NAVER_MAP",
        ),
    )
    val repository = DefaultSettingsRepository(dataSource)

    val committed = repository.updateUserPreferences { current ->
        current.copy(sortOrder = SortOrder.PRICE)
    }

    assertEquals(SortOrder.PRICE, committed.sortOrder)
    assertEquals(FuelType.DIESEL, committed.fuelType)
    assertEquals(MapProvider.NAVER_MAP, committed.mapProvider)
}
```

- [ ] **Step 2: Run the focused tests and capture RED**

Run:

```bash
./gradlew :core:datastore:testDebugUnitTest \
  --tests '*AndroidUserPreferencesDataSourceTest*update returns*'
./gradlew :domain:settings:test \
  --tests '*UpdateSettingsUseCasesTest*toggle preferred*'
./gradlew :data:settings:testDebugUnitTest \
  --tests '*DefaultSettingsRepositoryTest*returns mapped*'
```

Expected: compilation fails because mutations still return `Unit` and `TogglePreferredSortOrderUseCase` does not exist.

- [ ] **Step 3: Implement return-value contracts**

Use these exact signatures and bodies:

```kotlin
interface UserPreferencesDataSource {
    val userPreferences: Flow<StoredUserPreferences>

    suspend fun update(
        transform: (StoredUserPreferences) -> StoredUserPreferences,
    ): StoredUserPreferences
}
```

```kotlin
override suspend fun update(
    transform: (StoredUserPreferences) -> StoredUserPreferences,
): StoredUserPreferences = dataStore.updateData(transform)
```

```kotlin
interface SettingsRepository {
    fun observeUserPreferences(): Flow<UserPreferences>

    suspend fun updateUserPreferences(
        transform: (UserPreferences) -> UserPreferences,
    ): UserPreferences
}
```

```kotlin
override suspend fun updateUserPreferences(
    transform: (UserPreferences) -> UserPreferences,
): UserPreferences = dataSource.update { current ->
    transform(current.toDomain()).toStored()
}.toDomain()
```

Each explicit update use case returns the repository result. For example:

```kotlin
suspend operator fun invoke(fuelType: FuelType): UserPreferences =
    settingsRepository.updateUserPreferences { current ->
        current.copy(fuelType = fuelType)
}
```

Replace the private `Updater` adapter and secondary function constructor in
`UpdatePreferredSortOrderUseCase` with the same direct repository shape:

```kotlin
class UpdatePreferredSortOrderUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(sortOrder: SortOrder): UserPreferences =
        settingsRepository.updateUserPreferences { current ->
            current.copy(sortOrder = sortOrder)
        }
}
```

Create the atomic toggle:

```kotlin
package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

class TogglePreferredSortOrderUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): UserPreferences =
        settingsRepository.updateUserPreferences { current ->
            current.copy(
                sortOrder = when (current.sortOrder) {
                    SortOrder.DISTANCE -> SortOrder.PRICE
                    SortOrder.PRICE -> SortOrder.DISTANCE
                },
            )
        }
}
```

Update every fake repository/data source override to return its new state:

```kotlin
override suspend fun updateUserPreferences(
    transform: (UserPreferences) -> UserPreferences,
): UserPreferences {
    state.value = transform(state.value)
    return state.value
}
```

```kotlin
override suspend fun update(
    transform: (StoredUserPreferences) -> StoredUserPreferences,
): StoredUserPreferences {
    state.value = transform(state.value)
    return state.value
}
```

- [ ] **Step 4: Run settings persistence suites**

Run:

```bash
./gradlew \
  :core:datastore:testDebugUnitTest \
  :domain:settings:test \
  :data:settings:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  --warning-mode fail
```

Expected: PASS.

- [ ] **Step 5: Commit the persisted mutation contract**

```bash
git add \
  core/datastore \
  domain/settings \
  data/settings \
  app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt \
  feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/SettingsUseCaseTestFixture.kt
git commit -m "fix: acknowledge persisted settings updates"
```

### Task 2: Gate Nearby On Preference Readiness

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFailureReason.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBodyState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListDecisionSummary.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/SettingsUseCaseTestFixture.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

**Interfaces:**
- Consumes: Task 1 mutation methods returning `UserPreferences`.
- Consumes: `TogglePreferredSortOrderUseCase`.
- Produces: `StationListUiState.preferences: UserPreferences?`.
- Produces: `StationListUiState.preferenceLoadFailed: Boolean`.
- Produces: query and preference actions are inert until preferences are ready.

- [ ] **Step 1: Extend the settings fixture with delayed and failing flows**

Replace the fixture constructor with an optional initial emission and add failure controls:

```kotlin
internal class SettingsUseCaseTestFixture(
    initialPreferences: UserPreferences? = UserPreferences.default(),
) {
    private val state = MutableSharedFlow<UserPreferences>(replay = 1)
    private val failure = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    init {
        initialPreferences?.let(state::tryEmit)
    }

    private val repository = object : SettingsRepository {
        override fun observeUserPreferences(): Flow<UserPreferences> =
            merge(
                state,
                failure.map { throwable -> throw throwable },
            )

        override suspend fun updateUserPreferences(
            transform: (UserPreferences) -> UserPreferences,
        ): UserPreferences {
            val current = state.replayCache.single()
            val updated = transform(current)
            state.emit(updated)
            return updated
        }
    }

    val observeUserPreferences = ObserveUserPreferencesUseCase(repository)
    val updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository)
    val togglePreferredSortOrder = TogglePreferredSortOrderUseCase(repository)
    val updateSearchRadius = UpdateSearchRadiusUseCase(repository)
    val updateFuelType = UpdateFuelTypeUseCase(repository)
    val updateBrandFilter = UpdateBrandFilterUseCase(repository)

    fun emit(preferences: UserPreferences) {
        state.tryEmit(preferences)
    }

    fun fail(throwable: Throwable) {
        failure.tryEmit(throwable)
    }

    val currentPreferences: UserPreferences
        get() = state.replayCache.single()
}
```

Imports must include `MutableSharedFlow`, `merge`, and `map`.

- [ ] **Step 2: Write failing Nearby readiness tests**

Add:

```kotlin
@Test
fun `preferences do not default or start a query before first emission`() = runTest(dispatcher) {
    val repository = FakeStationRepository(emptySearchResult())
    val settings = SettingsUseCaseTestFixture(initialPreferences = null)
    val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

    viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
    viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
    viewModel.onAction(StationListAction.AutoRefreshRequested)
    viewModel.onAction(StationListAction.SortToggleRequested)
    viewModel.onAction(StationListAction.StationClicked(StationListItemUiModel(stationEntry())))
    advanceUntilIdle()

    assertEquals(null, viewModel.uiState.value.preferences)
    assertTrue(repository.refreshedQueries.isEmpty())
    assertTrue(repository.observedQueries.isEmpty())
    assertTrue(viewModel.uiState.value.isLoading)
}
```

```kotlin
@Test
fun `first persisted preferences create the first query without a default query`() = runTest(dispatcher) {
    val repository = FakeStationRepository(emptySearchResult())
    val settings = SettingsUseCaseTestFixture(initialPreferences = null)
    val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())
    val expected = UserPreferences.default().copy(
        searchRadius = SearchRadius.KM_5,
        fuelType = FuelType.DIESEL,
        brandFilter = BrandFilter.GSC,
        sortOrder = SortOrder.PRICE,
    )

    viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
    viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
    settings.emit(expected)
    viewModel.onAction(StationListAction.AutoRefreshRequested)
    advanceUntilIdle()

    assertEquals(expected, viewModel.uiState.value.preferences)
    assertEquals(SearchRadius.KM_5, repository.refreshedQueries.single().radius)
    assertEquals(FuelType.DIESEL, repository.refreshedQueries.single().fuelType)
    assertEquals(BrandFilter.GSC, repository.refreshedQueries.single().brandFilter)
    assertEquals(SortOrder.PRICE, repository.refreshedQueries.single().sortOrder)
}
```

```kotlin
@Test
fun `preference read failure shows retryable failure without a default query`() = runTest(dispatcher) {
    val repository = FakeStationRepository(emptySearchResult())
    val settings = SettingsUseCaseTestFixture(initialPreferences = null)
    val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

    settings.fail(IllegalStateException("datastore read failed"))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.preferenceLoadFailed)
    assertEquals(StationListBodyState.Failure(StationListFailureReason.PreferencesFailed), viewModel.uiState.value.toBodyState())
    assertTrue(repository.refreshedQueries.isEmpty())
}
```

- [ ] **Step 3: Run station-list readiness tests and capture RED**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest \
  --tests '*StationListViewModelTest*preferences do not default*' \
  --tests '*StationListViewModelTest*first persisted preferences*' \
  --tests '*StationListViewModelTest*preference read failure*'
```

Expected: compilation fails because `StationListUiState` has no readiness fields and the ViewModel still seeds defaults.

- [ ] **Step 4: Implement typed preference readiness**

Replace selected default fields in `StationListUiState` with:

```kotlin
val preferences: UserPreferences? = null,
val preferenceLoadFailed: Boolean = false,
val pendingPreferenceWrite: Boolean = false,
```

Use a private state in the ViewModel:

```kotlin
private sealed interface PreferenceLoadState {
    data object Loading : PreferenceLoadState
    data class Ready(val preferences: UserPreferences) : PreferenceLoadState
    data object Failed : PreferenceLoadState
}

private val preferenceState = MutableStateFlow<PreferenceLoadState>(PreferenceLoadState.Loading)
private var preferenceObservationJob: Job? = null
```

Observation must retry explicitly and never emit defaults:

```kotlin
private fun observePreferences() {
    preferenceObservationJob?.cancel()
    preferenceState.value = PreferenceLoadState.Loading
    preferenceObservationJob = observeUserPreferences()
        .onEach { preferenceState.value = PreferenceLoadState.Ready(it) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            preferenceState.value = PreferenceLoadState.Failed
        }
        .launchIn(viewModelScope)
}
```

Build queries only from `Ready`:

```kotlin
val queryFlow = combine(preferenceState, locationStateMachine.state) { state, location ->
    val preferences = (state as? PreferenceLoadState.Ready)?.preferences
    val coordinates = location.usableCoordinates()
    if (preferences == null || coordinates == null) {
        null
    } else {
        buildQuery(preferences, coordinates)
    }
}.distinctUntilChanged()
```

Bind UI with the persisted snapshot:

```kotlin
val currentPreferenceState = preferenceState.value
val readyPreferences =
    (currentPreferenceState as? PreferenceLoadState.Ready)?.preferences
StationListUiState(
    currentCoordinates = location.currentCoordinates,
    currentAddressLabel = location.currentAddressLabel,
    permissionState = location.permissionState,
    needsRecoveryRefresh = location.needsRecoveryRefresh,
    isGpsEnabled = location.isGpsEnabled,
    isAvailabilityKnown = location.isAvailabilityKnown,
    isLoading =
        transient.isLoading ||
            currentPreferenceState is PreferenceLoadState.Loading,
    isRefreshing = transient.isRefreshing,
    isStale = resultProjection.freshness is StationFreshness.Stale,
    blockingFailure = blockingFailure,
    stations = resultProjection.stations,
    preferences = readyPreferences,
    preferenceLoadFailed = currentPreferenceState is PreferenceLoadState.Failed,
    pendingPreferenceWrite = transient.pendingPreferenceWrite,
    lastUpdatedAt = resultProjection.fetchedAt,
)
```

Serialize preference writes and use committed return values:

```kotlin
private fun updatePreference(update: suspend () -> UserPreferences) {
    if (preferenceState.value !is PreferenceLoadState.Ready) return
    if (transientState.value.pendingPreferenceWrite) return
    viewModelScope.launch {
        transientState.update { it.copy(pendingPreferenceWrite = true) }
        try {
            preferenceState.value = PreferenceLoadState.Ready(update())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            mutableEffects.emit(
                StationListEffect.ShowSnackbar(
                    StringResource.fromId(R.string.station_list_preference_save_failed),
                ),
            )
        } finally {
            transientState.update { it.copy(pendingPreferenceWrite = false) }
        }
    }
}
```

Route actions through `updatePreference`, and inject/use `TogglePreferredSortOrderUseCase` for `SortToggleRequested`. `StationClicked`, `refresh`, and manual preference actions return immediately unless the state is `Ready`.

Remove `UpdatePreferredSortOrderUseCase` from the `StationListViewModel`
constructor and imports. Replace that dependency with
`TogglePreferredSortOrderUseCase`; Settings continues to use
`UpdatePreferredSortOrderUseCase` for explicit option selection. Update the two
direct ViewModel constructor sites in `StationListViewModelTest.kt` and
`GpsAvailabilityMonitorTest.kt`.

Add `PreferencesFailed` and body priority:

```kotlin
internal fun StationListUiState.toBodyState(): StationListBodyState = when {
    permissionState == LocationPermissionState.Denied -> StationListBodyState.PermissionRequired
    !isGpsEnabled -> StationListBodyState.GpsRequired
    preferenceLoadFailed -> StationListBodyState.Failure(StationListFailureReason.PreferencesFailed)
    preferences == null -> StationListBodyState.InitialLoading
    isLoading && stations.isEmpty() -> StationListBodyState.InitialLoading
    blockingFailure != null && stations.isEmpty() -> StationListBodyState.Failure(blockingFailure)
    else -> StationListBodyState.Results
}
```

At the top of result-only composables use:

```kotlin
val preferences = requireNotNull(uiState.preferences) {
    "Results require ready user preferences"
}
```

Pass `enabled = !uiState.pendingPreferenceWrite` to filter chips and the sort chip. Use `preferences.searchRadius`, `preferences.fuelType`, `preferences.brandFilter`, and `preferences.sortOrder`.

For `RetryClicked`, call `observePreferences()` when `preferenceLoadFailed`; otherwise retain refresh behavior.

- [ ] **Step 5: Run the full station-list suite**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --warning-mode fail
```

Expected: PASS, including updated tests that construct `StationListUiState(preferences = UserPreferences.default())` for result rendering.

- [ ] **Step 6: Commit Nearby readiness**

```bash
git add feature/station-list
git commit -m "fix: gate nearby search on loaded preferences"
```

### Task 3: Model Settings Loading, Saving, Failure, And Success Effects

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsEffect.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsStateScreens.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsAction.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`
- Modify: `feature/settings/src/main/res/values-en/strings.xml`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsUiStateTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- Update Roborazzi baselines only if verified rendering changes.

**Interfaces:**
- Consumes: Task 1 update use cases returning persisted preferences.
- Produces: sealed `SettingsUiState`.
- Produces: `SettingsEffect.SelectionSaved(section)` and `SettingsEffect.SaveFailed`.
- Produces: option test tags `settings-option-<enum-name>`.

- [ ] **Step 1: Write failing ViewModel state/effect tests**

Use a `MutableSharedFlow<UserPreferences>` fake with no initial replay for loading. Add:

```kotlin
@Test
fun `settings exposes loading without default selections before first emission`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    val repository = ControllableSettingsRepository()
    val viewModel = settingsViewModel(repository)

    advanceUntilIdle()

    assertEquals(SettingsUiState.Loading, viewModel.uiState.value)
}
```

```kotlin
@Test
fun `successful selection emits completion after committed state is ready`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    val repository = ControllableSettingsRepository(UserPreferences.default())
    val viewModel = settingsViewModel(repository)
    advanceUntilIdle()

    viewModel.effects.test {
        viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as SettingsUiState.Ready
        assertEquals(SortOrder.PRICE, ready.preferences.sortOrder)
        assertEquals(null, ready.savingSection)
        assertEquals(SettingsEffect.SelectionSaved(SettingsSection.SortOrder), awaitItem())
    }
}
```

```kotlin
@Test
fun `failed selection keeps prior value and emits failure without completion`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    val repository = ControllableSettingsRepository(
        initial = UserPreferences.default(),
        updateFailure = IllegalStateException("write failed"),
    )
    val viewModel = settingsViewModel(repository)
    advanceUntilIdle()

    viewModel.effects.test {
        viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
        advanceUntilIdle()

        val ready = viewModel.uiState.value as SettingsUiState.Ready
        assertEquals(FuelType.GASOLINE, ready.preferences.fuelType)
        assertEquals(SettingsEffect.SaveFailed, awaitItem())
        expectNoEvents()
    }
}
```

- [ ] **Step 2: Run focused Settings tests and capture RED**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest \
  --tests '*SettingsViewModelTest*loading without default*' \
  --tests '*SettingsViewModelTest*completion after committed*' \
  --tests '*SettingsViewModelTest*failed selection*'
```

Expected: compilation fails because Settings has no readiness state or effects.

- [ ] **Step 3: Implement sealed state and effects**

Define:

```kotlin
sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data object LoadFailed : SettingsUiState
    data class Ready(
        val preferences: UserPreferences,
        val savingSection: SettingsSection? = null,
    ) : SettingsUiState
}
```

Move `selectedLabelFor` and `optionsFor` to `SettingsUiState.Ready`.

Define:

```kotlin
sealed interface SettingsEffect {
    data class SelectionSaved(val section: SettingsSection) : SettingsEffect
    data object SaveFailed : SettingsEffect
}
```

Add `RetryLoad`:

```kotlin
sealed interface SettingsAction {
    data object RetryLoad : SettingsAction
    data class SortOrderSelected(val sortOrder: SortOrder) : SettingsAction
    data class FuelTypeSelected(val fuelType: FuelType) : SettingsAction
    data class SearchRadiusSelected(val radius: SearchRadius) : SettingsAction
    data class BrandFilterSelected(val brandFilter: BrandFilter) : SettingsAction
    data class MapProviderSelected(val mapProvider: MapProvider) : SettingsAction
}
```

Add a stable key to each option:

```kotlin
data class SettingOptionUiModel(
    val key: String,
    val label: StringResource,
    val subtitle: StringResource? = null,
    val meta: StringResource? = null,
    val action: SettingsAction,
    val isSelected: Boolean,
    val brandIconBrand: Brand? = null,
    val brandIconTag: String? = null,
)
```

Use enum names for `key`.

In `SettingsViewModel`, keep
`private var lastPersistedPreferences: UserPreferences? = null` plus a
retryable observation job. Update that field from every observed emission and
every committed mutation. On selection:

```kotlin
private fun saveSelection(
    section: SettingsSection,
    update: suspend () -> UserPreferences,
) {
    val ready = mutableUiState.value as? SettingsUiState.Ready ?: return
    if (ready.savingSection != null) return
    viewModelScope.launch {
        mutableUiState.value = ready.copy(savingSection = section)
        try {
            val committed = update()
            lastPersistedPreferences = committed
            mutableUiState.value = SettingsUiState.Ready(committed)
            mutableEffects.emit(SettingsEffect.SelectionSaved(section))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            mutableUiState.value = SettingsUiState.Ready(
                lastPersistedPreferences ?: ready.preferences,
            )
            mutableEffects.emit(SettingsEffect.SaveFailed)
        }
    }
}
```

Observation uses `.catch` to set `LoadFailed`, rethrowing cancellation.
When an observed DataStore emission arrives during a write, preserve the
in-flight section:

```kotlin
.onEach { preferences ->
    lastPersistedPreferences = preferences
    val savingSection =
        (mutableUiState.value as? SettingsUiState.Ready)?.savingSection
    mutableUiState.value = SettingsUiState.Ready(
        preferences = preferences,
        savingSection = savingSection,
    )
}
```

`RetryLoad` restarts the observation job.

- [ ] **Step 4: Implement loading/failure UI and save-aware navigation**

`SettingsRoute` branches:

```kotlin
when (val state = uiState) {
    SettingsUiState.Loading -> SettingsLoadingScreen()
    SettingsUiState.LoadFailed -> SettingsLoadFailureScreen(
        onRetry = { viewModel.onAction(SettingsAction.RetryLoad) },
    )
    is SettingsUiState.Ready -> SettingsScreen(
        uiState = state,
        onSectionClick = onSectionClick,
    )
}
```

`SettingsDetailRoute` collects effects:

```kotlin
LaunchedEffect(viewModel, section) {
    viewModel.effects.collect { effect ->
        when (effect) {
            is SettingsEffect.SelectionSaved -> {
                if (effect.section == section) onBackClick()
            }
            SettingsEffect.SaveFailed -> {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_save_failed),
                )
            }
        }
    }
}
```

Remove the immediate `onBackClick()` from `onOptionClick`. Pass `isSaving = state.savingSection == section` to `SettingsDetailScreen`. Disable option clicks and the top-bar back action while saving; install `BackHandler(enabled = isSaving) { }`.

Use `SettingsStateScreens.kt` with existing `GasStationBackground`, `GasStationTopBar`, `LoadingState`-style rows, and `GasStationGuidanceCard`. Add:

```xml
<string name="settings_loading">설정을 불러오는 중입니다.</string>
<string name="settings_load_failed_title">설정을 불러오지 못했습니다.</string>
<string name="settings_load_failed_body">저장된 설정을 다시 확인해 주세요.</string>
<string name="settings_retry">다시 시도</string>
<string name="settings_saving">저장 중</string>
<string name="settings_save_failed">설정을 저장하지 못했습니다. 다시 시도해 주세요.</string>
```

Add equivalent English strings.

Option rows use:

```kotlin
.testTag("$SETTINGS_OPTION_TAG_PREFIX${option.key}")
.clickable(
    enabled = !isSaving,
    role = Role.RadioButton,
    onClick = onClick,
)
```

- [ ] **Step 5: Run Settings unit and screenshot tests**

Run:

```bash
./gradlew \
  :feature:settings:testDebugUnitTest \
  :feature:settings:verifyRoborazziDebug \
  --warning-mode fail
```

Expected: PASS. If only the approved loading/saving surfaces change baselines, record and immediately re-verify:

```bash
./gradlew :feature:settings:recordRoborazziDebug
./gradlew :feature:settings:verifyRoborazziDebug
```

- [ ] **Step 6: Commit Settings readiness UI**

```bash
git add feature/settings
git commit -m "fix: acknowledge settings selection persistence"
```

### Task 4: Add Bidirectional Home And Settings Integration Coverage

**Files:**
- Modify: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `settings-option-<enum-name>` from Task 3.
- Consumes: existing station-list filter tags.
- Produces: a real navigation/DataStore synchronization test across two ViewModels.

- [ ] **Step 1: Add a failing connected synchronization test**

Add imports for `FuelType`, `SearchRadius`, `BrandFilter`, `SortOrder`, `UserPreferences`, `flow.first`, and `withTimeout`. Add:

```kotlin
@Test
fun demoSettingsAndNearby_sharePersistedPreferencesAcrossNavigationAndRecreation() {
    reseedDemoDatabase()
    waitForNearby()

    selectNearbyFilter("station-list-filter-radius", "station-list-filter-option-KM_5")
    selectNearbyFilter("station-list-filter-fuel", "station-list-filter-option-DIESEL")
    selectNearbyFilter("station-list-filter-brand", "station-list-filter-option-GSC")
    rule.onNodeWithText("거리순", useUnmergedTree = true).performClick()

    runBlocking {
        withTimeout(5_000) {
            settingsRepository.observeUserPreferences().first { preferences ->
                preferences.searchRadius == SearchRadius.KM_5 &&
                    preferences.fuelType == FuelType.DIESEL &&
                    preferences.brandFilter == BrandFilter.GSC &&
                    preferences.sortOrder == SortOrder.PRICE
            }
        }
    }

    rule.onNodeWithTag("bottom-nav-settings", useUnmergedTree = true).performClick()
    rule.onNodeWithText("5km").assertExists()
    rule.onNodeWithText("경유").assertExists()
    rule.onNodeWithText("GS칼텍스").assertExists()
    rule.onNodeWithText("가격순 보기").assertExists()

    selectSetting("settings-row-search-radius", "settings-option-KM_4")
    selectSetting("settings-row-fuel-type", "settings-option-GASOLINE")
    selectSetting("settings-row-brand-filter", "settings-option-ALL")
    selectSetting("settings-row-sort-order", "settings-option-DISTANCE")

    runBlocking {
        withTimeout(5_000) {
            settingsRepository.observeUserPreferences().first { preferences ->
                preferences.searchRadius == SearchRadius.KM_4 &&
                    preferences.fuelType == FuelType.GASOLINE &&
                    preferences.brandFilter == BrandFilter.ALL &&
                    preferences.sortOrder == SortOrder.DISTANCE
            }
        }
    }

    rule.onNodeWithTag("bottom-nav-nearby", useUnmergedTree = true).performClick()
    rule.onNodeWithText("4km", useUnmergedTree = true).assertExists()
    rule.onNodeWithText("휘발유", useUnmergedTree = true).assertExists()
    rule.onNodeWithText("전체", useUnmergedTree = true).assertExists()
    rule.onNodeWithText("거리순", useUnmergedTree = true).assertExists()

    rule.activityRule.scenario.recreate()
    rule.waitUntil(10_000) {
        rule.onAllNodesWithText("거리순", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}
```

Helper:

```kotlin
private fun selectNearbyFilter(chipTag: String, optionTag: String) {
    rule.onNodeWithTag(chipTag, useUnmergedTree = true).performClick()
    rule.onNodeWithTag(optionTag, useUnmergedTree = true).performClick()
}

private fun selectSetting(rowTag: String, optionTag: String) {
    rule.onNodeWithTag(rowTag, useUnmergedTree = true).performClick()
    rule.onNodeWithTag(optionTag, useUnmergedTree = true).performClick()
    rule.waitUntil(5_000) {
        rule.onAllNodesWithTag("settings-screen-list", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

private fun waitForNearby() {
    rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(
            "station-list-watch-toggle",
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
    }
}
```

- [ ] **Step 2: Compile the connected test and capture RED**

Run:

```bash
./gradlew :app:compileDemoDebugAndroidTestKotlin
```

Expected: compilation or selector assertion fails until Task 3's option tag and delayed navigation are wired correctly.

- [ ] **Step 3: Finish selector and synchronization wiring**

Ensure every Settings option uses the exact tag from Task 3 and Settings detail returns only after the committed effect. Keep the existing shared settings back-stack `ViewModelStoreOwner`; do not create a second ViewModel.

- [ ] **Step 4: Run connected demo coverage**

Run with one explicit device:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDemoDebugAndroidTest
```

Expected: PASS. Existing watchlist and popup dismissal flows also remain green.

Run the demo startup contract explicitly:

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests '*DemoSeedStartupHookTest*resets demo preferences*'
```

Expected: PASS. The test sets every preference away from default, invokes the
real synchronous startup hook, and asserts `UserPreferences.default()`. An
instrumentation test cannot restart its own target process and continue, so
also perform one external device smoke:

```bash
ADB=/Users/kws/Library/Android/sdk/platform-tools/adb
"$ADB" shell am force-stop com.gasstation.demo
"$ADB" shell monkey -p com.gasstation.demo -c android.intent.category.LAUNCHER 1
```

Expected: after the process start and granted permission, Nearby and Settings
show the documented demo defaults (`KM_3`, gasoline, all brands, distance,
TMAP). Record this as manual device evidence rather than an automated pass.

- [ ] **Step 5: Commit cross-screen coverage**

```bash
git add app/src/androidTest feature/settings
git commit -m "test: cover nearby settings synchronization"
```

### Task 5: Synchronize Live Contracts And Verify Plan 1

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: completed persistence/readiness behavior from Tasks 1–4.
- Produces: current documentation and repeatable verification commands.

- [ ] **Step 1: Update live documentation**

Document these exact rules:

```text
DataStore first emission is the readiness boundary. Nearby and Settings do not
render or act on UserPreferences.default() before that emission.

Preference mutation returns the value committed by DataStore. Settings detail
returns only after a successful mutation; failure retains the previous value.

Nearby builds StationQuery only when permission, GPS, coordinates, and
preferences are all ready.
```

Add the connected synchronization test to `docs/test-strategy.md` and the command to `docs/verification-matrix.md`.

- [ ] **Step 2: Run focused and broad verification**

Run:

```bash
./gradlew \
  :core:datastore:testDebugUnitTest \
  :domain:settings:test \
  :data:settings:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
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

Expected: both commands PASS. `assembleProdDebug` is keyless; a real prod
runtime launch still requires a user-local `opinet.apikey`. Do not add a
placeholder to tracked files and do not claim live Opinet verification.

- [ ] **Step 3: Review the final diff**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors; only Plan 1 files plus the user's pre-existing `settings.gradle.kts` and `gradle/gradle-daemon-jvm.properties` changes.

- [ ] **Step 4: Commit live contracts**

```bash
git add README.md docs/architecture.md docs/state-model.md docs/test-strategy.md docs/verification-matrix.md
git commit -m "docs: define settings readiness contract"
```
