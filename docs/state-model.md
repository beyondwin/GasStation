# 상태 모델

이 문서는 GasStation의 상태 원천과 lifecycle 판단을 설명하는 단일 출처입니다. 상태는 여러 층으로 나눠서 보면 가장 덜 헷갈리고, 여기서는 각 상태가 어디서 만들어지고 얼마나 오래 유지되는지 설명합니다.

## 상태 층 요약

| 층 | 대표 타입 | 유지 범위 | 역할 |
| --- | --- | --- | --- |
| 영속 선호 상태 | `UserPreferences` | 프로세스 재시작 이후에도 유지 | 반경, 유종, 브랜드, 정렬, 지도 앱 |
| 목록 위치 상태 | `LocationStateMachine` | `StationListViewModel` 생존 동안만 유지 | 권한, GPS availability, 현재 좌표, 주소 라벨, denied-access, recovery refresh flag |
| 목록 검색 상태 | `StationSearchOrchestrator` | `StationListViewModel` 생존 동안만 유지 | active query, cache snapshot state, observed search result, pending blocking refresh failure |
| 목록 UI 조합 상태 | `StationListViewModel` | `StationListViewModel` 생존 동안만 유지 | loading flag, 사용자 action dispatch, one-shot effect, 최종 `StationListUiState` 조합 |
| 저장소 읽기 모델 | `StationSearchResult`, `WatchedStationSummary` | Room/DataStore/원격 데이터에서 다시 계산 가능 | 화면에 보여줄 데이터 조합 |
| 설정 화면 파생 상태 | `SettingsUiState` | `UserPreferences`로부터 항상 재생성 가능 | 요약 라벨과 선택 옵션 |
| 단발성 UI effect | `StationListEffect` | 한 번 소비하고 버림 | snackbar, 위치 설정 열기, 외부 지도 열기 |
| 구조화 이벤트 | `StationEvent` | 영속 상태가 아니라 관찰/진단용 로그 | watch toggle, refresh/location 실패, retry 결과 같은 앱 이벤트 |

## 1. 영속 선호 상태

영속 상태의 기준 원천은 `UserPreferences` 하나입니다.

- 저장 위치: `core:datastore`
- 저장소 구현: `data:settings`
- 사용 위치: 목록 조회 파이프라인, 설정 화면, 외부 지도 선택

포함하는 값:

- `searchRadius`
- `fuelType`
- `brandFilter`
- `sortOrder`
- `mapProvider`

`demo` flavor는 예외가 하나 있습니다. `DemoSeedStartupHook`이 앱 시작 시 `UserPreferences.default()`로 다시 덮어써 검토 시작 상태를 항상 고정합니다.

## 2. 목록 런타임 상태

목록 화면의 런타임 상태는 단일 세션 객체가 아니라 세 책임으로 나뉩니다.

- `LocationStateMachine`: permission, GPS availability, current coordinates, address label, denied-access flag, recovery refresh flag를 소유합니다.
- `StationSearchOrchestrator`: active query, cache snapshot state, observed search result, pending blocking refresh failure를 소유합니다.
- `StationListViewModel`: loading flag, 사용자 action dispatch, one-shot effect, 최종 `StationListUiState` composition을 소유합니다.

이 값들은 저장되지 않습니다. 화면을 떠나면 사라지고, 앱 재시작 후 복원 대상도 아닙니다.

### 위치 관련 분기

- 위치 availability는 `domain:location.ObserveLocationAvailabilityUseCase`를 통해 ViewModel로 들어오고, route는 foreground 구간에서만 이 흐름을 수집합니다.
- 현재 위치 조회는 평상시 구독이 아니라 새로고침 시점에만 `domain:location.GetCurrentLocationUseCase`를 호출합니다.
- 주소 라벨 조회는 표시용 context입니다. 주소 라벨 resolution은 non-blocking이어야 하며 주유소 refresh를 지연시키지 않습니다.
- `demo`에서는 `DemoLocationOverride`가 availability를 항상 사용 가능으로 만들고, `AndroidForegroundLocationProvider`가 고정 좌표를 공급합니다. permission state는 별도 우회 없이 그대로 유지합니다. 대신 ViewModel이 `hasDeniedLocationAccess`로 demo/recovery 경로를 따로 추적합니다.
- `prod`에서는 같은 저장소 구현이 Android 위치 provider 결과를 `LocationLookupResult`로 변환하므로, ViewModel이 success, timeout, unavailable, permission denied, error를 구분해 처리합니다.
- GPS 상태는 resume 시점 단발 확인이 아니라 availability flow를 통해 화면이 foreground인 동안 계속 반영됩니다.

