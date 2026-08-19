# 모듈 계약

이 문서는 "어떤 변경을 어디에 둬야 하는가"와 "어떤 모듈이 무엇을 소유하면 안 되는가"를 판단하는 단일 출처입니다. `AGENTS.md`와 `docs/agent-workflow.md`는 전체 모듈 표를 반복하지 않고 이 문서를 참조합니다. 세부 데이터 흐름은 `docs/architecture.md`, 읽기 순서는 `docs/project-reading-guide.md`를 봅니다.

## 공통 규칙

- `app`은 조립과 연결만 담당하고 정책을 소유하지 않습니다.
- `feature:*`는 화면 상태를 만들지만 Room, Retrofit, DataStore를 직접 다루지 않습니다. 읽기와 이벤트는 도메인 계약을 통해 연결하고, 설정 변경처럼 정책이 있는 쓰기는 명시적 도메인 유스케이스를 통해 수행합니다.
- `domain:*`는 계약과 모델을 소유하지만 Android/UI 타입을 노출하지 않습니다.
- `data:*`는 저장과 조합을 담당하지만 화면 상태나 Compose 타입을 만들지 않습니다.
- `core:*`는 여러 모듈이 공유하는 인프라와 값 객체만 둡니다.
- 위 경계는 `gasstation.root.quality`가 등록하는 `verifyModuleBoundaries`(CI `static-analysis` 포함)로 강제합니다.
  `settings.gradle.kts`가 소유하는 정확한 18개 활성 모듈의 JVM `main` 및 Android production variant compile/runtime hierarchy를 public Gradle/AGP model로 수집합니다. 직접 project/external dependency는 선언 configuration과 component membership까지 `config/quality/production-dependency-policy.txt`의 exact scope row와 대조하며 wildcard는 허용하지 않습니다. `benchmark → app`은 self-instrumenting tested-target 관계로 별도 관리합니다.
  `productionDependencyInventory`의 전이 resolved graph는 보고 전용 evidence이며 direct architecture allowlist로 승격하지 않습니다. 의도된 project 예외는 exact allowlist의 `core:location → domain:location` 한 행뿐입니다.
- 공개 ABI owner는 정확히 `core:model`, `core:observability`, `domain:location`, `domain:settings`, `domain:station`입니다. 각 모듈의 전체 `api/*.api` baseline과 compiled JVM surface를 `verifyPublicApiBoundaries`가 대조하고 `android.*`, `androidx.*`, `com.google.android.gms.*`, `retrofit2.*`, `okhttp3.*`, `com.google.gson.*` 타입의 공개 계약 누수를 차단합니다.

## 모듈 인벤토리

