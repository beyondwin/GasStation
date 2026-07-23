# 테스트 전략

이 문서는 GasStation의 테스트 철학과 계층별 신뢰 범위를 설명하는 단일 출처입니다. 테스트는 "현재 공식 경로인 `demo`와 `prod`가 계속 성립하는가"를 확인하는 데 집중합니다. 정확한 실행 명령은 `docs/verification-matrix.md`를 보고, 여기서는 어떤 층을 어떤 테스트로 막고 있는지 설명합니다.

## 기본 원칙

- 가장 복잡한 조합 로직은 저장소와 ViewModel 테스트로 먼저 막습니다.
- `demo`는 별도 예외 경로가 아니라 정식 제품 경로이므로 startup, seed, UI 플로우를 따로 검증합니다.
- `prod`는 실제 API 키/네트워크를 요구하는 대신, 빌드/그래프/런타임 설정 경로가 깨지지 않는지를 unit/assemble 수준에서 확인합니다.
- 이미 문서로 약속한 사용자 흐름은 가능한 한 테스트 파일 이름으로도 추적 가능해야 합니다.
- Android library 공통 unit test 의존성(`junit`, `kotlinx-coroutines-test`, `androidx.test:core`, `robolectric`)은 `gasstation.android.library` 컨벤션이 소유합니다.
- Android 모듈은 최신 AndroidX가 요구하는 `compileSdk 37`을 사용하되, 안정 Robolectric 4.16.1의 지원 상한에 맞춰 로컬 unit test SDK를 `config/robolectric/robolectric.properties`의 API 36으로 고정합니다. API 36 Robolectric 런타임에는 Java 21 이상을 사용하고, 앱의 Java/Kotlin bytecode target은 JVM 17을 유지합니다. app/library convention이 이 공통 test resource를 연결하며, API 37 동작은 Robolectric 안정 지원 전까지 connected test로 확인합니다.
- Compose Android library의 UI test/debug 의존성(Compose test BOM, UI test JUnit4, UI tooling, UI test manifest)은 `gasstation.android.library.compose` 컨벤션이 소유합니다. 모듈 build file에는 Turbine, MockWebServer, `kotlin.test`, project test dependency, androidTest smoke dependency처럼 모듈별로 필요한 의존성만 둡니다.
- Compose test rule은 `androidx.compose.ui.test.junit4.v2` 환경을 사용합니다. v2의 `StandardTestDispatcher` 기반 동기화 계약을 유지하고, deprecated v1 test-environment import는 `verifyNoDeprecatedComposeTestApis`가 차단합니다.
- Compose 테스트 selector는 ASCII `testTag`를 사용하고, 한글 사용자 문구와 스크린 리더용 설명은 `contentDescription` 같은 접근성 semantics에 남깁니다.
- 새로 추가하거나 반복 setup을 정리하는 coroutine ViewModel 테스트에서 `Dispatchers.Main`이 필요하면 feature-local rule/helper로 설정을 중앙화합니다. 현재 station-list 테스트는 `MainDispatcherRule`이 이 계약을 소유합니다.
- Build velocity settings are valid only while the verification matrix stays green. If `parallel`, build cache, or configuration cache changes a task result, treat it as a build correctness issue and fix the build boundary before changing product behavior.

## 계층별 목적

