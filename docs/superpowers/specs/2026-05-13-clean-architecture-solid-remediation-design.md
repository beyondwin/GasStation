# GasStation Clean Architecture / SOLID Remediation 설계 스펙

> 작성일: 2026-05-13
> 기준 브랜치: `main`
> 기준 커밋: `0b1de0e`
> 본 문서는 현재 코드 기준의 clean architecture, SOLID, 파일 크기, 중복 책임 분석 결과와 개선 요구사항을 고정한다. 구현 순서와 파일별 작업은 [`docs/superpowers/plans/2026-05-13-clean-architecture-solid-remediation.md`](../plans/2026-05-13-clean-architecture-solid-remediation.md)가 소유한다.

---

## 1. 결론 요약

현재 GasStation은 전체적으로 멀티모듈 clean architecture를 잘 지키고 있다. `feature:*`가 Room/Retrofit/DataStore를 직접 알지 않고, `domain:*`가 Android/Compose/Room 타입을 노출하지 않으며, `data:*`가 화면 상태를 만들지 않는 핵심 경계는 유지된다.

다만 아래 항목은 현재 코드 기준으로 개선 가치가 명확하다.

| 우선순위 | 항목 | 판정 | 핵심 근거 |
| --- | --- | --- | --- |
| P1 | `core:location -> domain:station` 의존 | clean architecture / DIP 위반 | `core/location/build.gradle.kts:13`, `AndroidAddressResolver.kt:10`이 위치 인프라에서 station domain의 `CrashReporter`를 참조한다. |
| P1 | 주소 라벨 정규화 중복 | SRP / DRY 위반 | `core:location/AddressLabelFormatter.kt`와 `feature:station-list/StationListScreen.kt:759-835`가 같은 행정동 파싱 로직을 가진다. |
| P1 | 거리 계산과 브랜드 코드 파싱 중복 | DRY / 변경 안전성 결함 | `DefaultStationRepository.kt:336-355`, `StationMappers.kt:80-94`가 같은 fallback/거리 공식을 복제한다. |
| P1 | `data:station` mapper의 죽은 remote DTO 매핑 | 책임 경계 혼탁 | `StationMappers.kt:21-47`, `72-78`은 현재 호출되지 않고 `core:network`가 이미 같은 일을 한다. |
| P2 | `StationListScreen.kt` 914라인 | SRP / 변경 비용 | 한 파일이 scaffold, body state, card, 상태 화면, query summary, 주소 파싱, label mapping, animation을 모두 가진다. |
| P2 | 테스트 파일 비대화 | 유지보수 비용 | `StationListViewModelTest.kt` 1260라인, `StationListScreenTest.kt` 879라인, `DefaultStationRepositoryTest.kt` 783라인. |
| P3 | `DefaultStationRepository.kt` 355라인 | 관찰 필요 | 저장소 orchestration 자체는 모듈 계약과 맞지만 read-model assembly/helper가 같은 파일에 몰려 있다. |

## 2. 분석 범위와 기준

### 읽은 단일 출처

- `AGENTS.md`
- `settings.gradle.kts`
- `docs/agent-workflow.md`
- `docs/module-contracts.md`
- `docs/architecture.md`
- `docs/state-model.md`
- `docs/offline-strategy.md`
- `docs/verification-matrix.md`
- `docs/project-reading-guide.md`
- `docs/history/deep-analysis-report.md`
- `docs/history/improvement-analysis.md`
- `docs/superpowers/specs/2026-05-11-production-baseline-design.md`

### 실행한 정적 확인

```bash
git status --short
rg --files -g '*.kt' -g '*.kts' | xargs wc -l | sort -nr | head -40
rg --line-number --no-heading 'implementation\(project|api\(project' --glob 'build.gradle.kts'
rg --line-number --no-heading 'com\.gasstation\.core\.(database|network|datastore|location)|androidx\.room|retrofit2|androidx\.datastore' feature domain data core/model/src core/designsystem/src
rg --line-number --no-heading 'android\.|androidx\.compose|androidx\.room|retrofit2|datastore|DataStore|Room' domain core/model/src data/station/src/main data/settings/src/main
rg --line-number --no-heading 'toDongLevelAddressLabel|AddressLabelFormatter|distanceBetween|toBrand|toRemoteStation|toFuelProductCode' app core data domain feature tools --glob '*.kt'
./gradlew :data:station:compileDebugKotlin
```

