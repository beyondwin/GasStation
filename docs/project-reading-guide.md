# 프로젝트 읽기 가이드

처음 보는 사람이 읽기 순서를 가장 빨리 잡기 위한 안내서입니다. 현재 구조는 `app / feature / domain / data / core / tools / benchmark`입니다.

## 이 문서에서 바로 얻는 것

| 질문 | 가장 빠른 답 |
| --- | --- |
| 프로젝트 전체 구조를 어디서 보나 | `README.md` -> `docs/architecture.md` |
| 사용자 플로우 진입점은 어디인가 | `MainActivity.kt`, `GasStationNavHost.kt`, `StationListRoute.kt` |
| 가장 복잡한 구현은 어디에 있나 | `data/station/DefaultStationRepository.kt` |
| demo 시연 경로는 어디서 고정되나 | `DemoSeedStartupHook.kt`, `DemoLocationModule.kt` |

## 먼저 잡아야 할 그림

이 앱은 현재 위치와 사용자 선호를 입력으로 받아 주유소 목록을 만들고, 마지막 성공 결과를 Room에 유지한 채 관심 주유소 비교와 외부 지도 연동까지 이어지는 Compose 앱입니다.

처음 읽을 때는 아래 세 질문으로 나누면 가장 덜 헷갈립니다.

1. 화면이 어떤 상태를 보여주나
2. 그 상태를 만드는 계약이 어디 있나
3. 실제 데이터는 어디서 읽고 어디에 저장하나

## 가장 안전한 읽기 순서

처음 보는 사람에게는 이 순서를 권장합니다.

1. `README.md`
2. `docs/architecture.md`
3. `docs/module-contracts.md`
4. `docs/state-model.md`
5. `docs/offline-strategy.md`
6. `docs/test-strategy.md`
7. `docs/verification-matrix.md`
8. `settings.gradle.kts`
9. `app/src/main/java/com/gasstation/MainActivity.kt`
10. `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
11. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
12. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`

이 순서는 조립 계층에서 시작해 화면 상태를 거쳐 저장소 구현으로 내려갑니다. 처음부터 DAO나 네트워크 매퍼를 보면 큰 그림 없이 디테일만 보게 됩니다.

## 기능별 읽기 루트

### 주변 주유소 목록이 어떻게 만들어지는가

1. `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
2. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
3. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
4. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
5. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshNearbyStationsUseCase.kt`
6. `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
7. `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
8. `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
9. `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
10. `core/database/src/main/kotlin/com/gasstation/core/database/station/*`

### 설정 화면이 어떻게 구성되는가

1. `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`
2. `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
3. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
4. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
5. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt`
6. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
7. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
8. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
9. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt`
10. `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`

핵심은 설정 메인 화면과 상세 선택 화면이 다른 route를 쓰지만 같은 `SettingsViewModel` 상태를 공유한다는 점입니다.

### 관심 주유소 비교 화면이 어떻게 만들어지는가

1. `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`
2. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
3. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
4. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
5. `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

watchlist는 별도 세션 상태를 만들지 않고, 목록 화면에서 넘긴 기준 좌표와 저장된 관심 목록을 조합해 즉시 읽기 모델을 만듭니다.

### demo 실행 경로가 어떻게 고정되는가

1. `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
2. `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
3. `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt`
4. `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedStationRemoteDataSource.kt`
5. `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`

여기까지 보면 demo가 "가짜 API"가 아니라 "실제 API로 미리 생성한 JSON 자산을 런타임에 다시 적재하는 경로"라는 점이 명확해집니다.

## 모듈을 읽을 때 봐야 할 포인트

### `app`

- 조립 계층인지 본다.
- startup hook과 DI가 들어 있는 건 정상이다.
- 비즈니스 규칙이나 캐시 정책이 들어오면 과도해진 신호다.

현재 중요 파일은 `App.kt`, `MainActivity.kt`, `GasStationNavHost.kt`, `AppConfigModule.kt`, `AnalyticsModule.kt`, flavor별 startup hook입니다.

### `feature:*`

- 사용자 액션을 어떤 상태 변환으로 바꾸는지 본다.
- Android 환경 의존은 route에서, 상태 조합은 ViewModel에서, 렌더링은 screen에서 처리하는지 확인한다.
- 현재 UI는 `core:designsystem`의 `GasStationTopBar`, `GasStationCard`, `GasStationSectionHeading`, `GasStationStatusBanner`, `GasStationBackground` 같은 컴포넌트를 적극 사용한다.

### `domain:*`

- "얇아 보여도 남겨 둔 경계"라고 이해하면 된다.
- 저장소 인터페이스, 유스케이스, 모델, 이벤트 계약이 여기에 있다.
- 구현 세부 대신 기능 경계를 읽는 층이다.

### `data:*`

- 이 프로젝트의 실질적인 복잡도는 여기 있다.
- 특히 `DefaultStationRepository`는 캐시 스냅샷, 관심 여부, 가격 히스토리, 필터, 정렬, stale 판정을 한 곳에서 조합한다.

### `core:*`

- 여러 feature/data/domain이 공유하는 원시 타입과 인프라다.
- `core:network`는 Opinet 호출과 좌표 변환을, `core:database`는 Room 저장소를, `core:datastore`는 선호 저장소를, `core:location`은 위치 계약을 담당한다.

## 변경할 때 바로 열어야 하는 곳

- 목록 UI를 바꾸려면: `feature/station-list/*`, `core/designsystem/*`
- 목록 정렬/필터 규칙을 바꾸려면: `domain/station/model/*`, `data/station/DefaultStationRepository.kt`
- stale 규칙을 바꾸려면: `data/station/StationCachePolicy.kt`
- 설정 옵션을 추가하려면: `domain/settings/model/UserPreferences.kt`, `feature/settings/*`, `core/datastore/*`
- watchlist 비교 규칙을 바꾸려면: `data/station/DefaultStationRepository.kt`, `feature/watchlist/*`
- demo 시연 데이터를 바꾸려면: `tools/demo-seed/*`, `app/src/demo/assets/demo-station-seed.json`, `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`

## 마지막 팁

이 저장소는 문서, 모듈 그래프, demo 검토 경로가 꽤 정렬된 편입니다. 길을 잃으면 다시 `GasStationNavHost`와 `DefaultStationRepository` 두 파일로 돌아오면 됩니다. 대부분의 사용자 플로우는 그 둘 사이에서 설명됩니다.
