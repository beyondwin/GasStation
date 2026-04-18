# 상태 모델

GasStation은 상태를 네 층으로 나눠 보면 가장 빠르게 읽힙니다.

이 상태 모델은 현재 지원하는 `demo`/`prod` 실행 경로만 설명한다. 과거 앱 버전이나 과거 직렬화 포맷을 유지하기 위한 호환 상태는 더 이상 지원 범위에 포함하지 않는다.

1. 영속 선호 상태
2. 목록 화면 세션 상태
3. 저장소가 만드는 읽기 모델
4. 단발성 UI 효과

## 빠른 요약

| 층 | 대표 타입 | 왜 존재하나 |
| --- | --- | --- |
| 영속 선호 상태 | `UserPreferences` | 사용자가 고른 반경, 유종, 정렬, 지도 앱을 유지 |
| 세션 상태 | `StationListSessionState` | 권한, GPS, 로딩, 실패 같은 런타임 환경을 관리 |
| 읽기 모델 | `StationSearchResult`, `WatchedStationSummary` | Room/히스토리/관심 목록을 조합해 화면용 데이터 생성 |
| 단발성 효과 | `StationListEffect` | 스낵바, 외부 지도 열기, 설정 이동 같은 즉시 소비 이벤트 처리 |

구조 관점에서 중요한 점은 "영속값, 런타임 환경, 화면용 데이터, 한 번만 소비할 효과"를 섞지 않았다는 것입니다.

## 영속 선호 상태

영속 상태의 기준 원천은 `core:datastore`가 저장하는 `UserPreferences` 하나입니다. `data:settings`가 `SettingsRepository` 구현을 제공하고, 설정 화면과 목록 조회 파이프라인이 같은 값을 읽습니다.

영속값은 다음 다섯 가지입니다.

- 검색 반경
- 유종
- 브랜드 필터
- 정렬 순서
- 외부 지도 제공자

이 값은 프로세스가 죽어도 유지됩니다. 다만 `demo` flavor는 `DemoSeedStartupHook`이 앱 시작 때 `UserPreferences.default()`로 다시 덮어써 시작 상태를 고정합니다.

## 목록 화면 세션 상태

`feature:station-list`는 영속 선호와 별개로 런타임 전용 `StationListSessionState`를 유지합니다.

- 위치 권한 상태
- GPS 사용 가능 여부
- 현재 좌표
- 초기 로딩 여부
- 새로고침 진행 여부
- `blockingFailure`

이 세션 상태는 `StationListViewModel` 내부에만 있고 저장되지 않습니다. `demo`에서는 `DemoLocationOverride`가 들어오면 권한 상태를 사실상 허용으로 취급하고, 실제 GPS 대신 고정 좌표를 공급합니다.

위치 조회 자체는 `ForegroundLocationProvider`가 `LocationLookupResult`로 돌려줍니다. 즉 세션 상태는 단순히 "좌표가 있나"만 들고 있는 것이 아니라, ViewModel이 성공/timeout/unavailable/error를 구분한 뒤 그 결과를 `currentCoordinates`와 `blockingFailure`, snackbar 효과에 반영하는 구조입니다.

GPS 사용 가능 여부도 더 이상 `onResume`성 재확인 한 번으로 끝나지 않습니다. `StationListRoute`는 화면이 foreground인 동안 broadcast-backed flow를 수집해 provider 변경을 즉시 `GpsAvailabilityChanged` 액션으로 보냅니다.

## 목록 읽기 모델

목록 화면이 실제로 그리는 값은 `StationListSessionState`만으로 만들어지지 않습니다. `StationListViewModel`은 아래 세 입력을 결합해 `StationListUiState`를 만듭니다.

- `UserPreferences`
- `StationListSessionState`
- `StationSearchResult`

`StationSearchResult`는 저장소에서 오는 읽기 모델로, 아래 정보를 포함합니다.

- `stations`
- `freshness`
- `fetchedAt`

최종 `StationListUiState`는 여기에 다음 파생값을 더합니다.

- `isStale`
- 화면 카드용 `StationListItemUiModel`
- 현재 선택된 필터/반경/유종/정렬
- 마지막 업데이트 시각
- `blockingFailure`