| 계층 | 대표 테스트 파일 | 무엇을 증명하나 |
| --- | --- | --- |
| `core:model` | `ValueObjectInvariantTest`, `CoordinatesDistanceTest`, `BrandFilterTest`, `core:model/SharedEnumContractTest` | 값 객체 불변식 유지, 좌표 거리 계산, 공유 enum identity와 UI/transport field 배제, RTO/RTX/NHO의 `ALTEUL` 그룹 매칭과 ETC-last 선택 순서 |
| `domain:*` | `StationPriceDeltaTest`, `StationQueryCacheKeyTest`, `AddressLabelNormalizerTest`, `LocationUseCasesTest`, `UpdateSettingsUseCasesTest`, `DomainContractSurfaceTest`, `UserPreferencesTest` | 순수 규칙, 주소 라벨 정규화, 계약 표면, `StationEvent` variant 계약, 캐시 키 계산, 유스케이스 위임 |
| `core:database` | `StationCacheDaoTest`, `StationPriceHistoryDaoTest`, `WatchedStationDaoTest`, `GasStationDatabaseMigrationTest` | Room DAO와 migration, 오래된 cache pruning, watchlist 최신 캐시용 deterministic latest row/index |
| `core:network` | `LocalKoreanCoordinateTransformTest`, `NetworkStationFetcherTest`, `ProxyStationFetcherTest`, `NetworkRuntimeConfigTest` | 좌표 변환, direct/proxy 원격 fetcher, endpoint 모드 설정 주입과 proxy base URL 검증 |
| `core:location` | `AddressLabelFormatterTest`, `AndroidForegroundLocationProviderSurfaceTest`, `AndroidForegroundLocationProviderTest`, `DefaultLocationRepositoryTest`, `LocationAvailabilityFlowTest`, `LocationPermissionStateTest`, `GeocoderAsyncLookupTest`, `AndroidAddressResolverDeviceTest` | Android 위치 조회 표면, API 33+ 지오코더 callback wrapping, Android Address 후보 변환과 domain 정규화 적용, domain location 구현, availability broadcast 반영, device-backed callback smoke |
| `core:datastore` | `UserPreferencesSerializerTest`, `AndroidUserPreferencesDataSourceTest` | storage-local 설정 DTO 직렬화와 DataStore 업데이트 |
| `core:designsystem` | `GasStationThemeDefaultsTest`, `GasStationThemeSurfaceTest`, `GasStationThemeTokensTest`, `ChromeContractsTest`, `BrandIconTest`, `BrandLabelsTest`, Roborazzi snapshot | Urban Signal `#FFFCF2`/`#222222`/`#FFDC00` token, typography/spacing, chrome와 shared primitive, 실제 `Brand` drawable 매핑 |
| `data:settings` | `DefaultSettingsRepositoryTest` | storage-local 설정 DTO와 domain `UserPreferences` 매핑, legacy RTO/RTX/NHO 저장값의 ALTEUL migration과 안정적인 재저장, 알 수 없는 enum name fallback |
| `data:station` | `DefaultStationRepositoryTest`, `StationCachePolicyTest`, `data:station/StationRetryPolicyTest`, `StationRemoteDataSourceTest`, `WatchlistRepositoryTest` | 캐시/히스토리/watchlist 조합, stale/retention 규칙, 성공 refresh 이후 pruning과 `SearchRefreshed` event, `Timeout`/`Network` retry once 정책과 retry event, 원격 오류 매핑 |
| `feature:station-list` | `feature:station-list/LocationStateMachineTest`, `feature:station-list/StationSearchOrchestratorTest`, `StationListViewModelTest`, `StationListScreenTest`, `StationListRoutePolicyTest`, `StationListBannerModelTest`, `StationListItemUiModelTest`, `GpsAvailabilityMonitorTest`, Roborazzi states | 위치 상태 전이, query/cache/failure orchestration, price-first row와 2줄 typed summary, 반경/유종/브랜드 menu interaction, 320dp popup containment와 마지막 항목 scroll, 네 가지 가격 이력 상태, 320dp·200% 글꼴의 summary/station metadata, stale/empty/permission/GPS/failure, route lifecycle 기반 availability 관찰과 권한/GPS recovery |
| `feature:settings` | `SettingsViewModelTest`, `SettingsScreenTest`, `SettingsSectionTest`, Roborazzi overview/detail | 설정 상태, update use case dispatch, flat row, 실제 브랜드 tile, route/summary 계약 |
| `feature:watchlist` | `WatchlistViewModelTest`, `WatchlistScreenTest`, `WatchlistItemUiModelTest`, Roborazzi snapshot | 관심 비교 상태, `CompareViewed` event, 실제 logo와 visible label 미반복, 108–116dp 5행, 200% font scale 확장과 clipping 방지 |
| `app` | `AppStartupGraphTest`, `AppStartupRunnerTest`, `ExternalMapLauncherTest`, `GasStationBottomNavigationTest`, `SplashThemeResourceTest`, `AppIconResourceTest`, `NetworkSecurityConfigResourceTest`, `BackupPolicyResourceTest`, `ProdSecretsStartupHookTest` | startup hook 바인딩, icon-only navigation의 접근성 이름/선택·비활성 semantics/ASCII tag/48dp touch target, prod key fail-fast, 앱 리소스, Opinet-only cleartext config, Android backup 비활성화, 외부 지도 인텐트 |
| `demo` 전용 앱 경로 | `DemoSeedStartupHookTest`, `DemoSeedAssetLoaderTest`, `DemoLocationHookIntegrationTest`, `StationPortfolioFlowTest` | seed 적재, 고정 위치, RTO/ETC portfolio row, `station-list-watch-toggle` -> `bottom-nav-watchlist` -> `watchlist-card` 실제 관심 플로우. `StationPortfolioFlowTest.demoSettingsAndNearby_sharePersistedPreferencesAcrossNavigationAndRecreation`은 Nearby와 Settings의 mutation이 DataStore에 commit된 뒤 서로와 activity recreation에 같은 선호값으로 동기화되는지 보호합니다. |
| `benchmark` | `StationListBenchmark`, `BaselineProfileGenerator`, `GasStationBenchmarkActions` | startup-to-first-content, list scroll, refresh, watchlist 진입, baseline profile journey |
| `tools:demo-seed` | `DemoSeedGeneratorTest` | seed 생성기와 질의 매트릭스 |

