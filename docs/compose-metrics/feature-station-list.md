# Compose Stability Metrics — feature:station-list

> 측정 환경: AGP 9.1.1 / Kotlin 2.3.20 / Compose Compiler 2.3.20
> 생성 명령: `./gradlew :feature:station-list:assembleDebug`
> 측정일: 2026-05-11

## Classes

```
unstable class com.gasstation.feature.stationlist.LocationStateMachine {
  unstable val getCurrentLocation: GetCurrentLocationUseCase
  unstable val getCurrentAddress: GetCurrentAddressUseCase
  unstable val observeAvailability: ObserveLocationAvailabilityUseCase
  unstable val mutableState: MutableStateFlow<LocationState>
  unstable val state: StateFlow<LocationState>
  <runtime stability> = Unstable
}
unstable class com.gasstation.feature.stationlist.LocationState {
  unstable val permissionState: LocationPermissionState
  stable val hasDeniedLocationAccess: Boolean
  stable val needsRecoveryRefresh: Boolean
  stable val isGpsEnabled: Boolean
  stable val isAvailabilityKnown: Boolean
  unstable val currentCoordinates: Coordinates?
  stable val currentAddressLabel: String?
  <runtime stability> = Unstable
}
unstable class com.gasstation.feature.stationlist.LocationAcquisitionResult.Success {
  unstable val coordinates: Coordinates
  <runtime stability> = Unstable
}
stable class com.gasstation.feature.stationlist.LocationAcquisitionResult.PermissionDenied {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.LocationAcquisitionResult.TimedOut {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.LocationAcquisitionResult.Unavailable {
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.stationlist.LocationAcquisitionResult.Error {
  unstable val throwable: Throwable
  <runtime stability> = Unstable
}
stable class com.gasstation.feature.stationlist.StationListAction.AutoRefreshRequested {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListAction.RefreshRequested {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListAction.RetryClicked {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListAction.SortToggleRequested {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListAction.WatchToggled {
  stable val stationId: String
  stable val watched: Boolean
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.stationlist.StationListAction.PermissionChanged {
  unstable val permissionState: LocationPermissionState
  <runtime stability> = Unstable
}
stable class com.gasstation.feature.stationlist.StationListAction.GpsAvailabilityChanged {
  stable val isEnabled: Boolean
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListAction.StationClicked {
  stable val station: StationListItemUiModel
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListBannerModel {
  stable val titleResId: Int
  stable val detailResId: Int?
  stable val detailArg: String?
  stable val tone: StationListBannerTone
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListEffect.OpenExternalMap {
  stable val provider: MapProvider
  stable val stationName: String
  stable val originLatitude: Double?
  stable val originLongitude: Double?
  stable val latitude: Double
  stable val longitude: Double
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListEffect.OpenLocationSettings {
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.stationlist.StationListEffect.ShowSnackbar {
  unstable val message: StringResource
  <runtime stability> = Unstable
}
stable class com.gasstation.feature.stationlist.StationListFailureReason.LocationTimedOut {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListFailureReason.LocationFailed {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListFailureReason.RefreshTimedOut {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListFailureReason.RefreshFailed {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListItemUiModel {
  stable val id: String
  stable val name: String
  stable val brand: Brand
  stable val brandLabel: String
  stable val priceLabel: String
  stable val distanceLabel: String
  stable val priceNumberLabel: String
  stable val priceUnitLabel: String
  stable val distanceNumberLabel: String
  stable val distanceUnitLabel: String
  stable val priceDeltaLabel: String
  stable val priceDeltaTone: PriceDeltaTone
  stable val isWatched: Boolean
  stable val latitude: Double
  stable val longitude: Double
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListBodyState.PermissionRequired {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListBodyState.GpsRequired {
  <runtime stability> = Stable
}
stable class com.gasstation.feature.stationlist.StationListBodyState.InitialLoading {
  <runtime stability> = Stable
}
runtime class com.gasstation.feature.stationlist.StationListBodyState.Failure {
  runtime val reason: StationListFailureReason
  <runtime stability> = Uncertain(StationListFailureReason)
}
stable class com.gasstation.feature.stationlist.StationListBodyState.Results {
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.stationlist.StationListUiState {
  unstable val currentCoordinates: Coordinates?
  stable val currentAddressLabel: String?
  unstable val permissionState: LocationPermissionState
  stable val hasDeniedLocationAccess: Boolean
  stable val needsRecoveryRefresh: Boolean
  stable val isGpsEnabled: Boolean
  stable val isAvailabilityKnown: Boolean
  stable val isLoading: Boolean
  stable val isRefreshing: Boolean
  stable val isStale: Boolean
  runtime val blockingFailure: StationListFailureReason?
  unstable val stations: List<StationListItemUiModel>
  stable val selectedBrandFilter: BrandFilter
  stable val selectedRadius: SearchRadius
  stable val selectedFuelType: FuelType
  stable val selectedSortOrder: SortOrder
  unstable val lastUpdatedAt: Instant?
  <runtime stability> = Unstable
}
unstable class com.gasstation.feature.stationlist.StationListViewModel {
  unstable val searchOrchestrator: StationSearchOrchestrator
  unstable val updateWatchState: UpdateWatchStateUseCase
  unstable val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase
  unstable val locationStateMachine: LocationStateMachine
  unstable val stationEventLogger: StationEventLogger
  unstable val preferences: MutableStateFlow<UserPreferences>
  unstable val transientState: MutableStateFlow<StationListTransientState>
  unstable val mutableUiState: MutableStateFlow<StationListUiState>
  unstable val mutableEffects: MutableSharedFlow<StationListEffect>
  unstable val uiState: StateFlow<StationListUiState>
  unstable val effects: SharedFlow<StationListEffect>
  <runtime stability> = Unstable
}
unstable class com.gasstation.feature.stationlist.StationSearchOrchestrator {
  unstable val observeNearbyStations: ObserveNearbyStationsUseCase
  unstable val refreshNearbyStations: RefreshNearbyStationsUseCase
  unstable val mutableActiveQueryState: MutableStateFlow<ActiveStationQueryState>
  unstable val mutableSearchResult: MutableStateFlow<StationSearchResult>
  unstable val mutableBlockingFailure: MutableStateFlow<StationListFailureReason?>
  unstable val pendingBlockingFailure: MutableStateFlow<PendingBlockingFailure?>
  unstable val activeQueryState: StateFlow<ActiveStationQueryState>
  unstable val searchResult: StateFlow<StationSearchResult>
  unstable val blockingFailure: StateFlow<StationListFailureReason?>
  <runtime stability> = Unstable
}
unstable class com.gasstation.feature.stationlist.ActiveStationQueryState {
  unstable val query: StationQuery?
  stable val cacheState: CachedSnapshotState
  <runtime stability> = Unstable
}
stable class com.gasstation.feature.stationlist.RefreshOutcome.Success {
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.stationlist.RefreshOutcome.Failed {
  unstable val reason: StationRefreshFailureReason?
  <runtime stability> = Unstable
}
```

