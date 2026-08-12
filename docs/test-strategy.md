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
- agent contract test는 `v*` tag의 `release-publish`가 전체 CI job을 선행 조건으로 사용하고, job 범위의 `contents: write`, release note, 다운로드한 APK, `gh release create` 경로를 잃지 않도록 보호합니다.
- Build velocity settings are valid only while the verification matrix stays green. If `parallel`, build cache, or configuration cache changes a task result, treat it as a build correctness issue and fix the build boundary before changing product behavior.

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
| `data:station` | `DefaultStationRepositoryTest`, `StationCachePolicyTest`, `StationFreshnessTickerTest`, `LatestRefreshGateTest`, `data:station/StationRetryPolicyTest`, `StationRemoteDataSourceTest`, `WatchlistRepositoryTest` | 캐시/히스토리/watchlist 조합, timer boundary와 metadata 재projection, key별 latest-write/tombstone/ABA side-effect silence, 선택 유종 전용 watchlist cache/history와 가격 없음 identity fallback, retention/pruning·`SearchRefreshed`, typed retry-once 정책과 원격 오류 매핑 |
| `feature:station-list` | `feature:station-list/LocationStateMachineTest`, `feature:station-list/StationSearchOrchestratorTest`, `StationListViewModelTest`, `StationListScreenTest`, `StationListRoutePolicyTest`, `StationListBannerModelTest`, `StationListItemUiModelTest`, `GpsAvailabilityMonitorTest`, Roborazzi states | 위치 상태 전이, denied가 retained coordinate/cache/refresh보다 먼저 이기는 gate, query/cache/failure orchestration, price-first row와 2줄 typed summary, 반경/유종/브랜드 menu interaction, 320dp popup containment와 마지막 항목 scroll, 네 가지 가격 이력 상태, 320dp·200% 글꼴의 summary/station metadata, stale/empty/permission/GPS/failure, route lifecycle 기반 availability 관찰과 권한/GPS recovery |
| `feature:settings` | `SettingsViewModelTest`, `SettingsScreenTest`, `SettingsSectionTest`, Roborazzi overview/detail | 설정 상태, update use case dispatch, flat row, 실제 브랜드 tile, route/summary 계약 |
| `feature:watchlist` | `WatchlistViewModelTest`, `WatchlistScreenTest`, `WatchlistItemUiModelTest`, Roborazzi snapshot | 선택 유종 readiness/query 전환, 가격 없음 저장 identity 유지와 명시적 unavailable UI, `CompareViewed` event, 실제 logo와 visible label 미반복, 108–116dp 5행, 200% font scale 확장과 clipping 방지 |
| `app` | `AppStartupGraphTest`, `AppStartupRunnerTest`, `ExternalMapLauncherTest`, `GasStationBottomNavigationTest`, `SplashThemeResourceTest`, `SplashExitAnimatorTest`, `AppIconResourceTest`, `AppIconSourceContractTest`, `NetworkSecurityConfigResourceTest`, `BackupPolicyResourceTest`, `ProdSecretsStartupHookTest` | startup hook 바인딩, icon-only navigation의 접근성 이름/선택·비활성 semantics/ASCII tag/48dp touch target, prod key fail-fast, 앱 리소스, Opinet-only cleartext config, Android backup 비활성화, 외부 지도 provider package/URI와 route -> Play Store app URI -> HTTPS Store fallback·최종 실패 결과. `SplashThemeResourceTest`는 API 30/31, day/night, static foreground, post-theme resource contract를 보호하고, `SplashExitAnimatorTest`는 180ms exit와 animations-off 즉시 제거·one-shot cleanup을 보호합니다. 실제 clipping과 blank-frame 여부는 API 30/API 37 cold-launch evidence가 소유합니다. |
| `demo` 전용 앱 경로 | `DemoSeedStartupHookTest`, `DemoSeedAssetLoaderTest`, `DemoLocationHookIntegrationTest`, `DemoPermissionFlowTest`, `StationPortfolioFlowTest` | seed 적재, permission grant 뒤 고정 위치, 권한 자동 dialog 부재, explicit request의 deny/grant, UI Automator permission-controller 상호작용, RTO/ETC portfolio row, `station-list-watch-toggle` -> `bottom-nav-watchlist` -> `watchlist-card` 실제 관심 플로우. Android Test Orchestrator와 `clearPackageData`는 permission test가 다른 class의 권한 상태에 의존하지 않게 합니다. `StationPortfolioFlowTest`는 Nearby/Settings mutation과 recreation 동기화, 선택 유종의 가격 없는 저장 행 유지, 선택 지도 provider가 기록 Hilt launcher에 전달되는 consumer 경계를 보호합니다. |
| `benchmark` | `StationListBenchmark`, `BaselineProfileGenerator`, `GasStationBenchmarkActions` | startup-to-first-content, list scroll, refresh, watchlist 진입, baseline profile journey |
| `tools:demo-seed` | `DemoSeedGeneratorTest` | seed 생성기와 질의 매트릭스 |