## flavor별 관점

### `demo`

`demo`는 가장 넓게 검증합니다.

- startup hook이 DB와 선호를 고정 상태로 리셋하는지
- 고정 위치 override가 실제 런타임에 들어오는지
- 목록 -> 관심 저장 -> `bottom-nav-watchlist` -> `watchlist-card` 플로우가 실제 기기 테스트에서 동작하는지
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
  일시적 refresh 실패는 data 계층에서 한 번만 재시도하고, cancellation, 재시도 불가 실패, 예기치 않은 두 번째 예외는 즉시 전파해야 합니다.
- `LocationStateMachine`, `StationSearchOrchestrator`, `StationListViewModel`
  권한/GPS/주소 라벨은 location state machine, query/cache/blocking failure는 orchestrator, loading/effect/action dispatch와 최종 UI 조합은 ViewModel에서 갈립니다.
- `AddressLabelNormalizer` / `AddressLabelFormatter`
  Android 지오코더는 `대한민국`, `KR`, 건물 동, 도로명 조각을 섞어 줄 수 있습니다. 순수 정규화는 `domain:location`, Android `Address` 후보 변환은 `core:location` 테스트로 나눠 목록 상단에 raw 주소가 그대로 노출되지 않게 막습니다.
- `AndroidAddressResolverDeviceTest`
  API 33+ Geocoder callback path를 실제 기기/에뮬레이터에서 확인하는 connected smoke test입니다. Provider 출력은 기기와 네트워크 상태에 따라 달라지므로 주소 문자열이 아니라 terminal domain result 도달만 검증합니다.
- `BrandIconTest` / feature Roborazzi
  RTO/RTX/NHO -> `ic_rtx`, ETC -> `ic_etc`와 나머지 실제 drawable mapping, Watchlist의 visible brand label 미반복을 함께 막습니다.
- Theme/string cleanup
  Feature-owned user copy는 `app`이나 `core:designsystem`으로 이동하지 않습니다. 화면 semantics와 test tag가 회귀 방어선입니다.
- Compose semantics/test tag cleanup
  테스트용 식별자는 사용자 표시 문자열과 분리합니다. 스크린 리더에 필요한 한글 설명은 유지하되, 테스트는 `station-list-watch-toggle`, `bottom-nav-watchlist`, `watchlist-card` 같은 안정적인 ASCII tag를 선택합니다.