## 3. 저장소 읽기 모델

목록 화면은 세션 상태만으로 그려지지 않습니다. ViewModel은 다음 세 입력을 결합해 `StationListUiState`를 만듭니다.

- `UserPreferences`
- `LocationStateMachine`과 `StationSearchOrchestrator`가 소유한 목록 런타임 상태
- `StationSearchResult`

현재 좌표가 유지된 상태에서 `UserPreferences`의 반경, 유종, 브랜드, 정렬 조건이 바뀌면 `StationListViewModel`은 이전 query와 다음 query를 비교해 새 조건으로 refresh를 요청합니다. 브랜드 필터와 정렬은 캐시 키에 들어가지 않지만 `StationQuery`와 읽기 모델에는 포함되므로, UI는 즉시 새 조건으로 다시 계산되고 원격 성공 시 스냅샷도 최신화됩니다.

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
- 데이터는 `ObserveWatchlistUseCase` 구독 결과를 그대로 `WatchlistUiState`로 바꿉니다.
- 권한, GPS, 새로고침 플래그는 다시 들고 있지 않습니다.

즉 watchlist 화면은 "어떤 좌표를 기준으로 거리 계산을 할지"와 "저장 항목 요약이 무엇인지"만 알면 됩니다.

## 6. 설정 화면 상태

설정 화면은 별도 복잡한 세션 상태를 거의 만들지 않습니다.

- `SettingsUiState`는 `UserPreferences`를 화면용 라벨/옵션으로 투영한 값입니다.
- `SettingsRoute`와 `SettingsDetailRoute`는 route가 다르지만 같은 `SettingsViewModel`을 공유합니다.
- 사용자가 항목을 선택하면 `UpdateFuelTypeUseCase`, `UpdateSearchRadiusUseCase`, `UpdateBrandFilterUseCase`, `UpdateMapProviderUseCase`, `UpdatePreferredSortOrderUseCase` 같은 명시적 설정 유스케이스가 호출됩니다.

즉 설정 화면은 "영속 상태를 편집하는 얇은 UI 계층"에 가깝습니다.

## 7. 단발성 UI effect

영속 상태나 세션 상태로 저장하지 않는 반응도 있습니다.

- `StationListEffect.ShowSnackbar`
- `StationListEffect.OpenLocationSettings`
- `StationListEffect.OpenExternalMap`

이 값들은 복원 대상이 아니라 즉시 소비 대상입니다. 화면 재구성 후 그대로 남겨 두면 중복 실행되므로 `SharedFlow`로 분리합니다.

## 8. 구조화 이벤트

`StationEvent`는 화면 복원용 상태가 아니라 관찰과 진단을 위한 계약입니다.

- `WatchToggled`는 북마크 저장/해제 액션에서 emit됩니다.
- `RefreshFailed`는 원격 refresh 최종 실패가 feature에 도착했을 때 emit됩니다.
- `LocationFailed`는 위치 획득 실패 유형을 feature가 분류해 emit합니다.
- `RetryAttempted`는 `data:station`의 retry 정책이 두 번째 시도를 마쳤을 때 emit합니다.
- `SearchRefreshed`, `CompareViewed`, `ExternalMapOpened`는 계약과 Logcat 매핑은 있지만 현재 코드 경로에서는 emit되지 않습니다.

## 상태 경계 한 줄 요약

- 오래 유지되는 사용자 선택: `UserPreferences`
- 실행 중 위치 환경: `LocationStateMachine`
- 실행 중 검색/cache/failure 판단: `StationSearchOrchestrator`
- 목록 화면 action/effect/UI 조합: `StationListViewModel`
- 화면에 그릴 데이터 조합: `StationSearchResult`, `WatchedStationSummary`
- 설정 화면 라벨과 옵션: `SettingsUiState`
- 한 번만 소비할 반응: `StationListEffect`
- 복원하지 않는 관찰 이벤트: `StationEvent`
