# 테스트 전략

이 문서는 GasStation의 테스트 철학과 계층별 신뢰 범위를 설명하는 단일 출처입니다. 테스트는 "현재 공식 경로인 `demo`와 `prod`가 계속 성립하는가"를 확인하는 데 집중합니다. 정확한 실행 명령은 `docs/verification-matrix.md`를 보고, 여기서는 어떤 층을 어떤 테스트로 막고 있는지 설명합니다.

## 기본 원칙

- 가장 복잡한 조합 로직은 저장소, 상태 collaborator 집중 테스트와 얇은 ViewModel integration 테스트로 먼저 막습니다.
- `demo`는 별도 예외 경로가 아니라 정식 제품 경로이므로 startup, seed, UI 플로우를 따로 검증합니다.
- `prod`는 실제 API 키/네트워크를 요구하는 대신, 빌드/그래프/런타임 설정 경로가 깨지지 않는지를 unit/assemble 수준에서 확인합니다.
- 이미 문서로 약속한 사용자 흐름은 가능한 한 테스트 파일 이름으로도 추적 가능해야 합니다.
- Android library 공통 unit test 의존성(`junit`, `kotlinx-coroutines-test`, `androidx.test:core`, `robolectric`)은 `gasstation.android.library` 컨벤션이 소유합니다.
- Android 모듈은 최신 AndroidX가 요구하는 `compileSdk 37`을 사용하되, 안정 Robolectric 4.16.1의 지원 상한에 맞춰 로컬 unit test SDK를 `config/robolectric/robolectric.properties`의 API 36으로 고정합니다. API 36 Robolectric 런타임에는 Java 21 이상을 사용하고, 앱의 Java/Kotlin bytecode target은 JVM 17을 유지합니다. app/library convention이 이 공통 test resource를 연결하며, API 37 동작은 Robolectric 안정 지원 전까지 connected test로 확인합니다.
- Compose Android library의 UI test/debug 의존성(Compose test BOM, UI test JUnit4, UI tooling, UI test manifest)은 `gasstation.android.library.compose` 컨벤션이 소유합니다. 모듈 build file에는 Turbine, MockWebServer, `kotlin.test`, project test dependency, androidTest smoke dependency처럼 모듈별로 필요한 의존성만 둡니다.
- Compose test rule은 `androidx.compose.ui.test.junit4.v2` 환경을 사용합니다. v2의 `StandardTestDispatcher` 기반 동기화 계약을 유지하고, deprecated v1 test-environment import는 `verifyNoDeprecatedComposeTestApis`가 차단합니다.
- Compose 테스트 selector는 ASCII `testTag`를 사용하고, 한글 사용자 문구와 스크린 리더용 설명은 `contentDescription` 같은 접근성 semantics에 남깁니다.
- 새로 추가하거나 반복 setup을 정리하는 coroutine ViewModel 테스트에서 `Dispatchers.Main`이 필요하면 feature-local rule/helper로 설정을 중앙화합니다. 현재 station-list 테스트는 `MainDispatcherRule`이 이 계약을 소유합니다.
- agent contract test는 `v*` tag의 `release-publish`가 전체 CI job을 선행 조건으로 사용하고, job 범위의 `contents: write`, release note, 다운로드한 APK, `gh release create` 경로를 잃지 않도록 보호합니다.
- Build velocity settings are valid only while the verification matrix stays green. If `parallel`, build cache, or configuration cache changes a task result, treat it as a build correctness issue and fix the build boundary before changing product behavior.

## Android Lint 경로

Android application/library convention은 production source와 test source를 같은 정책 owner로 구성합니다. `gasstation.lintTestSources`는 생략 또는 정확한 `false`일 때 test source를 제외하고, 정확한 `true`일 때 unit/instrumented test source를 포함합니다. 대소문자 변형, 공백, 오타, 빈 값은 설정 오류입니다. Android Lint error와 warning은 모두 build를 중단하며, production `static-analysis`와 test-source `lint-tests` CI job은 모두 blocking입니다.

