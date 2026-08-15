# 상태 모델

이 문서는 GasStation의 상태 원천과 lifecycle 판단을 설명하는 단일 출처입니다. 상태는 여러 층으로 나눠서 보면 가장 덜 헷갈리고, 여기서는 각 상태가 어디서 만들어지고 얼마나 오래 유지되는지 설명합니다.

## 상태 층 요약

| 층 | 대표 타입 | 유지 범위 | 역할 |
| --- | --- | --- | --- |
| 영속 선호 상태 | `UserPreferences` | 프로세스 재시작 이후에도 유지 | 반경, 유종, 브랜드, 정렬, 지도 앱 |
| 목록 위치 상태 | `LocationStateMachine` | `StationListViewModel` 생존 동안만 유지 | 권한, GPS availability, 현재 좌표, 주소 라벨, recovery refresh flag |
| navigation 좌표 payload | `GasStationNavHost` | app navigation graph 생존 동안 유지 | 관심 tab 활성화와 watchlist 거리 기준 route |
| 목록 검색 상태 | `StationSearchOrchestrator` | `StationListViewModel` 생존 동안만 유지 | active query, cache snapshot state, observed search result, pending blocking refresh failure |
| 목록 refresh 상태 | `RefreshCoordinator` | `StationListViewModel` 생존 동안만 유지 | 단일 in-flight refresh work, work identity, loading/refreshing flag, active refresh query |
| 목록 UI 조합 상태 | `StationListStateAssembler` | 조합 결과는 `StationListViewModel` 생존 동안만 유지 | immutable collaborator snapshot을 최종 `StationListUiState`로 순수 투영 |
| 목록 조정 경계 | `StationListViewModel` | ViewModel 생존 동안만 유지 | collaborator lifecycle 수집, action/result routing, assembler 결과 게시 |
| 저장소 읽기 모델 | `StationSearchResult`, `WatchedStationSummary` | Room/DataStore/원격 데이터에서 다시 계산 가능 | 화면에 보여줄 데이터 조합 |
| 설정 화면 파생 상태 | `SettingsUiState` | `UserPreferences`로부터 항상 재생성 가능 | 요약 라벨과 선택 옵션 |
| 승인 대기 UI command | `StationListCommandQueue` | `StationListViewModel` 생존 동안만 유지 | snackbar, 위치 설정 열기, 외부 지도 열기를 FIFO로 보관하고 exact-head ID로 승인 |
| 구조화 이벤트 | `StationEvent` | 영속 상태가 아니라 관찰/진단용 로그 | `domain:station` 계약으로 watch toggle, refresh/location 실패, retry 결과 같은 앱 이벤트 표현 |
| 비치명 예외 보고 | `CrashReporter` | 영속 상태가 아니라 관찰/진단용 로그 | `core:observability` 계약으로 예상하지 못한 nonfatal exception 보고 |

## 1. 영속 선호 상태

영속 상태의 기준 원천은 `UserPreferences` 하나입니다.

- 저장 위치: `core:datastore`
- 저장소 구현: `data:settings`
- 사용 위치: 목록 조회 파이프라인, 관심 화면의 선택 유종 context, 설정 화면, 외부 지도 선택

`core:datastore`는 domain model을 직접 저장하지 않고 `StoredUserPreferences` string DTO를 직렬화합니다. `data:settings`가 이 storage DTO를 domain `UserPreferences`로 매핑하고, 알 수 없는 enum name은 domain default로 되돌립니다.

DataStore의 첫 emission이 선호값 readiness 경계입니다. Nearby와 Settings는 그 emission 전 `UserPreferences.default()`를 렌더링하거나 사용자 action에 사용하지 않습니다. `UserPreferences.default()`는 새 저장소의 storage fallback과 demo seed 초기화에만 쓰이며, 기존 사용자의 화면 초기값을 추정하는 값이 아닙니다.

포함하는 값:

- `searchRadius`
- `fuelType`
- `brandFilter`
- `sortOrder`
- `mapProvider`

`demo` flavor는 예외가 하나 있습니다. `DemoSeedStartupHook`이 앱 시작 시 `UserPreferences.default()`로 다시 덮어써 검토 시작 상태를 항상 고정합니다.

`mapProvider`의 현재 Kakao identity는 `KAKAO_MAP`입니다. storage-local legacy 값 `KAKAO_NAVI`는 `data:settings` 읽기 경계에서 `KAKAO_MAP`으로 복원되고, 다음 쓰기는 현재 이름만 저장합니다.

## 2. 목록 런타임 상태

목록 화면의 런타임 상태는 하나의 거대한 reducer가 아니라 여섯 소유 경계로 나뉩니다.

- `LocationStateMachine`: permission, GPS availability, current coordinates, address label, recovery refresh flag와 네 generation을 소유합니다.
- `StationSearchOrchestrator`: active query/session, cache snapshot state, observed search result, observation failure와 pending blocking failure를 소유합니다.
- `RefreshCoordinator`: 위치 획득, 최신 eligible query 재검증, 단일 refresh job/work identity, loading/refreshing 상태, `RefreshNearbyStationsUseCase` 호출을 소유합니다.
- `StationListCommandQueue`: ViewModel-lifetime immutable FIFO snapshot과 exact-head acknowledgement를 소유합니다.
- `projectStationSearchResult`와 `StationListStateAssembler`: domain 결과의 UI list identity 투영과 typed immutable input의 최종 field/body 투영을 각각 소유합니다.
- 얇은 `StationListViewModel`: preference/search/state publication lifecycle, preference mutation admission, action routing, typed coordinator-result 번역, foreground GPS suspend bridge를 소유합니다.

ViewModel은 location/address generation, refresh work identity, search retry/cache/failure policy, field/body projection, command retention/acknowledgement, watch latest-intent 직렬화를 다시 구현하지 않습니다.

이 값들은 저장되지 않습니다. 화면을 떠나면 사라지고, 앱 재시작 후 복원 대상도 아닙니다.

### 위치 관련 분기

- 위치 availability는 `domain:location.ObserveLocationAvailabilityUseCase`를 통해 ViewModel로 들어오고, route는 foreground 구간에서만 이 흐름을 수집합니다.
- 현재 위치 조회는 평상시 구독이 아니라 새로고침 시점에만 `domain:location.GetCurrentLocationUseCase`를 호출합니다.
- `LocationStateMachine`은 permission, GPS, location request, address request generation을 하나의 synchronized boundary에서 관리합니다. provider 호출은 lock 밖에서 suspend하고, 돌아온 뒤 cancellation 확인과 active-token 검증·visible-state commit을 원자적으로 수행합니다.
- 실제 permission/GPS 전이와 새 요청은 이전 work를 무효화합니다. 같은 값 callback은 generation을 올리지 않지만 equal-coordinate와 away/back ABA를 포함한 늦은 완료는 `LocationAcquisitionResult.Superseded`가 되어 failure, command, refresh, analytics를 만들지 않습니다.
- 주소 라벨은 `resolveAddressLabel(coordinates)` 한 연산이 조회와 조건부 commit을 함께 소유합니다. 성공한 위치 획득 뒤 caller scope에서 non-blocking으로 시작하므로 주유소 refresh나 indicator 종료를 기다리게 하지 않고, 늦은 geocoding commit은 address generation이 거부합니다. API 33 이상 지오코더 callback 오류는 주소 조회 `Error`, 빈 성공 결과는 `Unavailable`로만 들어오며 cancellation은 상태로 저장하지 않고 전파합니다.
- `demo`와 `prod`는 같은 permission state machine을 통과합니다. denied는 먼저 `currentCoordinates`, address label, recovery refresh flag를 비우고 permission guidance를 body state의 최우선으로 만듭니다. 특히 `PreciseGranted -> ApproximateGranted` 전이도 보존된 정밀 좌표·주소·recovery state를 지워 privacy downgrade를 적용합니다. 따라서 demo override, retained coordinate, cached station list, auto/manual refresh가 denial이나 downgrade를 우회하지 않습니다.
- `demo`에서는 `DemoLocationOverride`가 availability를 사용 가능으로 만들되, approximate 또는 precise grant 뒤에만 고정 좌표를 공급합니다. `prod`에서는 같은 repository 경계가 Android provider 결과를 `LocationLookupResult`로 변환하므로, ViewModel이 success, timeout, unavailable, permission denied, error를 구분해 처리합니다.
- GPS 상태는 resume 시점 단발 확인이 아니라 availability flow를 통해 화면이 foreground인 동안 계속 반영됩니다. GPS 비활성화는 permission denial과 다른 상태입니다. permission이 granted일 때만 GPS 안내가 location settings command를 열고, denied 상태의 새로고침은 permission guidance/snackbar를 유지합니다.
- 권한 dialog는 explicit-action 전용입니다. `StationListRoute`는 입장 시 자동 요청하지 않고 안내 CTA에서만 요청합니다. 같은 route 세션에서 terminal denial이 반복되면 CTA는 Android app settings로 전환됩니다. 이 request-count UI 정책은 cold launch나 프로세스 재시작을 넘어 영속되는 marker가 아닙니다.