- Station-list Main dispatcher test setup
  `StationListViewModelTest`의 `Dispatchers.setMain/resetMain` 반복은 `MainDispatcherRule`로 묶어 scheduler 기대를 한 곳에서 관리합니다.
- `DemoSeedStartupHook`
  demo 시작 상태가 흔들리면 문서, 스크린샷, benchmark, UI 테스트가 함께 흔들립니다.
- `ExternalMapLauncher`
  사용자 설정의 지도 앱 선택이 실제 외부 인텐트와 맞아야 합니다.
- First usable content policy
  Startup metric은 첫 frame이 아니라 사용 가능한 목록/empty/failure content 기준으로 보고합니다. `StationListFirstContentPolicy`와 `StartupDrawReporter` 테스트가 이 기준을 보호합니다.
- Hero benchmark source set
  `benchmark`는 `com.android.test` 모듈의 main source set(`benchmark/src/main/kotlin`)에서 scenario와 baseline profile generator를 컴파일합니다. 실기기 증거 수집은 `connectedBenchmarkAndroidTest` 경로가 단일 기준입니다.

## 코드 커버리지

`coverageXmlReport`는 JaCoCo 0.8.15로 JVM 모듈과 Android debug unit-test 실행 데이터를 한 번에 수집합니다. app은 `demoDebug` authored class 전체와 prod 전용 class를 함께 분석하고, 나머지 Android 모듈은 debug authored class를 분석합니다. Hilt factory/module, Compose singleton, preview 생성 코드는 분모에서 제외합니다.

통합 XML은 `build/reports/coverage/report.xml`에 생성되며 main/tag push의 Codecov 업로드가 이 파일을 사용합니다. 현재 커버리지는 신호 수집용이고, 의미 있는 모듈별 floor가 별도로 설계되기 전까지 blocking coverage threshold는 두지 않습니다.

## Mutation testing (변이 테스트)

라인 커버리지 숫자만으로는 테스트가 실제 결함을 잡는지 알 수 없습니다. JVM-only 모듈(`gasstation.jvm.library`)에 변이 테스트를 적용해 테스트의 결함 탐지력을 측정·기록합니다. Pitest는 Android 모듈에서 불안정하므로 JVM 모듈로 한정합니다. 현재 `domain:station`, `domain:settings`, `domain:location` 세 모듈을 다룹니다.

### `domain:station` (floor 게이트)

- **대상 선정 이유:** JVM-only 모듈 중 라인 커버리지가 가장 약한(48.57%) 1순위 모듈.
- **실행 명령:** `./gradlew :domain:station:pitest`. 리포트는 `domain/station/build/reports/pitest/`.
- **현재 변이 점수(2026-06-06 기준):** 보강 전 `Killed 19/60 (32%)`, test strength 70%, SURVIVED 8. 보강 후 `Killed 28/60 (47%)`, **test strength 97%**, SURVIVED 1. (전체 점수가 낮은 이유는 `no-coverage` 변이 31건 때문이며, 커버된 변이 기준 결함 탐지력은 test strength가 나타냅니다.) 남은 SURVIVED 1건은 `StationPriceDelta.from`의 `<` 경계 변이로, 상위 분기에서 `==` 케이스가 이미 처리돼 동작이 동일한 equivalent mutant라 추가 테스트로 잡을 수 없습니다.
- **보강한 테스트:** `StationPriceDeltaTest`에 0(비음수 경계) 허용과 음수 previous price 거부 케이스를, `StationQueryCacheKeyTest`에 좌표→버킷의 정확한 곱셈/나눗셈 결과 검증과 `bucketMeters` 비양수 거부 케이스를 추가했습니다.
- **게이트:** `mutationThreshold.set(40)` floor 게이트로 점수 하락을 막습니다. 현재 점수 47%가 40 floor를 넘어 통과하며, floor를 60으로 올리면 `Mutation score of 47 is below threshold of 60`으로 빌드가 실패함을 확인했습니다. report-only 베이스라인이 안정화된 모듈만 이렇게 게이트화합니다.

