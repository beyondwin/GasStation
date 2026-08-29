# 테스트 전략

어떤 층이 무엇을 막는지 설명한다. 명령은 `docs/verification-matrix.md`다. 확인하는 것은 `demo`와 `prod`가 계속 성립하는가다.

## 원칙

- 복잡한 조합은 저장소와 상태 collaborator 테스트로 먼저 막는다.
- `demo`는 정식 경로다. startup, seed, UI를 따로 본다.
- `prod`는 실서버 대신 빌드, 그래프, 런타임 설정이 깨지지 않는지 본다.
- 문서가 약속한 흐름은 테스트 파일 이름으로도 따라갈 수 있게 한다.
- Android library 공통 test 의존은 `gasstation.android.library`가 맡는다.
- unit test SDK는 `config/robolectric/robolectric.properties`의 API 36이다. 앱 bytecode는 JVM 17, Robolectric은 Java 21이다.
- Compose UI 테스트는 v2 환경이다. v1 import는 `verifyNoDeprecatedComposeTestApis`가 막는다.
- selector는 ASCII `testTag`, 스크린 리더 문구는 `contentDescription`.
- station-list coroutine 테스트의 `Dispatchers.Main`은 `MainDispatcherRule`이다.

## Lint와 Kotlin

Android Lint error/warning은 build를 멈춘다. production `static-analysis`와 test-source `lint-tests`는 둘 다 blocking이다. JVM 모듈(`core:model`, `core:network`, `core:observability`, `domain:*`, `tools:demo-seed`)은 Android Lint 대상이 아니다.