### 검색 관찰 복구

Repository exception과 정상 completion은 active non-null `ObservationSession(query, retryGeneration)` 내부에서 observation failure로 바뀌며, 바깥 query/retry collector는 살아 있습니다. `CancellationException`은 상태로 바꾸지 않고 다시 던집니다. 상태 commit은 현재 active query와 exact session identity가 모두 일치할 때만 허용됩니다.

실제 query 변경은 이전 query의 unkeyed result와 failure를 즉시 지우고 cache state를 `Unknown`으로 돌린 뒤 새 emission을 기다립니다. 같은 query의 실패 상태에서만 `retryObservation()`이 retry generation을 하나 올리고 기존 result/cache/blocking-failure snapshot을 유지한 채 repository 관찰을 다시 구독합니다. 이 복구는 remote refresh를 시작하지 않으며, healthy/null-query 호출은 no-op입니다. 캐시 의미는 계속 `hasCachedSnapshot`만 사용합니다.

## 3. 저장소 읽기 모델

목록 화면은 세션 상태만으로 그려지지 않습니다. ViewModel은 다음 입력을 typed snapshot으로 결합하고, `StationListStateAssembler`가 그 snapshot을 `StationListUiState`로 투영합니다.

- `UserPreferences`
- `LocationStateMachine`과 `StationSearchOrchestrator`가 소유한 목록 런타임 상태
- `RefreshCoordinator`가 소유한 refresh work와 indicator 상태
- `StationSearchResult`에서 만든 `StationListSearchProjection`
- `StationSearchOrchestrator.blockingFailure`와 `StationListCommandQueue.commands`

Nearby는 permission, GPS availability, 현재 좌표, loaded `UserPreferences`가 모두 준비된 뒤에만 `StationQuery`를 만듭니다. permission이 denied로 바뀌면 cached `StationSearchResult`가 남아 있어도 permission guidance가 먼저 렌더링되고, 좌표 없는 자동/수동 refresh는 시작하지 않습니다. 현재 좌표가 유지된 상태에서 `UserPreferences`의 반경, 유종, 브랜드, 정렬 조건이 바뀌면 `RefreshCoordinator.requiresRefresh`가 새 조건 refresh 필요 여부를 판단합니다. coordinator는 exact work를 publish하고 lazy job을 시작하기 전에 completion cleanup을 등록합니다. `RefreshStarting(query)`은 remote I/O 전에 orchestrator query를 활성화하고, 위치 획득 뒤·refresh 직전·terminal delivery 직전에 latest eligible query를 다시 확인합니다. 모든 cleanup은 exact identity를 확인하므로 stale completion이 replacement work를 지울 수 없습니다. 결과는 별도 channel 없이 inline suspending callback으로 전달되고 superseded/cancelled terminal은 analytics·blocking failure·command 없이 끝납니다. 브랜드 필터와 정렬은 캐시 키에 들어가지 않지만 `StationQuery`와 읽기 모델에는 포함되므로, UI는 즉시 새 조건으로 다시 계산되고 원격 성공 시 스냅샷도 최신화됩니다.

