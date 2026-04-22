# 프로젝트 읽기 가이드

이 문서는 처음 보는 사람이 "어디서부터 읽어야 가장 빨리 이해되는가"를 찾는 라우터입니다. 운영 계약은 `AGENTS.md`, 작업 절차는 `docs/agent-workflow.md`, 모듈 위치 판단은 `docs/module-contracts.md`가 소유하고, 여기서는 질문별 진입점만 제공합니다.

## 먼저 볼 문서

1. `AGENTS.md`
2. `README.md`
3. `docs/architecture.md`
4. `docs/module-contracts.md`
5. `docs/agent-workflow.md`
6. `docs/state-model.md`
7. `docs/offline-strategy.md`
8. `docs/test-strategy.md`
9. `docs/verification-matrix.md`

이 순서는 "운영 계약 -> 큰 그림 -> 구조 -> 경계 -> 작업 절차 -> 상태 -> 캐시/오프라인 -> 테스트 -> 실행 명령" 순서입니다.

## 질문별 가장 빠른 진입점

| 질문 | 먼저 볼 파일 |
| --- | --- |
| 모든 작업에 적용되는 운영 원칙은 어디서 보나 | `AGENTS.md` |
| 앱 전체 구조는 어디서 보나 | `settings.gradle.kts`, `README.md`, `docs/architecture.md` |
| 새 기능이나 수정 작업은 어떤 순서로 하나 | `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md` |
| 앱이 어디서 시작되나 | `app/src/main/java/com/gasstation/App.kt`, `MainActivity.kt`, `navigation/GasStationNavHost.kt` |
| 목록 화면 상태는 어디서 만들어지나 | `feature/station-list/StationListRoute.kt`, `StationListViewModel.kt`, `StationListUiState.kt`, `domain/location/*` |
| 설정 화면은 왜 main/detail route가 나뉘나 | `GasStationNavHost.kt`, `feature/settings/SettingsRoute.kt`, `SettingsDetailRoute.kt`, `SettingsViewModel.kt` |
| watchlist는 어떻게 만들어지나 | `feature/watchlist/WatchlistViewModel.kt`, `domain/station/usecase/ObserveWatchlistUseCase.kt`, `data/station/DefaultStationRepository.kt` |
| 디자인 방향과 공통 UI primitive는 어디서 보나 | `.impeccable.md`, `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/*`, `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/*` |
| 오프라인과 stale는 어디서 결정되나 | `data/station/DefaultStationRepository.kt`, `StationCachePolicy.kt`, `core/database/station/*` |
| demo는 어디서 고정되나 | `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`, `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`, `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedStationRemoteDataSource.kt` |
| prod는 어디서 달라지나 | `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`, `app/build.gradle.kts` |
| 외부 지도 연동은 어디 있나 | `app/src/main/java/com/gasstation/map/ExternalMapLauncher.kt` |

## 권장 코드 읽기 순서

### 1. 조립 계층 먼저

1. `settings.gradle.kts`
2. `app/build.gradle.kts`
3. `app/src/main/java/com/gasstation/App.kt`
4. `app/src/main/java/com/gasstation/MainActivity.kt`
5. `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`

여기까지 읽으면 모듈 수, flavor, 시작 화면, route 구조가 보입니다.

### 2. 목록 플로우

1. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
2. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
3. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
4. `domain/location/src/main/kotlin/com/gasstation/domain/location/ObserveLocationAvailabilityUseCase.kt`
5. `domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentLocationUseCase.kt`
6. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
7. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshNearbyStationsUseCase.kt`
8. `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

목록 화면이 이 프로젝트의 중심입니다. 권한, GPS, 위치 조회, 캐시 유지, 가격 변화, watch toggle까지 대부분 여기서 이어집니다.

### 3. 설정 플로우

1. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
2. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
3. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt`
4. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
5. `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
6. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/ObserveUserPreferencesUseCase.kt`
7. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateFuelTypeUseCase.kt`
8. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateSearchRadiusUseCase.kt`
9. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateBrandFilterUseCase.kt`
10. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdateMapProviderUseCase.kt`
11. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdatePreferredSortOrderUseCase.kt`
12. `domain/settings/src/main/kotlin/com/gasstation/domain/settings/model/UserPreferences.kt`
13. `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`

핵심은 설정 요약 화면과 상세 선택 화면이 같은 ViewModel을 공유한다는 점입니다.

### 4. watchlist 플로우

1. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
2. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
3. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
4. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
5. `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

watchlist는 별도 세션 상태가 거의 없고, 저장소 조합이 핵심입니다.

### 5. infra와 flavor

1. `core/database/src/main/kotlin/com/gasstation/core/database/*`
2. `core/network/src/main/kotlin/com/gasstation/core/network/station/*`
3. `core/location/src/main/kotlin/com/gasstation/core/location/*`
4. `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/*`
5. `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`
6. `app/src/demo/kotlin/com/gasstation/*`
7. `app/src/prod/kotlin/com/gasstation/*`
8. `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/*`

여기까지 보면 `demo`가 단순 mock이 아니라 "실제 규칙을 seed 데이터로 재현하는 경로"라는 점이 명확해집니다.

## 변경 목적별 바로 열 파일

- 목록 UI를 바꾸려면:
  `feature/station-list/*`, `core/designsystem/*`
- 정렬/필터 규칙을 바꾸려면:
  `domain/station/model/*`, `data/station/DefaultStationRepository.kt`
- stale 기준이나 캐시 동작을 바꾸려면:
  `data/station/StationCachePolicy.kt`, `core/database/station/*`
- watchlist 비교 규칙을 바꾸려면:
  `data/station/DefaultStationRepository.kt`, `feature/watchlist/*`
- 설정 항목을 바꾸려면:
  `domain/settings/model/UserPreferences.kt`, `domain/settings/usecase/*`, `core/datastore/*`, `feature/settings/*`
- 위치 경계를 바꾸려면:
  `domain/location/*`, `core/location/*`, `feature/station-list/*`
- demo 재현 데이터를 바꾸려면:
  `tools/demo-seed/*`, `app/src/demo/assets/demo-station-seed.json`, `app/src/demo/kotlin/*`
- 검증 명령을 바꾸려면:
  `docs/verification-matrix.md`와 실제 Gradle task 표면

## 길을 잃었을 때

대부분의 질문은 결국 두 파일로 돌아옵니다.

- 조립 기준: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- 데이터 조합 기준: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

현재 사용자 플로우와 캐시 정책은 거의 이 두 파일 사이에서 설명됩니다.