결과: compile은 성공했다. 즉 이번 문서의 P1 항목은 "현재 빌드가 깨지는 결함"이 아니라 "아키텍처 경계, 책임 분리, 중복 로직 때문에 다음 변경을 위험하게 만드는 결함"이다.

## 3. 유지해야 할 강점

- 활성 모듈 판단이 `settings.gradle.kts` 기준으로 명확하다. 현재 활성 모듈은 17개다.
- `feature:*`는 `core:database`, `core:network`, `core:datastore`, `core:location` 구현 타입을 직접 import하지 않는다.
- `domain:*`와 `core:model`의 main source에는 Android/Compose/Room/Retrofit/DataStore 타입이 노출되지 않는다.
- `StationListViewModel`은 이미 `LocationStateMachine`, `StationSearchOrchestrator`, `StationListBannerModel`, `StationListItemUiModel`로 주요 책임을 일부 분리했다.
- `demo`와 `prod`가 둘 다 정식 경로라는 제품 불변식은 코드와 문서에 반영되어 있다.
- `station_cache_snapshot` 기반의 빈 결과/캐시 없음 구분, retry policy, watchlist fallback은 모듈 문서와 테스트가 보호한다.

이 스펙의 목적은 위 구조를 갈아엎는 것이 아니라, 남아 있는 경계 누수와 큰 파일을 작게 잘라 현재 구조의 장점을 더 선명하게 만드는 것이다.

## 4. 문제 상세

### P1-1. `core:location`이 `domain:station`을 참조한다

**현재 코드**

- `core/location/build.gradle.kts:13`

```kotlin
implementation(project(":domain:station"))
```

- `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt:10`

```kotlin
import com.gasstation.domain.station.CrashReporter
```

**왜 문제인가**

`core:location`은 `domain:location` 구현체다. 위치 인프라가 station domain의 crash reporting 계약을 알면 "위치 기능이 station 기능의 하위 개념"처럼 보인다. 이는 `docs/module-contracts.md`의 `core:location` 직접 의존 설명과도 어긋난다. Clean Architecture 관점에서는 cross-cutting output port가 station domain에 잘못 놓여 DIP가 흐려진 상태다.

**요구사항**

- `CrashReporter`는 station domain에서 제거하고 중립 모듈로 이동한다.
- 새 소유자는 `core:observability`로 한다.
- `core:location`, `data:station`, `app`은 `core:observability`에 의존한다.
- `domain:station`은 `CrashReporter`를 더 이상 소유하지 않는다.
- `core:location`의 `domain:station` Gradle dependency는 제거한다.

**완료 기준**

```bash
rg -n 'domain\.station\.CrashReporter|project\(":domain:station"\)' core/location
```

위 명령이 아무 것도 출력하지 않는다.

### P1-2. 주소 행정동 정규화가 UI와 core에 중복되어 있다

**현재 코드**

- `core/location/src/main/kotlin/com/gasstation/core/location/AddressLabelFormatter.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt:759-835`

**왜 문제인가**

주소 정규화는 화면 배치가 아니라 "위치 결과 문자열을 어떤 의미의 표시 주소로 볼 것인가"에 가까운 정책이다. 현재는 `core:location`이 이미 정규화한 뒤에도 station-list UI가 같은 토큰 파싱을 다시 수행한다. 이 방식은 한쪽만 수정될 때 회귀를 만든다.

**요구사항**

- 순수 문자열 정규화 함수는 `domain:location`으로 올린다.
- `core:location`은 Android `Address`를 문자열 후보로 변환한 뒤 `domain:location` 정규화 함수를 호출한다.
- `feature:station-list`는 주소 토큰 파싱을 소유하지 않는다.
- `LocationStateMachine`은 `LocationAddressLookupResult.Success`를 상태에 저장하기 전에 한 번 더 정규화해 테스트/가짜 repository가 raw label을 줘도 같은 계약을 유지한다.

**완료 기준**

```bash
rg -n 'toDongLevelAddressLabel|isAdministrativeDongPart|joinSplitAdministrativeTokens|findFallbackRegionIndexBefore' feature/station-list/src/main
```

위 명령이 아무 것도 출력하지 않는다.

### P1-3. 거리 계산과 브랜드 코드 fallback이 `data:station` 안에 복제되어 있다