## Composables

```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.StationListRoute(
  stable onSettingsClick: Function0<Unit>
  stable onWatchlistClick: Function1<Coordinates, Unit>
  stable onOpenExternalMap: Function1<OpenExternalMap, Unit>
  unstable viewModel: StationListViewModel? = @dynamic <expression>
)
fun com.gasstation.feature.stationlist.rememberLocationPermissionsState()
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.StationListScreen(
  unstable uiState: StationListUiState
  stable snackbarHostState: SnackbarHostState
  stable onAction: Function1<StationListAction, Unit>
  stable onRequestPermissions: Function0<Unit>
  stable onOpenLocationSettings: Function0<Unit>
  stable onSettingsClick: Function0<Unit>
  stable onWatchlistClick: Function0<Unit>? = @static null
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.SortToggleTitle(
  stable sortOrder: SortOrder
  stable onClick: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.SortToggleSegment(
  stable label: String
  stable selected: Boolean
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.StationListContent(
  unstable uiState: StationListUiState
  stable onAction: Function1<StationListAction, Unit>
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.QueryContextSummary(
  unstable uiState: StationListUiState
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.StationCard(
  stable station: StationListItemUiModel
  stable fuelTypeLabel: String
  stable modifier: Modifier? = @static <expression>
  stable onClick: Function0<Unit>
  stable onWatchToggle: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.PriceDeltaIndicator(
  stable label: String
  stable tone: PriceDeltaTone
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.FuelChip(
  stable text: String
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.WatchToggleButton(
  stable watched: Boolean
  stable onClick: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.PermissionRequired(
  stable modifier: Modifier? = @static <expression>
  stable onRequestPermissions: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.GpsRequired(
  stable modifier: Modifier? = @static <expression>
  stable onOpenLocationSettings: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.LoadingState(
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.FailureState(
  reason: StationListFailureReason
  stable onAction: Function1<StationListAction, Unit>
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.EmptyState(
  stable onAction: Function1<StationListAction, Unit>
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.feature.stationlist.BrandedStateContainer(
  stable modifier: Modifier? = @static <expression>
  stable content: @[ExtensionFunctionType] Function3<BoxScope, Composer, Int, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.StationListResultsPane(
  unstable uiState: StationListUiState
  stable onAction: Function1<StationListAction, Unit>
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.stationlist.RefreshingStatusRail(
  stable modifier: Modifier? = @static <expression>
)
fun com.gasstation.feature.stationlist.toStateDescription(
  stable <this>: SortOrder
): String
fun com.gasstation.feature.stationlist.toNextSortActionLabel(
  stable <this>: SortOrder
): String
fun com.gasstation.feature.stationlist.toLabel(
  stable <this>: FuelType
): String
fun com.gasstation.feature.stationlist.toFailureCardContent(
  <this>: StationListFailureReason
): StationListFailureCardContent
```

## Unstable 분류 대응

- `StationListItemUiModel`: 모든 필드가 stable — 현재 stable 상태 유지, 추가 조치 불필요.
- `StationListBannerModel`: 모든 필드가 stable — 현재 stable 상태 유지.
- `StationListUiState`: `Coordinates?`(외부 도메인 타입), `LocationPermissionState`(외부), `List<StationListItemUiModel>`, `Instant?`(kotlinx.datetime) 때문에 unstable로 분류됩니다. `StationListUiState`는 ViewModel의 `StateFlow`에서만 방출되며 UI에 통째로 전달되므로 전체 recompose 비용이 실제로 발생하지 않습니다. 이 baseline 단계에서는 kept as-is로 유지합니다.
- `StationListEffect.ShowSnackbar`: `StringResource` sealed interface의 `FromId` 변형이 unstable이기 때문. `ShowSnackbar`는 one-shot effect로 Compose recomposition 트리 바깥에서 소비되므로 실질적 영향 없음 — kept as-is.
- `LocationStateMachine`, `StationListViewModel`, `StationSearchOrchestrator`: ViewModel/의존성 주입 클래스이며 Compose 트리에 직접 전달되지 않음 — unstable 그대로 유지.
- `LocationPermissionState`(외부 라이브러리 accompanist), `Coordinates`(domain 모델), `Instant`(kotlinx.datetime): 외부 타입이므로 `@Stable` 주입보다 모듈 경계 유지 우선 — kept as-is.