### `domain:settings` (report-only)

- **실행 명령:** `./gradlew :domain:settings:pitest`. 리포트는 `domain/settings/build/reports/pitest/`.
- **현재 변이 점수(2026-06-06 기준):** `Killed 5/22 (23%)`, test strength 33%, NO_COVERAGE 7, SURVIVED 10.
- **SURVIVED 분석(보강 불가):** SURVIVED 10건은 전부 use case의 `suspend operator fun invoke`에서 발생하는 coroutine-suspend **equivalent mutant**입니다(suspend 디스패치 라인의 `NegateConditionals`, Unit 반환의 `NullReturnVals`). 입력 케이스로는 동작 차이를 만들 수 없어 추가 테스트로 잡히지 않습니다. 따라서 별도 보강 없이 baseline만 기록합니다. (플랜은 SURVIVED 0을 예상했으나 실제는 10건이며 모두 등가 변이입니다.)
- **report-only 결정:** equivalent mutant 비중이 높아 게이트화하지 않습니다.

### `domain:location` (report-only)

- **실행 명령:** `./gradlew :domain:location:pitest`. 리포트는 `domain/location/build/reports/pitest/`.
- **현재 변이 점수(2026-06-06 기준):** 보강 전 `Killed 51/68 (75%)`, test strength 78%, SURVIVED 14. 보강 후 `Killed 55/68 (81%)`, **test strength 85%**, SURVIVED 10.
- **보강한 테스트:** `AddressLabelNormalizerTest`에 (1) 선행 noise 토큰을 건너뛰고 bare metro(`서울`)를 이름으로 골라내는 fallback 경로, (2) district 앞의 가장 가까운 `시`/`도` 지역 선택, (3) dong 앞 trailing noise를 건너뛰고 행정 district를 고르는 케이스를 추가해 `findFallbackRegionIndexBefore`/`findLastAdminIndexBefore`의 실제 로직 갭(SURVIVED 4건)을 제거했습니다.
- **SURVIVED 잔여 분석:** 남은 10건은 문자 범위(`'가'..'힣'`) 경계 변이와 인덱스 경계 변이(`dongIndex < 0`, `districtIndex >= 0` 등)로, 추적 결과 동작이 동일한 equivalent/near-equivalent mutant입니다.
- **report-only 결정:** baseline 기록만 하고 게이트화는 점수 안정화 후 별도 결정합니다.

> 변이 테스트는 느리므로 세 모듈 모두 CI에 포함하지 않고 로컬/온디맨드로 실행합니다. `domain:station`만 `mutationThreshold` floor 게이트를 가지며, 이는 `:domain:station:pitest`를 직접 실행할 때만 적용됩니다.

## 의도적으로 약하게 보는 것

- 실제 Opinet 서버 상태에 의존하는 end-to-end 네트워크 테스트
- 현재 제품 경로에 없는 실험적 flavor나 폐기된 provider
- 과거 앱 버전 호환을 위한 별도 회귀 시나리오

## 문서와 테스트의 연결

문서에 아래가 적혀 있다면, 테스트도 그 사실을 간접적으로라도 보호해야 합니다.

- demo는 재현 가능한 시작 상태를 제공한다
- 현재 주소는 행정동까지만 보여준다
- stale 결과를 유지한다
- watchlist는 저장 항목 비교를 지원한다
- 설정은 `UserPreferences`를 편집한다
- DataStore 첫 emission 전 Nearby와 Settings는 default preference를 렌더링하거나 action에 사용하지 않는다
- 설정 detail은 DataStore commit 성공 뒤에만 돌아가고, 실패하면 이전 값을 유지한다
- Nearby `StationQuery`는 permission, GPS, 좌표, 선호값이 모두 준비된 뒤에만 만들어진다
- benchmark는 demo 경로를 기준으로 돈다

새 문서 설명을 추가할 때는 "이 설명이 어떤 테스트 파일에 기대고 있는가"까지 같이 점검하는 편이 안전합니다.