이 경로는 `gasstation.android.application.compose`, `gasstation.android.library`, 그리고 이를 상속하는 `gasstation.android.library.compose` Android 모듈을 다룹니다. `gasstation.jvm.library`를 사용하는 `core:model`, `core:network`, `core:observability`, `domain:*`, `tools:demo-seed`는 Android Lint 대상이 아니며 Spotless, Kotlin compiler diagnostic, dependency/ABI, unit coverage와 구성된 PIT가 별도 owner입니다. `benchmark`도 이 convention 경로를 상속하지 않습니다. 정확한 production/test-source 명령과 보고서 위치는 [검증 매트릭스](verification-matrix.md#android-lint-분리-경로)를 따릅니다.

## Kotlin 및 테스트 convention 경로

Android Lint는 application/library Android source를 맡고, Kotlin compiler strict explicit-API gate는 현재 활성 `domain:*` 세 모듈과 `core:model`, `core:observability`의 다섯 JVM contract 모듈을 blocking으로 맡습니다. 그 밖의 convention-owned 모듈은 report-only이며 명시적 opt-in으로만 compiler warning을 승격합니다. application, Android library, JVM library convention이 소유하는 모든 `Test` task는 15분 task timeout을 사용하고 retry는 적용하지 않습니다.

다섯 JVM contract 모듈은 Kotlin 2.4.10 built-in ABI validation의 전체 `api/` baseline을 사용합니다. `checkKotlinAbi`가 source와 baseline 일치를 먼저 증명한 뒤 `verifyPublicApiBoundaries`가 실제 writer dump의 class/field/fun grammar를 fail-closed로 파싱하고, dump가 선택한 owner/name/descriptor만 ASM 9.9.1로 스캔합니다. descriptor뿐 아니라 generic signature, bound, suspend `Continuation`, Kotlin `FunctionN`, exception, annotation과 class-valued annotation 위치까지 검사하므로 generic erasure 뒤의 Android/Compose/Room/Retrofit/DataStore/SDK 타입 누수도 차단합니다. `Flow`, Kotlin/JDK collection, `Instant`, `Throwable`, GasStation model 타입은 정상 계약 타입입니다.

TestKit은 다섯 `checkKotlinAbi` 뒤 root scanner가 실행되고 `updateKotlinAbi`가 task graph에 없으며 configuration cache 재사용 후 report bytes가 동일함을 확인합니다. 순수 parser 테스트는 malformed descriptor, unknown record, duplicate member, dotted object descriptor를 거부하고 forbidden family를 substring이 아닌 canonical class identity로 판정합니다.

Roborazzi 이름의 screenshot test는 일반 unit-test task에서 제외합니다. 정확한 Roborazzi lifecycle task를 해당 프로젝트에 요청하거나 명시적인 exact-true property를 준 경우에만 포함합니다. 정확한 속성 표와 실행 명령은 [검증 매트릭스](verification-matrix.md#kotlin-및-convention-정책)를 따릅니다.

## 계층별 목적

| 계층 | 대표 테스트 파일 | 무엇을 증명하나 |
| --- | --- | --- |
| `core:model` | `ValueObjectInvariantTest`, `CoordinatesDistanceTest`, `BrandFilterTest`, `core:model/SharedEnumContractTest` | 값 객체 불변식 유지, 좌표 거리 계산, 공유 enum identity와 UI/transport field 배제, RTO/RTX/NHO의 `ALTEUL` 그룹 매칭과 ETC-last 선택 순서 |
| `domain:*` | `StationPriceDeltaTest`, `StationQueryCacheKeyTest`, `AddressLabelNormalizerTest`, `LocationUseCasesTest`, `UpdateSettingsUseCasesTest`, `DomainContractSurfaceTest`, `UserPreferencesTest` | 순수 규칙, 주소 라벨 정규화, 계약 표면, `StationEvent` variant 계약, 캐시 키 계산, 유스케이스 위임 |
| `core:database` | `StationCacheDaoTest`, `StationBucketSnapshotObserverTest`, `StationPriceHistoryDaoTest`, `WatchedStationDaoTest`, `GasStationDatabaseMigrationTest`, compiled `GasStationDatabaseMigrationInstrumentedTest` | Room DAO, atomic marker/row snapshot과 stable ordering, 오래된 cache pruning, exported v1–v5 schema/migration contract, watchlist 최신 캐시용 deterministic latest row/index. Instrumented migration은 device가 있을 때 platform SQLite에서 실행하고, 항상-on host evidence는 Robolectric/compile/assets입니다. |
| `core:network` | `LocalKoreanCoordinateTransformTest`, `NetworkStationFetcherTest`, `ProxyStationFetcherTest`, `NetworkRuntimeConfigTest` | 좌표 변환, direct/proxy typed transport classification·HTTP retry-owner parity·fuel validation, endpoint 모드 설정 주입과 proxy base URL 검증 |
| `core:location` | `AddressLabelFormatterTest`, `AndroidForegroundLocationProviderSurfaceTest`, `AndroidForegroundLocationProviderTest`, `DefaultLocationRepositoryTest`, `LocationAvailabilityFlowTest`, `LocationPermissionStateTest`, `GeocoderAsyncLookupTest`, `AndroidAddressResolverDeviceTest` | Android 위치 조회 표면, API 33+ 지오코더 callback wrapping, Android Address 후보 변환과 domain 정규화 적용, domain location 구현, availability broadcast 반영, device-backed callback smoke |
| `core:datastore` | `UserPreferencesSerializerTest`, `AndroidUserPreferencesDataSourceTest` | storage-local 설정 DTO 직렬화와 DataStore 업데이트 |
| `core:designsystem` | `GasStationThemeDefaultsTest`, `GasStationThemeSurfaceTest`, `GasStationThemeTokensTest`, `ChromeContractsTest`, `BrandIconTest`, `BrandLabelsTest`, Roborazzi snapshot | Urban Signal `#FFFCF2`/`#222222`/`#FFDC00` token, typography/spacing, chrome와 shared primitive, 실제 `Brand` drawable 매핑 |
| `data:settings` | `DefaultSettingsRepositoryTest` | storage-local 설정 DTO와 domain `UserPreferences` 매핑, legacy RTO/RTX/NHO 저장값의 ALTEUL migration, legacy `KAKAO_NAVI`의 `KAKAO_MAP` migration과 현재 이름 재저장, 알 수 없는 enum name fallback |
| `data:station` | `DefaultStationRepositoryTest`, `StationCachePolicyTest`, `StationFreshnessTickerTest`, `LatestRefreshGateTest`, `LatestWatchIntentGateTest`, `data:station/StationRetryPolicyTest`, `StationRemoteDataSourceTest`, `WatchlistRepositoryTest` | 캐시/히스토리/watchlist 조합, timer boundary와 metadata re-projection, refresh/watch key별 latest-write와 tombstone/ABA silence, watch update/remove 공유 gate와 typed committed/superseded 결과, 선택 유종 전용 watchlist cache/history와 가격 없음 identity fallback, retention/pruning·`SearchRefreshed`, typed retry-once 정책과 원격 오류 매핑 |
| `feature:station-list` | `LocationStateMachineTest`, `StationSearchOrchestratorTest`, `RefreshCoordinatorTest`, `StationListStateAssemblerTest`, `StationListCommandQueueTest`, `StationListCommandHandlerTest`, `StationListCommandEffectTest`; `StationListPreferencesTest`, `StationListCommandIntegrationTest`, `StationListWatchMutationTest`, `StationListLocationIntegrationTest`, `StationListRefreshIntegrationTest`; screen/model tests와 Roborazzi states | generation/관찰/refresh/FIFO/projection owner 정책, 얇은 ViewModel의 preferences·command·watch·location·refresh 조합, price-first UI와 typed summary, menu/accessibility, stale/empty/permission/GPS/failure, route lifecycle recovery |
| `feature:settings` | `SettingsViewModelTest`, `SettingsScreenTest`, `SettingsSectionTest`, Roborazzi overview/detail | 설정 상태, update use case dispatch, flat row, 실제 브랜드 tile, route/summary 계약 |
| `feature:watchlist` | `WatchlistViewModelTest`, `WatchlistScreenTest`, `WatchlistItemUiModelTest`, Roborazzi snapshot | 선택 유종 readiness/query 전환, 가격 없음 저장 identity 유지와 명시적 unavailable UI, `CompareViewed` event, 실제 logo와 visible label 미반복, 108–116dp 5행, 200% font scale 확장과 clipping 방지 |
| `app` | `AppStartupGraphTest`, `AppStartupRunnerTest`, `ExternalMapLauncherTest`, `GasStationBottomNavigationTest`, `SplashThemeResourceTest`, `SplashExitAnimatorTest`, `AppIconResourceTest`, `AppIconSourceContractTest`, `NetworkSecurityConfigResourceTest`, `BackupPolicyResourceTest`, `ProdSecretsStartupHookTest` | startup hook 바인딩, icon-only navigation의 접근성 이름/선택·비활성 semantics/ASCII tag/48dp touch target, prod key fail-fast, 앱 리소스, Opinet-only cleartext config, Android backup 비활성화, 외부 지도 provider package/URI와 route -> Play Store app URI -> HTTPS Store fallback·최종 실패 결과. `SplashThemeResourceTest`는 API 30/31, day/night, static foreground, post-theme resource contract를 보호하고, `SplashExitAnimatorTest`는 180ms exit와 animations-off 즉시 제거·one-shot cleanup을 보호합니다. 실제 clipping과 blank-frame 여부는 API 30/API 37 cold-launch evidence가 소유합니다. |
| `demo` 전용 앱 경로 | `DemoSeedStartupHookTest`, `DemoSeedAssetLoaderTest`, `DemoLocationHookIntegrationTest`, `DemoPermissionFlowTest`, `StationPortfolioFlowTest` | seed 적재, permission grant 뒤 고정 위치, 권한 자동 dialog 부재, explicit request의 deny/grant, UI Automator permission-controller 상호작용, RTO/ETC portfolio row, `station-list-watch-toggle` -> `bottom-nav-watchlist` -> `watchlist-card` 실제 관심 플로우. Android Test Orchestrator와 `clearPackageData`는 permission test가 다른 class의 권한 상태에 의존하지 않게 합니다. `StationPortfolioFlowTest`는 Nearby/Settings mutation과 recreation 동기화, 선택 유종의 가격 없는 저장 행 유지, 선택 지도 provider가 기록 Hilt launcher에 전달되는 consumer 경계를 보호합니다. |
| `benchmark` | `StationListBenchmark`, `BaselineProfileGenerator`, `GasStationBenchmarkActions` | startup-to-first-content, list scroll, refresh, watchlist 진입, baseline profile journey |
| `tools:demo-seed` | `DemoSeedGeneratorTest` | seed 생성기와 질의 매트릭스 |

## flavor별 관점

## 제한된 API 24/28/36 기기 증거

기기 계층은 host/Robolectric 계약을 대체하지 않고 platform SQLite migration, permission-controller UI, API 33+ Geocoder callback의 최소 실제 Android 경계를 추가합니다. API 28 PR report-only lane은 정확한 annotation 5개, scheduled lane은 API 24/28에서 app 10 + Room 6, API 36에서 app 10 + Room 6 + Geocoder 1을 zero skip으로 요구합니다. API 24는 명시적으로 provision한 connected AVD, API 28/36은 서로 묶지 않은 Pixel 2 GMD를 사용합니다. inventory, 실패 artifact, 격리와 판정의 운영 계약은 [Android 기기 검증 런북](runbooks/device-verification.md)이 소유합니다.

컴파일, task discovery, parser/fake-tool test는 구현 준비 증거이며 runtime `PASS`가 아닙니다. 지원·권한 있는 정규 wrapper attempt가 없으면 lane 상태는 `NOT RUN`으로 기록합니다.

### `demo`

`demo`는 가장 넓게 검증합니다.

- startup hook이 DB와 선호를 고정 상태로 리셋하는지
- 고정 위치 override가 실제 런타임에 들어오는지
- denied first entry가 Android dialog를 자동으로 열지 않고, explicit CTA의 deny는 guidance에 머물며 grant 뒤에만 고정 좌표 목록이 열리는지 (`DemoPermissionFlowTest`)
- 목록 -> 관심 저장 -> `bottom-nav-watchlist` -> `watchlist-card` 플로우가 실제 기기 테스트에서 동작하는지
- 설정에서 바꾼 유종이 관심 화면의 query/context에 반영되고 선택 유종 가격이 없어도 저장 행을 유지하는지
- 설정에서 바꾼 지도 provider가 Nearby row handoff의 기록 launcher에 전달되는지
- benchmark가 반복 가능한 데이터 경로를 기준으로 측정되는지

### `prod`

`prod`는 네트워크 실통신을 자동화하지는 않지만 아래는 계속 확인합니다.

- `ProdSecretsStartupHook`가 현재 flavor에서 선택되는지
- `prodDebug` 변형이 테스트/assemble 대상에 포함되는지
- 사용자 로컬 `opinet.apikey`가 없을 때 런타임이 fail-fast 하도록 유지되는지
- cleartext 예외가 `www.opinet.co.kr`에만 열리는지
- Android backup/data extraction이 로컬 캐시와 watchlist/settings를 내보내지 않도록 비활성화되어 있는지

## 회귀 위험이 큰 구간

- `DefaultStationRepository`
  스냅샷 마커, 캐시 행, 가격 히스토리, 성공 refresh 이후 pruning, watchlist fallback이 한 곳에서 조합됩니다.
- `StationRetryPolicy`
  direct/proxy 분류, 단일 retry owner, cancellation·superseded 종료와 예기치 않은 예외 전파를 검증합니다.
  <!-- station-data-policy-ref: retry -->[오프라인 전략의 구조화된 `retry` 계약](offline-strategy.md#기계-판독-정책-계약)
- `StationBucketSnapshotObserver` / `StationFreshnessTicker` / `LatestRefreshGate`
  marker와 row의 torn emission, timer 소유권과 metadata 재투영, 과거 요청의 늦은 persistence, replacement entry ABA, superseded analytics/reporting을 각각 독립 테스트로 막습니다.
  <!-- station-data-policy-ref: freshness -->[구조화된 `freshness` 계약](offline-strategy.md#기계-판독-정책-계약)
- `LocationStateMachineTest` / `StationSearchOrchestratorTest`
  permission·GPS·location·address generation의 equal-coordinate/away-back ABA, precise-to-approximate privacy reset, provider cancellation과 silent supersession은 전자가 막습니다. active session의 exception/normal completion, exact-session commit, query 교체의 old result 제거, snapshot을 보존하는 same-query observation-only retry는 후자가 막습니다.
- `RefreshCoordinatorTest`
  lazy work가 body 전에 취소되는 경로, exact-identity cleanup, stale terminal result, query revalidation, inline callback 순서와 느린 address lookup이 refresh/finalization을 막지 않는 경계를 검증합니다.
- `StationListCommandQueueTest` / `StationListCommandHandlerTest` / `StationListCommandEffectTest`
  immutable FIFO, exact-head acknowledgement, stale/tail/zero no-op, handler 실패·취소 시 보존, START별 한 번의 retry와 정상 ack 뒤 다음 head 진행을 분리해 검증합니다. `StationListCommandEffectTest`는 현재 Compose lifecycle handler 이름이며 삭제된 모델을 뜻하지 않습니다.
- `StationListStateAssemblerTest`
  field projection, permission -> GPS -> preference failure/loading -> no-snapshot failure/loading -> results 우선순위, explicit `hasCachedSnapshot`, cached-empty 결과와 station/command list identity 보존을 검증합니다.
- `StationListPreferencesTest` / `StationListCommandIntegrationTest` / `StationListWatchMutationTest` / `StationListLocationIntegrationTest` / `StationListRefreshIntegrationTest`
  얇은 ViewModel의 collaborator composition, preference mutation admission, observation retry 우선순위, command enqueue/외부 지도 logging, committed-only watch analytics, permission 취소와 coordinator result translation을 통합 경계에서 검증합니다.
- `LatestWatchIntentGateTest` / `WatchlistRepositoryTest` / `WatchedStationDaoTest`
  station별 update/remove ABA와 participant tombstone, superseded DAO/side-effect silence, `INSERT IGNORE`의 최초 watched time 보존, `watchedAt` 내림차순·station ID 오름차순의 안정적 결과를 검증합니다.

이 상태 계약의 증거 범위는 host coroutine, Room/Robolectric, app graph와 screenshot/module-edge 회귀입니다. 연결된 target 실행을 이 범위의 증거로 주장하지 않으며, permission/geocoder/demo connected 검증은 아래의 조건부 별도 경로입니다. 정확한 조합은 [검증 매트릭스의 station-list 상태 동시성 집중 회귀](verification-matrix.md#station-list-상태-동시성-집중-회귀)가 단독 소유합니다.

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)
- `AddressLabelNormalizer` / `AddressLabelFormatter`
  Android 지오코더는 `대한민국`, `KR`, 건물 동, 도로명 조각을 섞어 줄 수 있습니다. 순수 정규화는 `domain:location`, Android `Address` 후보 변환은 `core:location` 테스트로 나눠 목록 상단에 raw 주소가 그대로 노출되지 않게 막습니다.
- `AndroidAddressResolverDeviceTest`
  API 33+ Geocoder callback path를 실제 기기/에뮬레이터에서 확인하는 connected smoke test입니다. Provider 출력은 기기와 네트워크 상태에 따라 달라지므로 주소 문자열이 아니라 terminal domain result 도달만 검증합니다.
- `BrandIconTest` / feature Roborazzi
  RTO/RTX/NHO -> `ic_rtx`, ETC -> `ic_etc`와 나머지 실제 drawable mapping, Watchlist의 visible brand label 미반복을 함께 막습니다.
- `AppIconSourceContractTest` / launcher-splash device evidence
  승인된 source에서 옮긴 `ic_brand_drop` path가 color/monochrome/splash에서 공유되는지, Urban Signal 색상과 API 31+ animator override 부재를 확인합니다. Legacy launcher PNG는 mdpi부터 xxxhdpi까지 48/72/96/144/192px를 유지하고 이전 자산 hash 집합을 재사용하지 않아야 합니다. API 30/API 37의 animator scale 1/0 녹화와 launcher adaptive/themed-monochrome screenshot이 mask clipping, blank/residual frame, recognizability를 검증합니다. 이 변경은 app-owned launcher/splash 자산에만 닿고 본문 `Brand` drawable과 layout은 바꾸지 않으므로 feature/body-icon Roborazzi baseline은 갱신하지 않으며 기존 golden을 검증만 합니다.
- Theme/string cleanup
  Feature-owned user copy는 `app`이나 `core:designsystem`으로 이동하지 않습니다. 화면 semantics와 test tag가 회귀 방어선입니다.
- Compose semantics/test tag cleanup
  테스트용 식별자는 사용자 표시 문자열과 분리합니다. 스크린 리더에 필요한 한글 설명은 유지하되, 테스트는 `station-list-watch-toggle`, `bottom-nav-watchlist`, `watchlist-card` 같은 안정적인 ASCII tag를 선택합니다.
- Station-list Main dispatcher test setup
  다섯 integration suite의 `Dispatchers.setMain/resetMain` setup은 `MainDispatcherRule`로 묶어 scheduler 기대를 한 곳에서 관리합니다.
- `DemoSeedStartupHook`
  demo 시작 상태가 흔들리면 문서, 스크린샷, benchmark, UI 테스트가 함께 흔들립니다.
- `ExternalMapLauncher`
  사용자 설정의 지도 앱 선택이 실제 외부 인텐트와 맞아야 합니다. Unit test는 provider별 explicit package, 좌표·이름 URI 직렬화, NAVER runtime `appname`, route -> Play Store app URI -> HTTPS Store fallback, 최종 실패를 보호합니다. Connected test는 운영 launcher를 기록 Hilt binding으로 교체해 Settings에서 선택한 provider가 Nearby handoff까지 전달되는 소비 경계를 보호합니다.
- First usable content policy
  Startup metric은 첫 frame이 아니라 사용 가능한 목록/empty/failure content 기준으로 보고합니다. `StationListFirstContentPolicy`와 `StartupDrawReporter` 테스트가 이 기준을 보호합니다.
- Hero benchmark source set
  `benchmark`는 `com.android.test` 모듈의 main source set(`benchmark/src/main/kotlin`)에서 scenario와 baseline profile generator를 컴파일합니다. 실기기 증거 수집은 `connectedBenchmarkAndroidTest` 경로가 단일 기준입니다.

## 코드 커버리지

`coverageXmlReport`는 JaCoCo 0.8.15와 공개 Gradle/AGP provider API로 JVM `main`, Android `debug`, app `demoDebug`/`prodDebug` unit-test 보고서 18개를 생성합니다. 활성 모듈은 `settings.gradle.kts`의 명시 include 목록이 단일 기준이며 `benchmark`만 device 성능 증거 owner로 제외합니다. Hilt factory/module과 generated Compose singleton만 class 준비 단계에서 제외하고, authored preview라는 이유만으로 넓게 제외하지 않습니다.

각 보고서는 `**/build/reports/coverage/*/report.xml`과 같은 디렉터리의 `manifest-entry.json`을 남깁니다. Root `build/reports/coverage/report-manifest.json`은 23개 Gradle project node, 18개 build module, 18개 entry를 연결합니다. Entry는 production/test source SHA-256, exact test task, prepared class와 JaCoCo class ID, execution/XML raw·semantic identity를 기록합니다. app shared source의 baseline owner는 demo이고 prod 전용 source는 prod가 소유하지만, shared changed line은 두 variant에서 각각 판정합니다.

`verifyCoverageReport`는 current HEAD와 명시 source commit, Git production/test blob inventory, policy/baseline lineage, source classification, denominator, 모듈 floor, baseline 대비 최대 50bp 하락, changed line 8000bp/branch 7000bp를 정수 연산으로 검증합니다. Manifest 결정에는 exact-one class/exec/XML raw identity와 Kotlin/Python이 같은 full report/package/source/class/method/line/counter semantic identity가 포함됩니다. Summary는 report evidence, attributable class denominator와 baseline delta, authored source에 속하지 않아 제외된 XML entry를 함께 남깁니다. Feature는 exact `state`와 `rendering` source unit으로 나뉘며 rendering/design-system/tool/app assembly에는 raw floor를 만들지 않습니다. CI는 report 생성과 이 ratchet 판정을 하나의 차단형 Gradle 호출로 실행합니다.

의존성 verification metadata는 Task 9의 단독 소유입니다. Coverage 작업과 명령은 기존 Gradle verification metadata를 읽을 수 있지만 그 파일을 생성·갱신하지 않습니다.

Coverage ratchet 기준은 `config/quality/coverage-policy.json`과 `config/quality/coverage-baseline.json`입니다. PIT를 함께 담는 `quality-baseline.json`은 별도 mutation evidence이며 coverage baseline을 대체하지 않습니다. Coverage 실행에는 symbolic ref나 축약 SHA가 아닌 현재 40-hex HEAD를 명시합니다.

Baseline 교체는 새 producer/verifier architecture를 먼저 commit한 뒤 그 exact HEAD에서 보고서를 다시 생성하고 `capture --predecessor-commit <same-40-hex-HEAD>`를 실행합니다. Capture는 그 commit의 기존 baseline/policy blob SHA-256을 predecessor로 기록하고 source ancestry, policy lineage, floor 비감소와 200bp 이하 인상만 허용합니다. 기존 baseline blob이 없는 첫 baseline만 predecessor를 생략할 수 있습니다. 실패한 capture는 기존 baseline output을 바꾸지 않습니다.

```bash
SOURCE_COMMIT="$(git rev-parse HEAD)"
./gradlew coverageXmlReport \
  -Pgasstation.coverageSourceCommit="$SOURCE_COMMIT" \
  --warning-mode fail --rerun-tasks
python3 scripts/quality/verify_coverage.py capture \
  --manifest build/reports/coverage/report-manifest.json \
  --policy config/quality/coverage-policy.json \
  --source-commit "$SOURCE_COMMIT" \
  --predecessor-commit "$SOURCE_COMMIT" \
  --output config/quality/coverage-baseline.json
```

```bash
./gradlew coverageXmlReport verifyCoverageReport \
  -Pgasstation.coverageSourceCommit="$(git rev-parse HEAD)" \
  -Pgasstation.coverageEvent=local \
  --warning-mode fail
```

실행 결과는 `build/reports/coverage/verification-summary.json`에서 확인합니다. JSON은 NFC UTF-8, 정렬된 key/record와 정수 counter를 사용하며 wall-clock, 절대 경로, session ID를 baseline identity에 넣지 않습니다.

## Mutation testing (변이 테스트)

PIT 1.25.7은 Android/기기 경로가 아니라 JVM-only `domain:station`, `domain:location`, `domain:settings`의 `main`/`test` source set에만 적용합니다. plugin이 만든 `pitest` task와 `verifyPitestConfiguration`의 독립 실행은 금지되며, route·receipt를 먼저 생성하는 `scripts/quality/run_pitest.sh`가 configuration gate와 세 `pitestVerified` task를 함께 소유합니다. history, dry-run, retry, parallel module 실행, Android/device PIT는 사용하지 않습니다.

초기 baseline은 source commit `d8e19a60b1cc6542bdcefd754ca45ae748fd88a9`의 clean observation 실행에서 얻었습니다.

| 모듈 | KILLED | SURVIVED | NO_COVERAGE | 점수 상태 |
| --- | ---: | ---: | ---: | --- |
| station | 36/66 | 3 | 27 | blocking floor 45 |
| location | 53/68 | 12 | 3 | blocking floor 75 |
| settings | 8/13 | 5 | 0 | score report-only |

점수는 반올림 표시가 아니라 `killed * 100 >= floor * total`의 정수 교차곱으로 판정합니다. settings도 malformed XML, 허용되지 않은 status, source/class identity와 changed-package `NO_COVERAGE` non-increase는 차단하며 점수 floor만 없습니다. 세 모듈 모두 `KILLED`, `SURVIVED`, `NO_COVERAGE` 외 status를 거부합니다.

최종 CI job은 station 45/location 75 native threshold와 strict `verify`를 사용하고 `v*` tag의 unconditional release prerequisite입니다. settings는 계속 score report-only이지만 malformed/status/source identity와 changed-package no-coverage 위반은 job을 실패시킵니다. 최종 source에는 observation으로 돌아가는 property/task/CLI switch가 없고 `observe` subcommand는 blocking configuration을 거부합니다.

Baseline provenance는 순환하지 않습니다. candidate baseline은 predecessor hash(초기값 null)와 `captureEvidenceDigest`만 가지며 자기 hash나 receipt hash를 포함하지 않습니다. candidate를 쓴 뒤 `config/quality/mutation-captures/<candidate-sha256>.json`이라는 별도 append-only receipt가 candidate와 pre-baseline component hash를 묶습니다. 갱신은 CI/agent가 아닌 수동 canonical capture만 허용하며 predecessor baseline과 predecessor verification receipt를 정확히 이어야 합니다.

`hostNeutralMutationIdentity`는 PIT/plugin 버전, target/source-set, report-generation 설정과 Java 21 Temurin family를 비교하고, `perRunExecutionProvenance`는 선택 profile, 실제 Java executable/configuration/tool 관측값과 receipt를 분리해 보존합니다. host-neutral 설정은 `defaultCharacterEncoding=UTF-8`을 명시적으로 소유하고 pre-exec에서 alternate/same-value duplicate `file.encoding`과 관리 인자가 정확히 하나가 아닌 경우를 거부합니다. Gradle 9.6.1 getter가 ambient 기본 charset을 정규화하므로 null/absence를 증거로 사용하지 않습니다.

Darwin arm64 profile은 검토된 고정 content/version hash를 사용합니다. Linux x86_64 profile은 exact `ubuntu-24.04`, `ImageOS=ubuntu24`, `ImageVersion=20260816.277.1`과 runner-images release identity를 먼저 검사한 뒤 `/usr/bin/env`, `/bin/bash`, `/usr/bin/python3.12`, `/usr/bin/git`의 type/mode/content/version을 매 실행 관측합니다. 이 관측값은 고정 executable provenance나 서명된 attestation이 아닙니다. 초기 Linux 비교는 `NOT_ESTABLISHED`이고 검토된 `recapture-transition`만 이를 수립할 수 있습니다. hosted image rotation은 fail closed 후 정책/transition 재검토가 필요하며 최종 binary supply-chain 강화는 Task 9 범위입니다.

## 의도적으로 약하게 보는 것

- 실제 Opinet 서버 상태에 의존하는 end-to-end 네트워크 테스트
- 현재 제품 경로에 없는 실험적 flavor나 폐기된 provider
- 과거 앱 버전 호환을 위한 별도 회귀 시나리오

## 문서와 테스트의 연결

문서에 아래가 적혀 있다면, 테스트도 그 사실을 간접적으로라도 보호해야 합니다.

- demo는 재현 가능한 시작 상태를 제공한다
- 현재 주소는 행정동까지만 보여준다
- stale 결과를 유지한다
- cache snapshot은 marker/rows를 원자적으로 읽고 시간 경과만으로 stale을 다시 낸다
- 최신 refresh intent만 persistence와 event/reporting side effect를 남긴다
- exported Room schema 1–5와 v2→v3 history reset은 host에서 항상 검증하고, device migration 실행은 target이 있을 때만 주장한다
- watchlist는 저장 항목 비교를 지원한다
- watchlist는 선택 유종의 cache/history만 사용하고 가격이 없어도 저장 identity를 유지한다
- 설정은 `UserPreferences`를 편집한다
- legacy `KAKAO_NAVI`는 `KAKAO_MAP`으로 읽히고 새 쓰기는 현재 이름을 저장한다
- 외부 지도는 provider package를 명시하고 최종 fallback 실패를 사용자에게 알린다
- DataStore 첫 emission 전 Nearby와 Settings는 default preference를 렌더링하거나 action에 사용하지 않는다
- 설정 detail은 DataStore commit 성공 뒤에만 돌아가고, 실패하면 이전 값을 유지한다
- Nearby `StationQuery`는 permission, GPS, 좌표, 선호값이 모두 준비된 뒤에만 만들어진다
- demo와 prod가 같은 permission gate를 사용하고, permission dialog는 explicit CTA에서만 열린다
- benchmark는 demo 경로를 기준으로 돈다

새 문서 설명을 추가할 때는 "이 설명이 어떤 테스트 파일에 기대고 있는가"까지 같이 점검하는 편이 안전합니다.

## Build-input integrity와 reproducibility ownership

Build-input tests는 `config/quality/build-inputs.json`, full-SHA action/composite closure, exact Temurin pair, wrapper/SDK identity, dynamic dependency version 거부, redacted receipt와 two-clean-tree unsigned prod APK equality를 소유합니다. 이 샘플은 dependency verification metadata를 운영하지 않으며 TestKit fixture는 필요한 dependency cache seed만 공유합니다. 문서 task discovery는 stable bridge와 dynamic executed-source closure가 소유합니다.

이 계층의 SHA-256은 검토한 bytes와의 integrity만 증명합니다. publisher identity, vulnerability absence, license review, signed 또는 cross-OS APK reproducibility를 증명하지 않습니다. 동일 host에서 두 clean tree의 unsigned prod-release size/hash가 같은 경우만 재현성 `PASS`이며 demo-debug는 후보가 아닙니다. 명령과 receipt 경로는 [검증 매트릭스](verification-matrix.md), 운영 해석은 [Build Input Provenance](runbooks/build-input-provenance.md)를 따릅니다.

현재 convention suite는 검증 매트릭스가 소유하는 단일 `Test` task이며, 정확히 52개 test-class owner와 90개 test method를 다섯 fork에 배치합니다. Nested TestKit build는 `--max-workers=2`를 유지하고 test filter, shard, `forkEvery`, skip, command retry를 사용하지 않습니다. Outer build는 script listener와 dispatch staging이 configuration cache 대상이 아니므로 `--no-configuration-cache`로 실행합니다. 결정적으로 정렬된 scanner root와 그 owner inventory는 차단 계약이고, 실행 중 관측하는 Gradle executor identity와 lane 배치는 진단 정보라서 단독으로 실패를 만들지 않습니다.

Repository/default CI/ordinary-local outer convention `Test` timeout은 reviewed Round-21 five-lane workload bound 27분에 TestKit 초기화와 hosted-runner 편차 8분을 더한 35분이고, nested TestKit module `Test` timeout은 15분입니다. Convention suite는 retry, shard, skip 없이 같은 test inventory를 실행합니다.

실행 결과와 시간은 [Build Velocity](build-velocity.md), Linux와 외부 evidence 판정은 [검증 매트릭스](verification-matrix.md#build-input-provenance와-unsigned-release-재현성)가 소유합니다.