| 모듈 | 소유 범위 | 직접 의존 | 이 모듈에 두지 말 것 |
| --- | --- | --- | --- |
| `app` | Hilt 조립, startup hook, navigation, flavor 연결, 외부 앱 handoff, network endpoint 모드 선택(`AppConfigModule` via `BuildConfig.STATION_ENDPOINT_MODE`/`PROXY_BASE_URL`), `StationEventLogger` 구현 연결, flavor별 `CrashReporter` 구현/Hilt 바인딩 | `feature:*`, `data:*`, 필요한 `core:*`, `domain:*` | 캐시 정책, 비즈니스 규칙 |
| `feature:station-list` | `LocationStateMachine` 위치/address generation, `StationSearchOrchestrator` 관찰 session, `RefreshCoordinator` 단일 refresh work, `StationListCommandQueue` 승인형 FIFO, `StationListStateInputs`/`StationListStateAssembler` 순수 projection, 얇은 ViewModel의 action/lifecycle 조정 | `domain:location`, `domain:station`, `domain:settings`, `core:designsystem`, `core:model` | Room/Retrofit 접근, `core:location` 직접 호출, data retry/cache policy 복제, ViewModel에 collaborator 동시성 또는 field projection 재집중 |
| `feature:settings` | 설정 요약/상세 UI, 항목 선택 액션 | `core:model`, `domain:settings`, `core:designsystem` | 저장 구현, 네트워크 설정 |
| `feature:watchlist` | watchlist(북마크) 비교 UI | `domain:station`, `domain:settings`, `core:model`, `core:designsystem` | 현재 위치 조회, refresh 세션 상태 |
| `domain:location` | `LocationRepository`, 위치 permission/result 모델, 위치 조회/availability use case | `core:model` | Android 위치 API, Play services 타입 |
| `domain:settings` | `SettingsRepository`, `UserPreferences`, 관련 use case | `core:model` as public API | DataStore 구현, Android 타입 |
| `domain:station` | `StationRepository`, 검색/비교 use case, `WatchMutationResult` 변경 결과, `StationEvent`/`StationEventLogger` 계약, 도메인 모델 | `core:model` | Room entity, Retrofit DTO, Logcat/analytics/Crashlytics SDK 구현 |
| `data:settings` | `SettingsRepository` 구현 | `domain:settings`, `core:datastore` | Compose 상태 |
| `data:station` | `StationRepository` 구현, 캐시/히스토리/watchlist 조합, 검색 결과/watchlist 읽기 모델 조립, 일시적 refresh 실패 retry 정책, update/remove가 공유하는 station ID별 latest-watch-intent 직렬화 | `domain:station`, `core:observability`, `core:database`, `core:network`, `core:model` | 화면 전용 UI state/command, 위치 조회 구현, snackbar/전면 실패 판단 |
| `core:model` | `Coordinates`, `DistanceMeters`, `MoneyWon` 값 객체, `Coordinates.distanceTo`, `Brand.fromCode`, `Brand`, `BrandFilter`, `FuelType`, `MapProvider`, `SearchRadius`, `SortOrder` 공유 enum vocabulary | 없음 | 앱 정책 |
| `core:observability` | `CrashReporter` 같은 SDK-agnostic 관찰/진단 계약 | 없음 | feature 화면 상태, 특정 domain 정책, Timber/Crashlytics SDK 구현 |
| `core:designsystem` | 테마, 색상, 타이포, 카드/배너/탑바, metric/supporting-info/row/guidance 같은 공통 UI primitive, 브랜드 아이콘 리소스와 표시 label 매핑 | Compose/Material3, `core:model` | feature 전용 비즈니스 문구, 화면 상태 분기, 검색/저장 정책 |
| `core:location` | `domain:location` 구현체, Android 위치 provider, availability flow, API 33+ 지오코더 callback/pre-33 fallback, Android 주소 후보를 domain 정규화 규칙으로 변환, `DemoLocationOverride` 계약, repository/provider Hilt 바인딩 | `domain:location`, `core:observability`, `core:model` | 목록 카드 배치 정책, flavor별 demo override 바인딩, 위치 도메인 계약 |
| `core:network` | direct Opinet/proxy endpoint 모드(`StationNetworkSource` 추상화), Opinet 서비스, 좌표 변환, fetcher | `core:model` | 캐시/Room 조합, endpoint 모드/base URL 선택(=`app` 소유) |
| `core:database` | Room DB, DAO, migration, watch ON `INSERT IGNORE`와 `watchedAt DESC, stationId ASC` 관찰 순서 같은 저장 primitive | Room | latest 사용자 의도나 analytics 같은 도메인/feature 정책 |
| `core:datastore` | DataStore data source, serializer, storage-local settings DTO | Android DataStore | 화면 상태, 설정 정책, domain model |
| `tools:demo-seed` | demo seed 재생성 CLI | `core:network`, `domain:station`, `core:model` | 앱 런타임 의존 |
| `benchmark` | `demo` hero macrobenchmark, baseline profile journey, physical-device performance evidence | `app` | 기능 구현 |

## 경계가 헷갈릴 때 보는 기준