목록 좌표는 app에 navigation payload로도 전달됩니다. 이는 watchlist의 거리 기준과 관심 tab 활성화에만 쓰이며, 검색 정책이나 위치 세션의 소유권을 app으로 옮기지 않습니다. 좌표가 바뀌면 이전 concrete watchlist route를 제거해 restore가 stale 좌표를 재사용하지 않게 합니다.

`projectStationSearchResult`는 `StationSearchResult.stations` source list가 같고 freshness나 metadata만 바뀐 emission에서 기존 `StationListItemUiModel` list identity를 재사용합니다. assembler는 이미 매핑된 station list와 FIFO command list를 복사·정렬·필터링하지 않고 그대로 전달합니다. 이 identity 최적화는 assembler 앞의 순수 search projection이 소유하며, 캐시 존재 여부나 blocking failure 의미를 바꾸지 않습니다.

`StationSearchResult`의 의미:

| 필드 | 의미 |
| --- | --- |
| `stations` | 목록 카드로 보여줄 도메인 엔트리 |
| `freshness` | `Fresh` 또는 `Stale` |
| `fetchedAt` | 마지막 성공 스냅샷 시각 |
| `hasCachedSnapshot` | 현재 쿼리 버킷에 스냅샷 마커가 존재하는지 여부 |

여기서 중요한 점은 `hasCachedSnapshot`이 별도 필드라는 것입니다. 이 값 덕분에 다음 두 상태를 구분할 수 있습니다.

- 성공적으로 조회했지만 결과가 0건인 상태
- 아직 캐시 자체가 없어 아무 것도 보여줄 수 없는 상태

즉 "빈 결과"와 "실패 + 캐시 없음"은 같은 상태가 아닙니다.

assembler는 repository의 `hasCachedSnapshot`을 UI state에 그대로 전달하고, `hasCachedSnapshot == true`이면서 `freshness == Stale`일 때만 stale content로 표시합니다. 따라서 초기 no-cache sentinel의 `Stale`은 stale 배너를 만들지 않습니다. body 우선순위는 permission -> GPS -> preference failure -> preference loading -> no-snapshot blocking failure -> no-snapshot initial loading -> results입니다. marker가 있는 빈 목록은 refresh 중에도 settled results/EmptyState이고, snapshot이 없는 상태를 성공한 빈 결과로 만들지 않습니다. `StationListFirstContentPolicy`는 이 body 선택을 통과한 `Results`를 바로 usable content로 봅니다.

## 4. blocking failure 규칙

목록 화면이 전면 실패로 전환되는 기준은 `StationListUiState.blockingFailure`입니다.

- 캐시 스냅샷이 있으면:
  새로고침이나 위치 조회가 실패해도 기존 결과를 유지하고 snackbar만 보여줍니다.
- 캐시 스냅샷이 없으면:
  `LocationTimedOut`, `LocationFailed`, `RefreshTimedOut`, `RefreshFailed` 중 하나가 blocking failure로 올라갑니다.

이 정책은 `StationSearchOrchestrator`가 `PendingBlockingFailure`와 `CachedSnapshotState`를 사용해 구현합니다. 실패가 먼저 들어오고 저장소 관찰 결과가 나중에 도착할 수 있기 때문에, orchestrator는 "지금 정말 캐시가 없는가"를 확인한 뒤에만 전면 실패를 확정하고, ViewModel은 그 결과를 최종 `StationListUiState`에 반영합니다.