다섯 JVM 계약 모듈은 Kotlin ABI baseline과 `verifyPublicApiBoundaries`로 Android/Compose/Room/Retrofit 타입 누수를 막는다. Roborazzi 테스트는 일반 unit-test task에서 빠진다. 명령은 [검증 매트릭스](verification-matrix.md#kotlin-및-convention-정책)다.

## 층

| 계층 | 대표 테스트 | 막는 것 |
| --- | --- | --- |
| `core:model` | `ValueObjectInvariantTest`, `BrandFilterTest` | 값 객체, 거리, `ALTEUL` 그룹, ETC-last |
| `domain:*` | `StationQueryCacheKeyTest`, `AddressLabelNormalizerTest`, `LocationUseCasesTest` | 순수 규칙, 주소 라벨, use case |
| `core:database` | `StationCacheDaoTest`, `GasStationDatabaseMigrationTest` | DAO, 원자 스냅샷, schema 1–5. device migration은 기기가 있을 때만 주장 |
| `core:network` | `NetworkStationFetcherTest`, `ProxyStationFetcherTest` | 좌표 변환, direct/proxy 실패 분류, proxy URL |
| `core:location` | `DefaultLocationRepositoryTest`, `GeocoderAsyncLookupTest` | 위치 조회, 지오코더, demo override |
| `core:datastore` | `UserPreferencesSerializerTest` | 설정 DTO |
| `core:designsystem` | token/chrome/BrandIcon, Roborazzi | Urban Signal 토큰, 브랜드 drawable |
| `data:settings` | `DefaultSettingsRepositoryTest` | domain 매핑, `KAKAO_NAVI` → `KAKAO_MAP` |
| `data:station` | `DefaultStationRepositoryTest`, `StationRetryPolicyTest`, `LatestWatchIntentGateTest` | 캐시 조합, 재시도, 최신 watch 의도 |
| `feature:station-list` | owner tests + 다섯 integration suite, Roborazzi | 위치·관찰·refresh·FIFO·투영 |
| `feature:settings` | `SettingsViewModelTest`, Roborazzi | 설정 상태, commit 뒤 복귀 |
| `feature:watchlist` | `WatchlistViewModelTest`, Roborazzi | 가격 없음 유지, 5행, 200% 글꼴 |
| `app` | startup, backup, cleartext, 외부 지도, splash | flavor 조립, 권한, 리소스 |
| `demo` 경로 | `DemoPermissionFlowTest`, `StationPortfolioFlowTest` | seed, 권한 CTA, 관심 플로우 |
| `benchmark` | `StationListBenchmark` | startup, scroll, refresh, 관심 진입 |
| `tools:demo-seed` | `DemoSeedGeneratorTest` | seed 생성기 |

## 기기 증거

기기 테스트는 host 계약을 대체하지 않는다. API 24/28/36 매트릭스, artifact, 격리는 [기기 검증](runbooks/device-verification.md)이다. 지원 호스트 attempt가 없으면 `NOT RUN`이다.

### `demo`

가장 넓게 본다. seed 리셋, 권한 허용 뒤 고정 좌표, 목록 → 관심 저장 → `watchlist-card`, 설정 유종이 관심 query에 반영되는지, 지도 provider가 Nearby handoff까지 가는지.

### `prod`

실통신은 자동화하지 않는다. 대신 secret fail-fast, `prodDebug` assemble, Opinet-only cleartext, Android backup 꺼짐을 본다.

## 회귀가 큰 곳

- `DefaultStationRepository` — 마커, 행, 히스토리, pruning, 관심 fallback
- `StationRetryPolicy` — 한 번 재시도, cancellation·superseded

<!-- station-data-policy-ref: retry -->[오프라인 전략의 구조화된 `retry` 계약](offline-strategy.md#기계-판독-정책-계약)

- `StationBucketSnapshotObserver` / `StationFreshnessTicker` / `LatestRefreshGate` — 원자 스냅샷, 시간 경과 stale, 늦은 persistence

<!-- station-data-policy-ref: freshness -->[구조화된 `freshness` 계약](offline-strategy.md#기계-판독-정책-계약)

- station-list owner tests — generation, 관찰 복구, refresh identity, FIFO, 순수 투영
- 다섯 ViewModel integration suite — preferences, command, watch, location, refresh
- `LatestWatchIntentGateTest` / `WatchedStationDaoTest` — 마지막 의도, `INSERT IGNORE`, 안정적 순서

이 상태 계약의 증거는 host coroutine, Room/Robolectric, app graph다. 연결된 기기 실행을 이 범위의 증거로 주장하지 않는다. 명령은 [검증 매트릭스](verification-matrix.md#station-list-상태-동시성-집중-회귀)다.

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)

주소 정규화는 `domain:location`, Android 변환은 `core:location`. 브랜드 아이콘은 `BrandIconTest`와 Roborazzi. 외부 지도는 `ExternalMapLauncherTest`. first usable content는 `StationListFirstContentPolicy`.

## 커버리지

`coverageXmlReport`는 JaCoCo 0.8.15로 18개 활성 모듈 보고서를 만든다. `benchmark`는 제외한다. `verifyCoverageReport`는 HEAD, 모듈 floor, baseline 대비 하락을 정수로 판정한다. 정책은 `config/quality/coverage-policy.json`, baseline은 `config/quality/coverage-baseline.json`이다.

```bash
./gradlew coverageXmlReport verifyCoverageReport \
  -Pgasstation.coverageSourceCommit="$(git rev-parse HEAD)" \
  -Pgasstation.coverageEvent=local \
  --warning-mode fail
```

결과는 `build/reports/coverage/verification-summary.json`이다.

## Mutation

PIT는 JVM-only `domain:station`, `domain:location`, `domain:settings`다. 실행기는 `scripts/quality/run_pitest.sh`만 쓴다. plugin `pitest` task를 직접 부르지 않는다.

| 모듈 | KILLED | SURVIVED | NO_COVERAGE | 점수 |
| --- | ---: | ---: | ---: | --- |
| station | 36/66 | 3 | 27 | blocking floor 45 |
| location | 53/68 | 12 | 3 | blocking floor 75 |
| settings | 8/13 | 5 | 0 | score report-only |

점수는 `killed * 100 >= floor * total`이다. settings는 점수 floor만 없고 malformed/status 위반은 막는다. CI mutation은 `v*` tag의 release 선행 조건이다.

## 약하게 보는 것

- 실제 Opinet 서버에 의존하는 e2e
- 제품에 없는 flavor
- 과거 앱 버전 호환

## 문서와 테스트

문서에 아래가 있으면 테스트가 간접적으로라도 막아야 한다.

- demo는 같은 시작 상태를 준다
- 주소는 행정동까지만
- stale 결과를 유지한다
- 캐시 있음은 `hasCachedSnapshot`
- 최신 refresh만 저장한다
- 관심은 가격이 없어도 저장 identity를 유지한다
- 설정은 첫 값 전에 default를 화면에 쓰지 않는다
- 권한 dialog는 CTA에서만 연다
- 외부 지도 실패는 사용자에게 알린다

## Build-input integrity와 reproducibility ownership

SHA-256은 검토한 bytes와의 integrity다. 같은 host에서 두 clean tree의 unsigned prod APK size/hash가 같을 때만 재현성 PASS다. 명령은 [검증 매트릭스](verification-matrix.md), 운영은 [Build Input Provenance](runbooks/build-input-provenance.md), 시간은 [Build Velocity](build-velocity.md)다.

convention suite는 52 test-class / 90 method, 50분 blocking timeout이다. nested TestKit timeout은 15분이다.