즉, 목록 화면은 "영속 선호 + 런타임 환경 + 캐시된 검색 결과"를 동시에 보는 구조입니다.

여기서 중요한 분기가 있습니다.

- `fetchedAt != null`이면 적어도 한 번 저장된 캐시 스냅샷이 남아 있다는 뜻입니다. 이 값은 cached result의 존재를 증명하지만, 모든 successful empty 결과의 공통 마커는 아닙니다.
- location/refresh 실패가 발생해도 캐시 스냅샷이 남아 있으면 `blockingFailure`는 비워 두고, 기존 결과를 계속 보여주면서 snackbar와 stale 데이터만 갱신합니다.
- location/refresh 실패와 동시에 화면에 남길 캐시 스냅샷도 없으면 `StationListUiState.blockingFailure`가 채워지고 전면 실패 상태로 전환됩니다.

즉 empty results와 "새로고침이 실패했고 캐시도 없어 아무 것도 못 보여주는 상태"는 별개의 상태이며, 이 구분은 `fetchedAt` 단독이 아니라 `blockingFailure`와 캐시 스냅샷 존재 여부를 함께 봐야 정확합니다.

## watchlist 상태

`feature:watchlist`는 별도 세션 리듀서를 두지 않습니다. 목록 화면에서 nav argument로 넘긴 기준 좌표를 `SavedStateHandle`에서 읽고, `ObserveWatchlistUseCase`를 바로 구독해 `WatchlistUiState`를 만듭니다.

watchlist 화면이 가진 상태 관심사는 단순합니다.

- 어떤 좌표를 기준으로 거리를 계산할지
- 관심 주유소 요약 목록이 현재 무엇인지

이 덕분에 watchlist는 위치 권한, GPS, 새로고침 플래그를 다시 들고 있지 않습니다.

## 설정 화면 상태

`feature:settings`는 `UserPreferences`를 그대로 화면 상태로 투영한 `SettingsUiState`를 사용합니다. 메인 설정 목록과 상세 선택 화면은 route가 다르지만 같은 `SettingsViewModel`을 공유합니다.

요약하면 설정 화면은 세션 상태를 거의 만들지 않고, 영속 상태를 직접 편집하는 얇은 UI 계층입니다.

## 읽기 모델 상태

`data:station`은 Room 스냅샷, 가격 히스토리, 관심 목록을 결합해 두 종류의 읽기 모델을 만듭니다.

- 목록 읽기 모델: `StationListEntry`
- 비교 읽기 모델: `WatchedStationSummary`

이 읽기 모델에는 아래 같은 파생 정보가 포함됩니다.

- 현재 가격
- 현재 기준 좌표 대비 거리
- 관심 여부
- 가격 변화 배지 계산 결과
- 마지막으로 확인한 시각
- stale 여부에 따른 최신/오래된 스냅샷 의미

핵심은 이 파생 계산이 ViewModel이 아니라 저장소 구현에 있다는 점입니다.

새로고침 실패도 저장소 구현에서 바로 일반 예외로 뭉개지지 않습니다. remote 계층은 `StationRefreshFailureReason`을 붙인 `RemoteStationFetchResult.Failure`를 만들고, 저장소 경계에서는 이를 `StationRefreshException(reason, cause)`로 승격합니다. 이 덕분에 domain/use case/UI는 timeout과 generic refresh failure를 구분하면서도 기존 캐시 스냅샷을 그대로 유지하는 정책을 적용할 수 있습니다.

## 단발성 UI 효과

영속 상태에도 세션 상태에도 넣지 않는 값이 있습니다. `StationListViewModel`은 `SharedFlow` 기반 효과로 아래 이벤트를 내보냅니다.

- 위치 설정 화면 열기
- 스낵바 메시지 표시
- 외부 지도 앱 열기

이 값은 화면 재구성 후 복원 대상이 아니라 즉시 소비 대상입니다.

## 상태 경계 한 줄 요약

- 오래 유지되는 사용자 선택: `UserPreferences`
- 실행 중 환경과 로딩 플래그: `StationListSessionState`
- 화면에 그릴 데이터 조합: `StationSearchResult`, `WatchedStationSummary`
- 한 번만 소비할 UI 반응: `StationListEffect`
