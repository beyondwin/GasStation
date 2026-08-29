# 읽기 가이드

질문에서 코드와 계약으로 가는 표다. 문서 분류는 [문서 허브](README.md), 소유는 [documentation-catalog.json](documentation-catalog.json)이다. 지금 판단은 코드와 `settings.gradle.kts`가 먼저다.

## 에이전트

1. `scripts/agent/preflight.sh`
2. 루트 `AGENTS.md`와 가까운 중첩 `AGENTS.md`
3. `settings.gradle.kts`의 활성 모듈
4. 아래 표에서 계약과 파일을 고른다
5. 관련 테스트를 먼저 읽는다
6. `docs/superpowers/`, `docs/history/`, `docs/improvements/`는 이력이 필요할 때만 본다

## 질문

| 질문 | 먼저 볼 곳 |
| --- | --- |
| 작업 규칙 | `AGENTS.md` |
| 변경을 시작하려면 | `AGENTS.md`, `settings.gradle.kts`, `docs/agent-workflow.md` |
| 처음 맡은 사람 | [문서 허브](README.md), `docs/onboarding/developer-onboarding-guide.md` |
| 전체 구조 | `settings.gradle.kts`, `docs/architecture.md`, `docs/module-contracts.md` |
| 앱이 어디서 시작하나 | `App.kt`, `MainActivity.kt`, `GasStationNavHost.kt` |
| 목록 상태는 어디서 만들어지나 | `StationListViewModel`, `LocationStateMachine`, `StationSearchOrchestrator`, `RefreshCoordinator`, `StationListCommandQueue`, `StationListStateAssembler` |
| 설정 화면이 둘인 이유 | `GasStationNavHost`, `SettingsRoute`, `SettingsDetailRoute`, 같은 `SettingsViewModel` |
| 관심은 어떻게 만들어지나 | `WatchlistViewModel`, `ObserveWatchlistUseCase`, `DefaultStationRepository`, `WatchlistSummaryAssembler` |
| 디자인 | `.impeccable.md`, `core/designsystem` |
| 오프라인·stale | `DefaultStationRepository`, `StationSearchResultAssembler`, `StationCachePolicy` |
| 재시도 | `StationRetryPolicy`, `DefaultStationRepository` |
| demo | `DemoSeedStartupHook`, `DemoLocationModule`, `DemoSeedStationRemoteDataSource` |
| prod | `ProdSecretsStartupHook`, `app/build.gradle.kts` |
| 외부 지도 | `ExternalMapLauncher` |
| 이벤트 | `StationEvent`, `StationEventLogger`, `CrashReporter` |
| first usable content | `StationListFirstContentPolicy`, `StartupDrawReporter` |
| benchmark | `StationListBenchmark`, `BaselineProfileGenerator` |
| 성능 숫자 | `docs/performance.md` |
| proxy 승격 | `docs/adr/2026-05-18-backend-proxy-escalation.md`, `docs/security-trade-offs.md` |
| CI 명령 | `docs/verification-matrix.md`, `.github/workflows/android.yml` |
| 기기 lane | `docs/runbooks/device-verification.md` |

## 코드 읽기 순서

조립부터 본다. `settings.gradle.kts` → `App.kt` → `MainActivity.kt` → `GasStationNavHost.kt`.

목록이 중심이다. Route/ViewModel → 네 collaborator와 assembler → Screen/Cards → `ObserveNearbyStationsUseCase` → `DefaultStationRepository`.

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)

설정은 요약과 상세가 같은 ViewModel을 공유한다. `SettingsRoute` → `SettingsViewModel` → `domain:settings` use case → `DefaultSettingsRepository` → DataStore.

관심은 세션 상태가 거의 없다. `WatchlistViewModel` → `ObserveWatchlistUseCase` → `WatchlistSummaryAssembler`.

infra는 `core:database`, `core:network`, `core:location`, `core:designsystem`, `app/src/demo`, `app/src/prod`, `tools/demo-seed`다. `demo`는 같은 규칙을 seed로 재현한다.

## 바꿀 때 열 파일

- 목록 UI: `StationListScreen.kt`, `StationListCards.kt`, `core/designsystem`
- 정렬/필터: `domain/station/model/*`, `DefaultStationRepository.kt`
- stale/캐시: `StationCachePolicy.kt`, `core/database/station/*`
- 관심 비교: `WatchlistSummaryAssembler.kt`, `feature/watchlist`
- 설정: `UserPreferences.kt`, settings use case, datastore, `feature/settings`
- 위치: `domain/location`, `core/location`, `feature/station-list`
- demo seed: `tools/demo-seed`, `demo-station-seed.json`
- 검증 명령: `docs/verification-matrix.md`

## 길을 잃으면

- 화면과 route: `GasStationNavHost.kt`
- 데이터 조합: `DefaultStationRepository.kt`와 assembler들