**현재 코드**

- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt:336-355`
- `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt:80-94`

**왜 문제인가**

거리 계산은 `Coordinates`와 `DistanceMeters`의 순수 값 객체 행동이다. station cache row를 domain station으로 바꿀 때와 watchlist fallback station을 만들 때 같은 공식을 복제하면 소수점/반올림/지구 반경 변경 같은 작은 수정이 한쪽에만 들어갈 수 있다.

브랜드 코드 fallback도 `Brand` vocabulary의 책임으로 볼 수 있다. 현재는 data mapper 두 곳에서 `Brand.entries.firstOrNull { it.name == this } ?: Brand.ETC`가 반복된다.

**요구사항**

- `core:model`에 `Coordinates.distanceTo(destination: Coordinates): DistanceMeters`를 추가한다.
- `core:model.Brand`에 `fromCode(code: String): Brand` companion factory를 추가한다.
- `data:station`은 거리 공식과 brand fallback을 직접 소유하지 않는다.
- 기존 거리 표시/정렬/watchlist 결과는 변하지 않는다.

### P1-4. `StationMappers.kt`에 더 이상 쓰지 않는 remote DTO mapping이 남아 있다

**현재 코드**

- `StationMappers.kt:21-47`의 `OpinetStationDto.toRemoteStation()`
- `StationMappers.kt:41-47`의 `rawCoordinatesToWgs84()`
- `StationMappers.kt:72-78`의 `FuelType.toFuelProductCode()`

**왜 문제인가**

현재 네트워크 DTO 정규화는 `core:network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationMappers.kt`와 `NetworkStationFetcher`가 소유한다. `data:station`의 같은 함수들은 호출되지 않는다. 특히 `StationMappers.kt`가 `OpinetStationDto`와 `LocalKoreanCoordinateTransform`을 import하면 data 계층이 remote DTO 세부를 아직 소유하는 것처럼 보인다.

**요구사항**

- `StationMappers.kt`에서 호출되지 않는 remote DTO mapping을 제거한다.
- `data:station`의 mapper는 `RemoteStation -> StationCacheEntity`, `StationCacheEntity -> Station`만 남긴다.
- `core:network`의 product code / coordinate conversion 테스트가 이 책임을 계속 보호한다.

### P2-1. `StationListScreen.kt`가 914라인으로 UI 책임이 한 파일에 집중되어 있다

**현재 책임 혼합**

- top-level screen scaffold
- sort toggle top bar
- pull-to-refresh results pane
- query context summary
- station card
- price delta indicator
- fuel chip
- watch toggle
- permission/GPS/loading/failure/empty 상태 화면
- body state 분기
- 주소 행정동 파싱
- resource label mapping
- animation transform

**왜 문제인가**

Compose 파일이 길다는 것 자체가 결함은 아니다. 하지만 이 파일은 카드/상태/주소/라벨/animation이 섞여 있어 가격 우선 card UI를 고칠 때 주소 파싱이나 failure state까지 같은 context에 들어온다. SRP 관점에서 "station list 화면의 여러 leaf component와 정책 helper"가 한 변경 단위가 된 상태다.

**요구사항**

- 기능 변경 없이 파일을 책임별로 분리한다.
- 분리 후 `StationListScreen.kt`는 screen scaffold와 top-level wiring 중심으로 300라인 이하를 목표로 한다.
- station card 관련 코드는 `StationListCards.kt`가 소유한다.
- 상태 화면 관련 코드는 `StationListStates.kt`가 소유한다.
- query context와 label mapping은 `StationListQuerySummary.kt`가 소유한다.
- body state 계산은 `StationListBodyState.kt`가 소유한다.
- test tag, semantics, price-first hierarchy는 유지한다.

### P2-2. 테스트 파일이 너무 크고 fixture가 테스트 본문과 섞여 있다

**현재 수치**

| 파일 | 라인 수 | 특징 |
| --- | ---: | --- |
| `feature/station-list/.../StationListViewModelTest.kt` | 1260 | 27개 test와 fake repository/location/logger가 같은 파일에 있음 |
| `feature/station-list/.../StationListScreenTest.kt` | 879 | 27개 Compose test가 한 파일에 있음 |
| `data/station/.../DefaultStationRepositoryTest.kt` | 783 | repository tests와 fake remote/cache/event/crash reporter가 섞임 |

**요구사항**

- test double은 `*TestFixtures.kt` 또는 `*Doubles.kt`로 이동한다.
- test class는 behavior 축으로 나눈다.
- 테스트 의미와 assertion은 유지한다.
- 테스트 분리 작업은 production refactor 이후에 수행한다. 먼저 경계와 중복 로직을 정리해 test churn을 줄인다.

### P3-1. `DefaultStationRepository.kt`는 당장 분해보다 helper 추출이 적절하다

`DefaultStationRepository`는 355라인이지만, 이 저장소가 cache observation, refresh persistence, watchlist read model orchestration을 소유하는 것은 `docs/module-contracts.md`와 맞는다. 따라서 저장소 interface를 나누거나 새 repository를 만들 필요는 없다.

다만 아래 helper는 repository orchestration과 구분된다.

- search result assembly
- watchlist summary fallback assembly
- history row grouping/filtering
- sorting/fallback conversion

**요구사항**

- P1 정리 후에도 repository가 300라인 이상이면 data-local assembler 파일로 helper를 옮긴다.
- 새 public API를 만들지 않는다.
- `DefaultStationRepository`는 DAO/remote/retry/event orchestration을 읽기 쉽게 남긴다.

## 5. SOLID 관점 평가

| 원칙 | 현재 평가 | 조치 |
| --- | --- | --- |
| SRP | `StationListScreen.kt`와 `DefaultStationRepository.kt` 일부 helper가 책임을 많이 가진다. | screen split, data-local assembler/helper 추출 |
| OCP | enum 기반 vocabulary 자체는 작고 안정적이다. 큰 위반은 없다. | `Brand.fromCode()`로 fallback 확장 지점을 단일화 |
| LSP | 상속 계층이 거의 없고 sealed/value object 중심이라 특이 위반 없음. | 조치 없음 |
| ISP | `CrashReporter`의 `log()`는 현재 사용 빈도가 낮지만 interface 자체가 작다. | 이번 pass에서는 이동만 하고 interface 축소는 보류 |
| DIP | `core:location`이 `domain:station.CrashReporter`를 참조해 cross-domain port 소유가 잘못됨. | `core:observability`로 이동 |

## 6. 비목표

- 새로운 사용자 기능 추가
- backend proxy 도입
- 앱 UI 재디자인
- 다크 모드 semantic color migration
- `StationRepository` interface 분리
- `StationListViewModel` reducer 추가 추출
- `CrashReporter` 실제 Firebase Crashlytics SDK 통합
- `Optional<SeedStationRemoteDataSource>` nullable 전환

## 7. 검증 전략

각 phase는 아래처럼 owner module부터 좁게 검증한다.

```bash
./gradlew :core:observability:test
./gradlew :domain:location:test :core:location:testDebugUnitTest :feature:station-list:testDebugUnitTest
./gradlew :core:model:test :data:station:testDebugUnitTest
./gradlew :feature:station-list:testDebugUnitTest verifyRoborazziDebug
./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :app:assembleDemoDebug :app:assembleProdDebug
```

문서 갱신 후에는 아래를 추가한다.

```bash
git diff --check -- docs/superpowers/specs/2026-05-13-clean-architecture-solid-remediation-design.md docs/superpowers/plans/2026-05-13-clean-architecture-solid-remediation.md docs/architecture.md docs/module-contracts.md docs/project-reading-guide.md docs/state-model.md docs/verification-matrix.md
```

## 8. 성공 기준

- `core:location`은 `domain:station`에 의존하지 않는다.
- `CrashReporter`의 import 경로는 `com.gasstation.core.observability.CrashReporter` 하나로 통일된다.
- 주소 행정동 정규화 로직은 `domain:location` 한 곳에만 존재한다.
- 거리 계산 공식은 `core:model` 한 곳에만 존재한다.
- brand code fallback은 `Brand.fromCode()` 하나로 통일된다.
- `StationMappers.kt`는 Opinet DTO와 coordinate transform을 직접 알지 않는다.
- `StationListScreen.kt`는 300라인 이하 또는 그에 근접한 top-level screen 파일로 줄어든다.
- 가장 큰 production Kotlin 파일이 450라인 미만이다.
- 테스트 fixture가 큰 테스트 파일 본문에서 분리되어, 실패 시 원인 탐색 비용이 줄어든다.