## 5. watchlist 상태

`feature:watchlist`는 별도 세션 리듀서를 두지 않습니다.

- 기준 좌표는 `SavedStateHandle`에서 읽습니다.
- `ObserveUserPreferencesUseCase`의 첫 emission을 기다린 뒤 현재 `fuelType`과 기준 좌표로 `WatchlistQuery`를 만들고, `ObserveWatchlistUseCase` 결과를 `WatchlistUiState`로 바꿉니다.
- 선택 유종의 캐시와 히스토리만 가격/변화 계산에 사용합니다. 둘 다 없으면 저장 identity를 유지한 nullable price를 `선택 유종 가격 없음`으로 투영합니다.
- 반경, 브랜드 필터, Nearby 정렬은 관심 목록의 포함 여부와 watched-time 순서를 바꾸지 않습니다.
- `domain:station`의 두 watch mutation은 `WatchMutationResult.Committed` 또는 `Superseded`를 반환합니다. `data:station`의 공유 `LatestWatchIntentGate`가 station ID별 ON/OFF/remove intent를 직렬화하고 entry identity와 participant tombstone으로 stale-ticket ABA와 premature reuse를 막습니다. superseded mutation은 DAO를 호출하지 않으며 cancellation/DAO exception을 결과로 숨기지 않습니다.
- watch ON은 Room `INSERT IGNORE`를 사용해 반복 ON에서도 처음 `watchedAtEpochMillis`를 보존합니다. 두 watch 관찰 query는 `watchedAtEpochMillis DESC, stationId ASC` 순서를 공유합니다.
- Nearby와 watchlist는 `Committed` 결과에서만 `WatchToggled`를 기록합니다. superseded/cancelled work는 silent입니다.
- 첫 settings/watchlist emission 전에는 `isLoading`, 관찰 실패 뒤에는 `loadFailed`가 상태를 소유합니다. retry action은 settings와 watchlist 관찰을 함께 다시 시작합니다.
- 권한, GPS, 새로고침 플래그, snackbar undo는 다시 들고 있지 않습니다.

즉 watchlist 화면은 "어떤 좌표와 선택 유종으로 저장 항목을 비교할지"와 "저장 항목 요약이 무엇인지"만 알면 됩니다.

## 6. 설정 화면 상태

설정 화면은 별도 복잡한 세션 상태를 거의 만들지 않습니다.

- `SettingsUiState`는 `UserPreferences`를 화면용 라벨/옵션으로 투영한 값입니다.
- `SettingsRoute`와 `SettingsDetailRoute`는 route가 다르지만 같은 `SettingsViewModel`을 공유합니다.
- DataStore의 첫 emission 전에는 `Loading`만 노출하며 선택 action을 받지 않습니다. 따라서 기존 저장값이 아직 도착하지 않은 상태에서 `UserPreferences.default()`가 선택값으로 보이지 않습니다.
- 사용자가 항목을 선택하면 `UpdateFuelTypeUseCase`, `UpdateSearchRadiusUseCase`, `UpdateBrandFilterUseCase`, `UpdateMapProviderUseCase`, `UpdatePreferredSortOrderUseCase` 같은 명시적 설정 유스케이스가 호출됩니다. mutation은 DataStore가 commit한 `UserPreferences`를 반환합니다.
- Settings detail은 성공한 mutation의 committed value를 받은 뒤에만 돌아갑니다. mutation이 실패하면 이전 committed value를 유지하고 failure effect를 냅니다.

즉 설정 화면은 "영속 상태를 편집하는 얇은 UI 계층"에 가깝습니다.

## 7. 승인 대기 UI command

프로세스 재시작 이후 복원하지 않지만 route collector가 잠시 없어도 잃으면 안 되는 반응은 ViewModel-lifetime FIFO queue에 둡니다.

