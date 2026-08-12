# 프로젝트 읽기 가이드

이 문서는 질문·기능·변경 목적에서 실제 코드와 현재 계약으로 가는 라우터입니다. 전체 문서 분류와 독자별 시작점은 [문서 허브](README.md)를, 각 live 문서의 소유·검토 조건은 [documentation-catalog.json](documentation-catalog.json)을 사용합니다. 현재 판단은 항상 실제 코드와 `settings.gradle.kts`를 우선합니다.

## 에이전트 Fast Path

1. `scripts/agent/preflight.sh`로 branch, worktree, dirty state, toolchain, 기존 ledger를 확인합니다.
2. 루트 `AGENTS.md`와 현재 경로에 더 가까운 중첩 `AGENTS.md`를 읽습니다.
3. `settings.gradle.kts`에서 활성 모듈을 확인합니다.
4. 이 문서의 "변경 목적별 바로 열 파일"과 "질문별 가장 빠른 진입점"에서 목적에 맞는 현재 계약 문서를 고릅니다.
5. 관련 테스트 파일을 먼저 읽고 현재 계약을 확인합니다.
6. `docs/superpowers/`, `docs/history/`, `docs/improvements/`는 사용자가 이력 분석을 요청했거나 현재 판단의 배경이 필요할 때만 근거로 봅니다.

## 질문별 가장 빠른 진입점

| 질문 | 먼저 볼 파일 |
| --- | --- |
| 모든 작업에 적용되는 운영 원칙은 어디서 보나 | `AGENTS.md` |
| 나는 에이전트이고 변경 작업을 시작하려 한다 | `AGENTS.md`, `settings.gradle.kts`, `docs/agent-workflow.md`, 이 문서의 변경 목적별 진입점 |
| 처음 프로젝트를 맡은 개발자는 무엇부터 보면 되나 | [문서 허브](README.md)의 "새 기여자와 로컬 실행", `docs/onboarding/developer-onboarding-guide.md` |
| 앱 전체 구조는 어디서 보나 | 먼저 `settings.gradle.kts`, `README.md`, `docs/architecture.md`; 더 깊게는 `docs/module-contracts.md` |
| 새 기능이나 수정 작업은 어떤 순서로 하나 | `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md` |
| 앱이 어디서 시작되나 | `app/src/main/java/com/gasstation/App.kt`, `MainActivity.kt`, `navigation/GasStationNavHost.kt` |
| 목록 화면 상태는 어디서 만들어지나 | `feature/station-list/StationListRoute.kt`, `StationListViewModel.kt`, `LocationStateMachine.kt`, `StationSearchOrchestrator.kt`, `StationListUiState.kt`, `StationListBodyState.kt`, `domain/location/*` |
| 설정 화면은 왜 main/detail route가 나뉘나 | `GasStationNavHost.kt`, `feature/settings/SettingsRoute.kt`, `SettingsDetailRoute.kt`, `SettingsViewModel.kt` |
| watchlist는 어떻게 만들어지나 | `feature/watchlist/WatchlistViewModel.kt`, `domain/station/usecase/ObserveWatchlistUseCase.kt`, `data/station/DefaultStationRepository.kt`, `data/station/WatchlistSummaryAssembler.kt` |
| 디자인 방향과 공통 UI primitive는 어디서 보나 | `.impeccable.md`, `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/*`, `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/*` |
| 오프라인과 stale는 어디서 결정되나 | `data/station/DefaultStationRepository.kt`, `StationSearchResultAssembler.kt`, `StationCachePolicy.kt`, `core/database/station/*` |
| 일시적 refresh 실패 재시도는 어디서 보나 | `data/station/StationRetryPolicy.kt`, `DefaultStationRepository.kt`, `domain/station/model/StationEvent.kt` |
| demo는 어디서 고정되나 | `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`, `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`, `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedStationRemoteDataSource.kt` |
| prod는 어디서 달라지나 | `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`, `app/build.gradle.kts` |
| 외부 지도 연동은 어디 있나 | `app/src/main/java/com/gasstation/map/ExternalMapLauncher.kt` |
| 이벤트/관찰 계약은 어디 있나 | `domain/station/model/StationEvent.kt`, `domain/station/StationEventLogger.kt`, `core/observability/CrashReporter.kt`, `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt`, `app/src/{demo,prod}/kotlin/com/gasstation/analytics/*` |
| startup metric의 "first usable content" 기준은 어디서 정해지나 | `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFirstContentPolicy.kt`, `app/src/main/java/com/gasstation/startup/StartupDrawReporter.kt`, `app/src/main/java/com/gasstation/MainActivity.kt` |
| hero macrobenchmark는 어디 있나 | `benchmark/src/main/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`, `BaselineProfileGenerator.kt`, `GasStationBenchmarkActions.kt`, `app/build.gradle.kts` (`benchmark` build type) |
| 현재 측정된 성능 수치와 재현 명령은 어디서 보나 | `docs/performance.md`, `README.md` "Performance Snapshot" |
| backend proxy 승격 조건은 어디서 보나 | `docs/adr/2026-05-18-backend-proxy-escalation.md`, `docs/security-trade-offs.md` |
| proxy endpoint mode는 어디서 보나 | `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`, `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`, `docs/adr/2026-05-18-backend-proxy-escalation.md` |
| 현재 완료된 개선과 조건부 backlog는 어디서 보나 | `docs/history/deep-analysis-report.md`, `docs/history/improvement-analysis.md` |
| CI와 로컬 검증 명령의 기준은 어디서 보나 | `docs/verification-matrix.md`, `.github/workflows/android.yml` |

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
3. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
4. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
5. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
6. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
7. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStates.kt`
8. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`
9. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBodyState.kt`
10. `domain/location/src/main/kotlin/com/gasstation/domain/location/ObserveLocationAvailabilityUseCase.kt`
11. `domain/location/src/main/kotlin/com/gasstation/domain/location/GetCurrentLocationUseCase.kt`
12. `domain/location/src/main/kotlin/com/gasstation/domain/location/AddressLabelNormalizer.kt`
13. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
14. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshNearbyStationsUseCase.kt`
15. `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
16. `data/station/src/main/kotlin/com/gasstation/data/station/StationSearchResultAssembler.kt`
17. `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`

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
13. `core/datastore/src/main/kotlin/com/gasstation/core/datastore/StoredUserPreferences.kt`
14. `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
15. `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`

