# 테스트 전략

이 문서는 GasStation의 테스트 철학과 계층별 신뢰 범위를 설명하는 단일 출처입니다. 테스트는 "현재 공식 경로인 `demo`와 `prod`가 계속 성립하는가"를 확인하는 데 집중합니다. 정확한 실행 명령은 `docs/verification-matrix.md`를 보고, 여기서는 어떤 층을 어떤 테스트로 막고 있는지 설명합니다.

## 기본 원칙

- 가장 복잡한 조합 로직은 저장소와 ViewModel 테스트로 먼저 막습니다.
- `demo`는 별도 예외 경로가 아니라 정식 제품 경로이므로 startup, seed, UI 플로우를 따로 검증합니다.
- `prod`는 실제 API 키/네트워크를 요구하는 대신, 빌드/그래프/런타임 설정 경로가 깨지지 않는지를 unit/assemble 수준에서 확인합니다.
- 이미 문서로 약속한 사용자 흐름은 가능한 한 테스트 파일 이름으로도 추적 가능해야 합니다.

## 계층별 목적

| 계층 | 대표 테스트 파일 | 무엇을 증명하나 |
| --- | --- | --- |
| `core:model` | `ValueObjectInvariantTest`, `BrandFilterTest`, `core:model/SharedEnumContractTest` | 값 객체 불변식 유지, 공유 enum identity와 UI/transport field 배제 |
| `domain:*` | `StationPriceDeltaTest`, `StationQueryCacheKeyTest`, `LocationUseCasesTest`, `UpdateSettingsUseCasesTest`, `DomainContractSurfaceTest`, `UserPreferencesTest` | 순수 규칙, 계약 표면, `StationEvent` variant 계약, 캐시 키 계산, 유스케이스 위임 |
| `core:database` | `StationCacheDaoTest`, `StationPriceHistoryDaoTest`, `WatchedStationDaoTest`, `GasStationDatabaseMigrationTest` | Room DAO와 migration, 오래된 cache pruning, watchlist 최신 캐시용 deterministic latest row/index |
| `core:network` | `LocalKoreanCoordinateTransformTest`, `NetworkStationFetcherTest`, `NetworkRuntimeConfigTest` | 좌표 변환, 원격 fetcher, 설정 주입 |
| `core:location` | `AddressLabelFormatterTest`, `AndroidForegroundLocationProviderSurfaceTest`, `AndroidForegroundLocationProviderTest`, `DefaultLocationRepositoryTest`, `LocationAvailabilityFlowTest`, `LocationPermissionStateTest`, `GeocoderAsyncLookupTest` | Android 위치 조회 표면, API 33+ 지오코더 callback wrapping, 주소 라벨 정규화, domain location 구현, availability broadcast 반영 |
| `core:datastore` | `UserPreferencesSerializerTest`, `AndroidUserPreferencesDataSourceTest` | 커스텀 serializer와 DataStore 업데이트 |
| `core:designsystem` | `GasStationThemeDefaultsTest`, `GasStationThemeSurfaceTest`, `GasStationThemeTokensTest`, `ChromeContractsTest`, `BrandIconTest`, `BrandLabelsTest` | 브랜드 anchor 색상, tinted surface token, typography/spacing token, metric/supporting-info/row/status/guidance chrome 계약, `Brand`별 아이콘과 표시 label 매핑 |
| `data:settings` | `DefaultSettingsRepositoryTest` | 영속 설정 저장소 연결 |
| `data:station` | `DefaultStationRepositoryTest`, `StationCachePolicyTest`, `data:station/StationRetryPolicyTest`, `StationRemoteDataSourceTest`, `WatchlistRepositoryTest` | 캐시/히스토리/watchlist 조합, stale/retention 규칙, 성공 refresh 이후 pruning과 `SearchRefreshed` event, `Timeout`/`Network` retry once 정책과 retry event, 원격 오류 매핑 |
| `feature:station-list` | `feature:station-list/LocationStateMachineTest`, `feature:station-list/StationSearchOrchestratorTest`, `StationListViewModelTest`, `StationListScreenTest`, `StationListBannerModelTest`, `StationListItemUiModelTest`, `GpsAvailabilityMonitorTest` | 위치 상태 전이, query/cache/failure orchestration, extraction 이후 UI state composition/effect/action dispatch, stale/approximate guidance, 주소 컨텍스트 표시, 가격 우선 카드, 긴 역명/가격/유종 clipping 방지, route lifecycle 기반 availability 관찰과 권한/GPS recovery |
| `feature:settings` | `SettingsViewModelTest`, `SettingsScreenTest`, `SettingsSectionTest` | 설정 요약/상세 화면 계약, 그룹/row hierarchy, selected check affordance, 긴 현재값 row clipping 방지 |
| `feature:watchlist` | `WatchlistViewModelTest`, `WatchlistScreenTest`, `WatchlistItemUiModelTest` | 북마크 비교 화면 상태와 표시, `CompareViewed` event, brand label 보존, metric column alignment, 긴 저장 항목/큰 가격 clipping 방지 |
| `app` | `AppStartupGraphTest`, `AppStartupRunnerTest`, `ExternalMapLauncherTest`, `SplashThemeResourceTest`, `AppIconResourceTest`, `NetworkSecurityConfigResourceTest`, `BackupPolicyResourceTest`, `ProdSecretsStartupHookTest` | startup hook 바인딩, prod key fail-fast, 앱 리소스, Opinet-only cleartext config, Android backup 비활성화, 외부 지도 인텐트 |
| `demo` 전용 앱 경로 | `DemoSeedStartupHookTest`, `DemoSeedAssetLoaderTest`, `DemoLocationHookIntegrationTest`, `StationPortfolioFlowTest` | seed 적재, 고정 위치, 실제 북마크 플로우 |
| `benchmark` | `StationListBenchmark`, `BaselineProfileGenerator` | cold start, watchlist 이동, baseline profile |
| `tools:demo-seed` | `DemoSeedGeneratorTest` | seed 생성기와 질의 매트릭스 |

## flavor별 관점

### `demo`

`demo`는 가장 넓게 검증합니다.

- startup hook이 DB와 선호를 고정 상태로 리셋하는지
- 고정 위치 override가 실제 런타임에 들어오는지
- 목록 -> 북마크 저장 -> watchlist 이동 플로우가 실제 기기 테스트에서 동작하는지
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
- `AddressLabelFormatter`
  Android 지오코더는 `대한민국`, `KR`, 건물 동, 도로명 조각을 섞어 줄 수 있습니다. 이 값이 목록 상단에 그대로 노출되지 않도록 행정동 라벨 회귀 테스트로 막습니다.
- `BrandLabels`
  station list, watchlist, settings가 같은 브랜드 표시 label을 공유하므로 RTX 같은 canonical label 회귀를 한 곳에서 막습니다.
- `DemoSeedStartupHook`
  demo 시작 상태가 흔들리면 문서, 스크린샷, benchmark, UI 테스트가 함께 흔들립니다.
- `ExternalMapLauncher`
  사용자 설정의 지도 앱 선택이 실제 외부 인텐트와 맞아야 합니다.

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
- benchmark는 demo 경로를 기준으로 돈다

새 문서 설명을 추가할 때는 "이 설명이 어떤 테스트 파일에 기대고 있는가"까지 같이 점검하는 편이 안전합니다.