- `StationListCommandPayload.ShowSnackbar(message: StringResource)` — i18n을 위해 `String`이 아닌 `StringResource`를 보유. Compose 레이어에서 `message.resolve(context)`로 표시.
- `StationListCommandPayload.OpenLocationSettings`
- `StationListCommandPayload.OpenExternalMap`

`StationListCommandQueue`는 `StationListUiState.pendingCommands`에 immutable snapshot을 ID 순 FIFO로 보관합니다. `StationListCommandEffect` lifecycle handler가 현재 head의 suspend handler를 정상 완료하고 coroutine이 여전히 active인 것을 확인한 뒤 exact head ID를 승인할 때만 제거합니다. tail, stale, foreign, repeated, zero ID는 no-op입니다. 처리 중 cancellation이나 예외는 head를 남기고, 실패한 head는 한 START activation에서 한 번만 시도한 뒤 다음 START/route 재부착에서 재시도합니다. 정상 승인이 다음 head를 드러냅니다.

따라서 외부 side effect의 경계는 at-least-once이며 정확히 한 번의 외부 실행을 약속하지 않습니다. queue는 `SavedStateHandle`이나 영속 저장소에 기록하지 않으므로 process death/task removal 복원도 보장하지 않습니다. 외부 지도 command가 enqueue될 때 ViewModel이 `ExternalMapOpened`를 한 번 기록하고 route retry/acknowledgement는 다시 기록하지 않습니다. app은 현재 `mapProvider`의 명시적 package route를 시도한 뒤 app route -> Play Store app URI -> HTTPS Store 순으로 fallback하고, 최종 실패는 feature callback을 통해 snackbar command로 돌아옵니다.

## 8. 구조화 이벤트

`StationEvent`는 화면 복원용 상태가 아니라 관찰과 진단을 위한 계약입니다.
이벤트 종류와 payload는 `domain:station`이 소유하고, 예상하지 못한 비치명 예외 보고 계약은 `core:observability`의 `CrashReporter`가 소유합니다.

- `WatchToggled`는 북마크 저장/해제 mutation이 `Committed`로 끝난 경우에만 emit됩니다.
- `SearchRefreshed`는 저장소 refresh가 성공적으로 스냅샷과 히스토리를 저장한 뒤 emit됩니다.
- `CompareViewed`는 watchlist ViewModel 생존 동안 비교 데이터가 처음 표시될 때 한 번 emit됩니다.
- `ExternalMapOpened`는 외부 지도 앱 handoff가 요청될 때 emit됩니다. 실제 외부 앱 실행 성공 여부가 아니라 요청 이벤트입니다.
- `RefreshFailed`는 원격 refresh 최종 실패가 feature에 도착했을 때 emit됩니다.
- `LocationFailed`는 위치 획득 실패 유형을 feature가 분류해 emit합니다.
- `RetryAttempted`는 `data:station`의 retry 정책이 두 번째 시도를 마쳤을 때 emit합니다.

## Station-list 결정적 상태 계약

아래 JSON은 station-list concurrency와 책임 분리의 기계 판독 기준입니다. 사람이 읽는 설명과 validator가 같은 값을 보도록 별도 구조화 source를 그대로 렌더링하며, 실행 시점의 테스트 수·시간·기기 상태는 넣지 않습니다.