- 새 설정 항목 추가:
  `domain/settings/model/UserPreferences.kt` -> `core/datastore/*` storage DTO/serializer -> `data/settings/DefaultSettingsRepository.kt` mapper -> `feature/settings/*`
- 설정 변경 호출 경로 변경:
  `domain/settings/usecase/*` -> `feature/settings/*` 또는 `feature/station-list/*`
- 목록 정렬/필터 규칙 변경:
  `domain/station/model/*` -> `data/station/DefaultStationRepository.kt` -> 필요 시 `feature/station-list/*`
- 위치 조회 계약/구현 변경:
  `domain/location/*` -> `core/location/*` -> 필요 시 `feature/station-list/*`
- 위치 결과를 목록 검색에 연결:
  `feature/station-list/*`에서 `domain:location` 결과로 `StationQuery`를 만들고, `data:station`에는 위치 provider나 `core:location` 타입을 넣지 않음
- 현재 주소 표시 변경:
  주소 라벨 정규화 규칙은 `domain/location/AddressLabelNormalizer.kt`, Android 지오코더 후보 변환은 `core/location/*`, 목록 상단 표시는 `feature/station-list/*`
- station-list 상태 또는 command 변경:
  generation은 `LocationStateMachine`, 관찰 복구는 `StationSearchOrchestrator`, refresh work는 `RefreshCoordinator`, 보존/acknowledgement는 `StationListCommandQueue`, field/body projection은 `StationListStateInputs`와 `StationListStateAssembler`, action/lifecycle 연결만 `StationListViewModel`
- 브랜드 아이콘 또는 표시 label 변경:
  `core:model`의 `Brand`/`BrandFilter` enum, `core/designsystem/component/BrandIcon.kt`, `core/designsystem/BrandLabels.kt`를 먼저 확인하고, 목록/북마크별 label 노출 정책은 각 `feature:*` 화면에 둠
- 캐시/stale 정책 변경:
  `data/station/StationCachePolicy.kt`와 `core/database/*`
- refresh 재시도 정책 변경:
  `data/station/StationRetryPolicy.kt`, `data/station/DefaultStationRepository.kt`, retry event 계약이 바뀌면 `domain/station/model/StationEvent.kt`
- 원격 endpoint 모드(direct/proxy) 변경:
  `core/network/di/NetworkRuntimeConfig.kt`, `core/network/di/NetworkModule.kt`, `core/network/station/ProxyStationFetcher.kt`, 선택 wiring은 `app/src/main/java/com/gasstation/di/AppConfigModule.kt`와 `app/build.gradle.kts` buildConfigField
- 이벤트/관찰 계약 변경:
  이벤트 종류와 payload는 `domain/station/model/StationEvent.kt`, 비치명 예외 보고 계약은 `core/observability/CrashReporter.kt`, 앱의 현재 구현은 `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt`와 `app/src/{demo,prod}/kotlin/com/gasstation/analytics/*`
- watchlist 비교 규칙 변경:
  `data/station/DefaultStationRepository.kt`, `data/station/WatchlistSummaryAssembler.kt`, `feature/watchlist/*`
- watch mutation 순서 또는 결과 변경:
  결과 계약은 `domain/station/model/WatchMutationResult.kt`, latest intent는 `data/station/LatestWatchIntentGate.kt`, `INSERT IGNORE`와 안정적 관찰 순서는 `core/database/station/WatchedStationDao.kt`, committed-only feedback은 각 `feature:*`
- demo 재현 경로 변경:
  `tools/demo-seed/*`, `app/src/demo/assets/demo-station-seed.json`, `app/src/demo/kotlin/*`

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)

## 현재 프로젝트 전제

- 공식 지원 런타임은 `demo`와 `prod` 두 경로뿐입니다.
- `demo`는 예외 경로가 아니라 문서와 테스트가 전제로 삼는 정식 경로입니다.
- 과거 직렬화 포맷이나 폐기된 네트워크 provider 호환은 현재 설계 목표가 아닙니다.