## flavor별 관점

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
  <!-- station-data-policy-ref: retry -->[오프라인 전략의 구조화된 `retry` 계약](offline-strategy.md#기계-판독-정책-계약)에 맞춰 direct/proxy 분류, 단일 retry owner, cancellation·superseded 종료와 예기치 않은 예외 전파를 검증합니다.
- `StationBucketSnapshotObserver` / `StationFreshnessTicker` / `LatestRefreshGate`
  marker와 row의 torn emission, <!-- station-data-policy-ref: freshness -->[구조화된 `freshness` 계약](offline-strategy.md#기계-판독-정책-계약)의 timer 소유권과 metadata 재투영, 과거 요청의 늦은 persistence, replacement entry ABA, superseded analytics/reporting을 각각 독립 테스트로 막습니다.
- `LocationStateMachine`, `StationSearchOrchestrator`, `StationListViewModel`
  권한/GPS/주소 라벨은 location state machine, query/cache/blocking failure는 orchestrator, loading/effect/action dispatch와 최종 UI 조합은 ViewModel에서 갈립니다. denied permission은 demo override, 보존 좌표, cache/render, refresh보다 우선하며 GPS 설정 안내와 섞이지 않아야 합니다.
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
  `StationListViewModelTest`의 `Dispatchers.setMain/resetMain` 반복은 `MainDispatcherRule`로 묶어 scheduler 기대를 한 곳에서 관리합니다.
- `DemoSeedStartupHook`
  demo 시작 상태가 흔들리면 문서, 스크린샷, benchmark, UI 테스트가 함께 흔들립니다.
- `ExternalMapLauncher`
  사용자 설정의 지도 앱 선택이 실제 외부 인텐트와 맞아야 합니다. Unit test는 provider별 explicit package, 좌표·이름 URI 직렬화, NAVER runtime `appname`, route -> Play Store app URI -> HTTPS Store fallback, 최종 실패를 보호합니다. Connected test는 운영 launcher를 기록 Hilt binding으로 교체해 Settings에서 선택한 provider가 Nearby handoff까지 전달되는 소비 경계를 보호합니다.
- First usable content policy
  Startup metric은 첫 frame이 아니라 사용 가능한 목록/empty/failure content 기준으로 보고합니다. `StationListFirstContentPolicy`와 `StartupDrawReporter` 테스트가 이 기준을 보호합니다.
- Hero benchmark source set
  `benchmark`는 `com.android.test` 모듈의 main source set(`benchmark/src/main/kotlin`)에서 scenario와 baseline profile generator를 컴파일합니다. 실기기 증거 수집은 `connectedBenchmarkAndroidTest` 경로가 단일 기준입니다.

## 코드 커버리지

`coverageXmlReport`는 JaCoCo 0.8.15로 JVM 모듈과 Android debug unit-test 실행 데이터를 한 번에 수집합니다. app은 `demoDebug` authored class 전체와 prod 전용 class를 함께 분석하고, 나머지 Android 모듈은 debug authored class를 분석합니다. Hilt factory/module, Compose singleton, preview 생성 코드는 분모에서 제외합니다.

통합 XML은 `build/reports/coverage/report.xml`에 생성되며 main/tag push의 Codecov 업로드가 이 파일을 사용합니다. 현재 커버리지는 신호 수집용이고, 의미 있는 모듈별 floor가 별도로 설계되기 전까지 blocking coverage threshold는 두지 않습니다.

재현 가능한 관측 기준은 `config/quality/quality-baseline.json`입니다. 현재 checked-in baseline의 historical `sourceCommit`은 실행 시작 커밋 `7b8c149c9f792aaf43cc00a94ba671929008979e`이며, 이후 재생성은 아래처럼 새로 생성한 보고서와 현재 HEAD를 함께 기록합니다.

```bash
./gradlew coverageXmlReport :domain:station:pitest :domain:location:pitest :domain:settings:pitest --warning-mode fail --rerun-tasks
python3 scripts/quality/capture_baseline.py --commit "$(git rev-parse HEAD)" --coverage build/reports/coverage/report.xml --pitest domain/station/build/reports/pitest/mutations.xml --pitest domain/location/build/reports/pitest/mutations.xml --pitest domain/settings/build/reports/pitest/mutations.xml --output config/quality/quality-baseline.json
```

JaCoCo/PIT XML은 Git SHA를 포함하지 않으므로 `--commit`은 필수 명시값입니다. 보고서 생성기가 입력별 provenance를 제공할 때는 `--input-commit PATH=SHA`를 각 입력에 붙여 하나라도 다른 SHA인 캡처를 거부할 수 있습니다. JSON은 키 순서를 고정하고, 비교 대상 수치에는 wall-clock capture time을 넣지 않습니다.

## Mutation testing (변이 테스트)

라인 커버리지 숫자만으로는 테스트가 실제 결함을 잡는지 알 수 없습니다. JVM-only 모듈(`gasstation.jvm.library`)에 변이 테스트를 적용해 테스트의 결함 탐지력을 측정·기록합니다. Pitest는 Android 모듈에서 불안정하므로 JVM 모듈로 한정합니다. 현재 `domain:station`, `domain:settings`, `domain:location` 세 모듈을 다룹니다.

### `domain:station` (floor 게이트)

- **대상 선정 이유:** JVM-only 모듈 중 라인 커버리지가 가장 약한(48.57%) 1순위 모듈.
- **실행 명령:** `./gradlew :domain:station:pitest`. 리포트는 `domain/station/build/reports/pitest/`.
- **관측 baseline (sourceCommit `7b8c149`, 2026-08-12):** `Killed 32/65 (49%)`, `NO_COVERAGE 31`, `SURVIVED 2`, test strength 94%. 전체 점수가 낮은 이유는 no-coverage 변이 31건이며, baseline JSON의 상태별 카운터가 후속 ratchet의 단일 기준입니다.
- **보강한 테스트:** `StationPriceDeltaTest`에 0(비음수 경계) 허용과 음수 previous price 거부 케이스를, `StationQueryCacheKeyTest`에 좌표→버킷의 정확한 곱셈/나눗셈 결과 검증과 `bucketMeters` 비양수 거부 케이스를 추가했습니다.
- **게이트:** `mutationThreshold.set(40)` floor 게이트로 점수 하락을 막습니다. 현재 baseline의 49%는 이 floor를 넘습니다. 47%와 `Mutation score of 47 is below threshold of 60`은 이전 60-mutant 실험의 historical 결과이며, 현재 65-mutant baseline 수치와 혼용하지 않습니다. report-only 베이스라인이 안정화된 모듈만 이렇게 게이트화합니다.

### `domain:settings` (report-only)

- **실행 명령:** `./gradlew :domain:settings:pitest`. 리포트는 `domain/settings/build/reports/pitest/`.
- **관측 baseline (sourceCommit `7b8c149`, 2026-08-12):** `Killed 8/13 (62%)`, test strength 62%, `NO_COVERAGE 0`, `SURVIVED 5`.
- **SURVIVED 분석:** 현재 baseline의 SURVIVED 5건은 다음 mutation-review task에서 개별적으로 분류합니다. 과거의 10-survivor equivalent-mutant 분석은 현재 결과에 적용하지 않으며, 이 문서는 다섯 개를 자동으로 equivalent라고 주장하지 않습니다.
- **report-only 결정:** 현재 다섯 SURVIVED mutant의 개별 검토 전까지는 report-only로 유지합니다.

### `domain:location` (report-only)

- **실행 명령:** `./gradlew :domain:location:pitest`. 리포트는 `domain/location/build/reports/pitest/`.
- **관측 baseline (sourceCommit `7b8c149`, 2026-08-12):** `Killed 55/68 (81%)`, test strength 85%, `NO_COVERAGE 3`, `SURVIVED 10`.
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