<!-- station-list-state-contract:start -->
```json
{
  "schemaVersion": 1,
  "contractId": "station-list-state-concurrency-v1",
  "location": {
    "owner": "LocationStateMachine",
    "generations": ["permission", "gps", "locationRequest", "addressRequest"],
    "providerBoundary": "suspend_outside_lock_then_active_check_and_atomic_commit",
    "precisionDowngrade": "clear_coordinates_address_and_recovery",
    "superseded": "normal_silent"
  },
  "observation": {
    "owner": "StationSearchOrchestrator",
    "failureBoundary": "inside_active_query_session",
    "normalCompletion": "observation_failed",
    "cancellation": "rethrow",
    "sameQueryRetry": "resubscribe_without_remote_refresh",
    "queryChange": "clear_old_unkeyed_result_and_failures",
    "cacheEvidence": "hasCachedSnapshot"
  },
  "watch": {
    "owner": "LatestWatchIntentGate",
    "domainResult": "WatchMutationResult",
    "key": "stationId",
    "sharedOperations": ["updateWatchState", "removeWatchedStation"],
    "onConflict": "insert_ignore_preserves_original_watchedAt",
    "ordering": ["watchedAtEpochMillis DESC", "stationId ASC"],
    "superseded": "normal_silent"
  },
  "command": {
    "owner": "StationListCommandQueue",
    "stateField": "StationListUiState.pendingCommands",
    "delivery": "viewmodel_lifetime_fifo",
    "acknowledgement": "exact_head_after_normal_handler_completion_and_active_check",
    "handlerFailure": "retain_head_for_next_start_or_attachment",
    "externalSideEffect": "at_least_once",
    "processDeathPersistence": "not_promised"
  },
  "refresh": {
    "owner": "RefreshCoordinator",
    "work": "single_exact_identity_job",
    "completion": "registered_before_start_and_identity_guarded",
    "query": "revalidate_before_refresh_and_terminal_delivery",
    "address": "caller_scope_nonblocking_after_successful_acquisition",
    "resultDelivery": "inline_suspending_callback",
    "superseded": "normal_silent"
  },
  "projection": {
    "owner": "StationListStateAssembler",
    "input": "StationListStateInputs",
    "purity": ["no_io", "no_coroutines", "no_clock", "no_logging", "no_mutation"],
    "cacheMarker": "hasCachedSnapshot",
    "listIdentityOwner": "projectStationSearchResult"
  },
  "viewModel": {
    "owner": "StationListViewModel",
    "responsibilities": [
      "viewmodel_lifetime_collection",
      "preference_read_write_admission",
      "action_routing",
      "typed_result_translation",
      "assembler_publication",
      "foreground_gps_suspend_bridge"
    ],
    "forbiddenResponsibilities": [
      "location_or_address_generation",
      "refresh_job_or_work_identity",
      "search_session_retry_or_cache_failure_policy",
      "ui_field_projection_or_body_precedence",
      "command_retention_or_acknowledgement_policy",
      "watch_latest_intent_serialization",
      "one_shot_effect_stream"
    ]
  },
  "verification": {
    "primary": "host_coroutine_room_robolectric_and_app_graph",
    "connectedDeviceRequired": false,
    "connectedDeviceEvidence": "not_claimed"
  }
}
```
<!-- station-list-state-contract:end -->

이 계약을 바꿀 때는 구조화 source와 이 block을 함께 수정하고, 여덟 consumer는 정책을 재서술하지 말고 이 heading을 참조합니다. 실제 Kotlin owner와 host proof boundary가 먼저 바뀌어 있어야 하며 문서 validator와 상태 집중 회귀를 같은 변경에서 갱신합니다.

## 상태 경계 한 줄 요약

- 오래 유지되는 사용자 선택: `UserPreferences`
- 실행 중 위치 환경: `LocationStateMachine`
- 실행 중 검색/cache/failure 판단: `StationSearchOrchestrator`
- 실행 중 단일 위치-새로고침 work: `RefreshCoordinator`
- 실행 중 승인 대기 FIFO: `StationListCommandQueue`
- 목록 화면 action/result routing과 flow lifecycle: `StationListViewModel`
- immutable snapshot의 최종 UI 조합: `StationListStateAssembler`
- 화면에 그릴 데이터 조합: `StationSearchResult`, `WatchedStationSummary`
- 설정 화면 라벨과 옵션: `SettingsUiState`
- 승인 뒤 제거할 FIFO 반응: `StationListCommandQueue`, `StationListCommandPayload`
- 복원하지 않는 관찰 이벤트: `StationEvent`