핵심은 설정 요약 화면과 상세 선택 화면이 같은 ViewModel을 공유한다는 점입니다.

### 4. watchlist 플로우

1. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
2. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
3. `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
4. `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
5. `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
6. `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`

watchlist는 별도 세션 상태가 거의 없고, 저장소 조합이 핵심입니다.

### 5. infra와 flavor

1. `core/database/src/main/kotlin/com/gasstation/core/database/*`
2. `core/network/src/main/kotlin/com/gasstation/core/network/station/*`
3. `core/location/src/main/kotlin/com/gasstation/core/location/*`
4. `core/observability/src/main/kotlin/com/gasstation/core/observability/*`
5. `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/*`
6. `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`
7. `app/src/demo/kotlin/com/gasstation/*`
8. `app/src/prod/kotlin/com/gasstation/*`
9. `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/*`

여기까지 보면 `demo`가 단순 mock이 아니라 "실제 규칙을 seed 데이터로 재현하는 경로"라는 점이 명확해집니다.

## 기능 소유자와 변경 계약별 바로 열 파일

- 목록 UI를 바꾸려면 (`feature:station-list`, 디자인 표현은 `core:designsystem`):
  `feature/station-list/StationListScreen.kt`, `StationListCards.kt`, `StationListStates.kt`, `StationListQuerySummary.kt`, `StationListBodyState.kt`, `core/designsystem/*`
- 정렬/필터 규칙을 바꾸려면 (`domain:station` 모델, `data:station` 조합):
  `domain/station/model/*`, `data/station/DefaultStationRepository.kt`
- stale 기준이나 캐시 동작을 바꾸려면 (`data:station`, `core:database`; 계약은 `docs/offline-strategy.md`):
  `data/station/StationCachePolicy.kt`, `core/database/station/*`
- watchlist 비교 규칙을 바꾸려면 (`data:station` 조합, `feature:watchlist` 표시):
  `data/station/DefaultStationRepository.kt`, `data/station/WatchlistSummaryAssembler.kt`, `feature/watchlist/*`
- 설정 항목을 바꾸려면 (`domain:settings` use case, `data:settings`, `core:datastore`, `feature:settings`):
  `domain/settings/model/UserPreferences.kt`, `domain/settings/usecase/*`, `core/datastore/*`, `data/settings/DefaultSettingsRepository.kt`, `feature/settings/*`
- 위치 경계를 바꾸려면 (`feature:station-list -> domain:location -> core:location`):
  `domain/location/*`, `core/location/*`, `core/observability/*`, `feature/station-list/*`
- demo 재현 데이터를 바꾸려면 (`tools:demo-seed`, `app` demo flavor):
  `tools/demo-seed/*`, `app/src/demo/assets/demo-station-seed.json`, `app/src/demo/kotlin/*`
- 검증 명령을 바꾸려면 (계약 소유자 `docs/verification-matrix.md`):
  `docs/verification-matrix.md`와 실제 Gradle task 표면

## 길을 잃었을 때

대부분의 질문은 결국 두 기준으로 돌아옵니다.

- 조립 기준: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- 데이터 조합 기준: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`와 `data/station/src/main/kotlin/com/gasstation/data/station/*Assembler.kt`

현재 사용자 플로우와 캐시 정책은 거의 이 두 파일 사이에서 설명됩니다.
