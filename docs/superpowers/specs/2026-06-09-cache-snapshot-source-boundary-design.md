# Cache Snapshot And Remote Source Boundary Design

> 작성일: 2026-06-09
> 기준 커밋: `03b54d2`
> 범위: `StationSearchResult.hasCachedSnapshot` 명시화, `DefaultStationRepository`의 demo/prod remote source 선택 책임 분리
> 사용자 플로우 영향: 없음. 목록, 빈 결과, stale, refresh 실패, demo/prod 경로의 사용자 대면 동작은 유지한다.
> 짝 구현 문서: `docs/superpowers/plans/2026-06-09-cache-snapshot-source-boundary.md`

## 목표

인프런 조영호 "오브젝트" 강의의 책임 주도 설계, 변경 보호, OCP/DIP 관점으로 GasStation의 검색/캐시 경계를 다듬는다. 핵심은 두 가지다.

1. `StationSearchResult` 생성자가 캐시 스냅샷 존재 여부를 암묵적으로 추론하지 못하게 해서, "성공한 빈 결과"와 "캐시 없음"의 도메인 의미를 더 강하게 보존한다.
2. `DefaultStationRepository`가 demo seed source와 prod network source 중 무엇을 쓸지 직접 선택하지 않게 해서, repository가 persistence/cache/watchlist 조합 책임에 집중하게 만든다.

## 배경: 실제 코드에서 확인한 사실

### 현재 캐시 의미

문서와 코드는 이미 `hasCachedSnapshot`을 `fetchedAt != null`보다 우선하는 의미로 다룬다.

- `docs/state-model.md`는 `hasCachedSnapshot`을 "현재 쿼리 버킷에 스냅샷 마커가 존재하는지 여부"로 설명한다.
- `docs/offline-strategy.md`는 `station_cache_snapshot` 마커가 "성공했지만 0건"과 "아직 캐시 없음"을 구분한다고 설명한다.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`는 `result.hasCachedSnapshot`만 보고 `CachedSnapshotState.Present/Absent`를 정한다.
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`는 snapshot marker가 없으면 `hasCachedSnapshot = false`, marker가 있지만 rows가 0건이면 `hasCachedSnapshot = true`를 명시한다.

하지만 `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`의 현재 생성자는 다음 기본값을 가진다.

```kotlin
data class StationSearchResult(
    val stations: List<StationListEntry>,
    val freshness: StationFreshness,
    val fetchedAt: Instant?,
    val hasCachedSnapshot: Boolean = fetchedAt != null,
)
```

이 기본값은 현재 production path에서는 대부분 명시값으로 덮이지만, 새 테스트나 새 adapter가 `hasCachedSnapshot`을 빼먹으면 snapshot marker 의미가 다시 `fetchedAt`로 후퇴한다. 이 프로젝트의 오프라인 전략에서는 이 암묵 추론이 위험하다.

### 현재 remote source 선택

`DefaultStationRepository`는 다음 두 source를 동시에 주입받는다.

```kotlin
private val remoteDataSource: StationRemoteDataSource,
private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
```

그리고 refresh 중에 다음 private helper로 source를 고른다.

```kotlin
private suspend fun fetchRemoteStations(query: StationQuery): RemoteStationFetchResult = if (seedRemoteDataSource.isPresent) {
    seedRemoteDataSource.get().fetchStations(query)
} else {
    remoteDataSource.fetchStations(query)
}
```

이 구조는 동작은 맞지만 책임이 섞인다.

- `DefaultStationRepository`: Room snapshot, price history, watchlist, cache pruning, retry, logging 조합을 소유한다.
- `SeedStationRemoteDataSource`: demo flavor가 optional binding으로 제공하는 재현 가능한 remote source다.
- `DefaultStationRemoteDataSource`: prod/direct/proxy network source를 data 계층 result로 변환한다.
- "seed가 있으면 seed, 없으면 prod"는 repository persistence 정책이 아니라 runtime source 선택 정책이다.

현재 `DefaultStationRepositoryTest`의 `refreshNearbyStations prefers seed data source when available`는 이 선택 책임이 repository에 있음을 고정한다. 구현 후에는 이 테스트를 repository 테스트에서 제거하고, 새 source selector 테스트로 이동해야 한다.

## 문제 정의

### 문제 1. 캐시 스냅샷 의미가 생성자 기본값으로 약해진다

`hasCachedSnapshot`은 단순 파생값이 아니다. `fetchedAt == null`이면 스냅샷이 없다는 뜻이지만, 반대로 `fetchedAt != null`이 항상 "스냅샷 마커에서 왔다"는 계약을 모든 생성자가 보장하는 것은 아니다. 특히 테스트 fake나 향후 adapter가 임의 값을 만들 때 기본값이 있으면 생성 시점에 이 의미를 생각하지 않게 된다.

설계 원칙 관점:

- **캡슐화:** snapshot marker의 존재 의미는 명시 필드로 보존되어야 한다.
- **CQS/TDA:** 소비자가 `fetchedAt`을 묻고 캐시 존재를 추론하지 않도록, 생성자부터 명시 의도를 요구한다.
- **변경 보호:** snapshot marker schema나 offline policy가 바뀌어도 생성 call site가 "캐시 있음/없음" 결정을 드러내고 있어야 한다.

### 문제 2. repository가 source 선택과 persistence 조합을 함께 책임진다

`DefaultStationRepository`가 `Optional<SeedStationRemoteDataSource>`를 알면 demo/prod source 구성이 repository constructor와 테스트 helper까지 새어 들어간다. source 추가나 fallback 정책 변경이 생기면 repository가 수정 대상이 된다.

설계 원칙 관점:

- **SRP:** repository는 저장소 조합 책임, source selector는 remote fetch source 선택 책임을 갖는다.
- **OCP:** source 선택 정책 변경이 repository refresh/persistence 코드를 건드리지 않게 한다.
- **DIP:** repository는 단일 `StationRemoteDataSource` 계약만 의존한다.

## 설계

### Track A. `StationSearchResult.hasCachedSnapshot` 명시화

`StationSearchResult`에서 기본값을 제거한다.

```kotlin
data class StationSearchResult(
    val stations: List<StationListEntry>,
    val freshness: StationFreshness,
    val fetchedAt: Instant?,
    val hasCachedSnapshot: Boolean,
)
```

모든 call site는 다음 의미로 명시한다.

| 생성 위치 | `hasCachedSnapshot` 값 | 이유 |
| --- | --- | --- |
| `DefaultStationRepository.emptySearchResult()` | `false` | snapshot marker가 없음 |
| `DefaultStationRepository.snapshotOnlyResult()` | `true` | marker는 있고 rows가 0건 |
| `StationSearchResultAssembler.toSearchResult()` | `true` | marker와 rows가 있음 |
| `StationSearchOrchestrator.emptySearchResult()` | `false` | active query 없음 또는 repository result 없음 |
| feature tests fake result with `fetchedAt = null` | 보통 `false` | 캐시 없는 기본 fake |
| feature tests fake result with `fetchedAt != null` | 보통 `true` | 캐시 있는 fake |

기존 production code는 이미 거의 명시하고 있으므로 핵심 작업은 domain constructor와 test fake 보강이다.

### Track B. `FlavorAwareStationRemoteDataSource` 추가

`data:station`에 새 구현을 둔다.

```kotlin
class FlavorAwareStationRemoteDataSource(
    private val prodRemoteDataSource: StationRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult =
        seedRemoteDataSource.orElse(null)?.fetchStations(query)
            ?: prodRemoteDataSource.fetchStations(query)
}
```

이름은 "flavor-aware"로 둔다. 선택 기준이 현재 flavor Hilt graph에서 optional seed binding이 존재하는지이기 때문이다. 생성자 타입은 `StationRemoteDataSource`로 둬 테스트가 단순 fake를 넣을 수 있게 하고, Hilt provider는 concrete prod 구현인 `DefaultStationRemoteDataSource`를 넘긴다.

`StationDataModule`은 `StationRemoteDataSource` binding을 `@Binds`에서 `@Provides`로 바꾼다.

```kotlin
@Provides
@Singleton
fun provideStationRemoteDataSource(
    prodRemoteDataSource: DefaultStationRemoteDataSource,
    seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
): StationRemoteDataSource = FlavorAwareStationRemoteDataSource(
    prodRemoteDataSource = prodRemoteDataSource,
    seedRemoteDataSource = seedRemoteDataSource,
)
```

이후 `DefaultStationRepository` 생성자는 `StationRemoteDataSource` 하나만 받는다. `fetchRemoteStations()` helper와 `Optional` import는 제거한다.

## 비목표

- demo/prod 동작 변경. demo는 seed source를 계속 우선하고, prod는 seed binding이 없으므로 network source를 사용한다.
- retry 정책 변경. `StationRetryPolicy`는 그대로 source wrapper 바깥, repository refresh 안쪽에서 적용된다.
- cache key 이동. `StationQuery.toCacheKey()`를 data 계층으로 옮기는 일은 영향 범위가 더 크고, 기존 `2026-06-06-oo-collaboration-tell-dont-ask` 문서의 캐시 키 매직넘버 정리와도 겹치므로 별도 설계가 필요하다.
- `StationListViewModel` refresh coordinator 분리. 좋은 후속 후보지만 테스트와 상태 전이가 크므로 이번 문서의 1차 구현 범위에서는 제외한다.
- public API 호환 유지. 내부 멀티모듈 앱이므로 `StationSearchResult` constructor 변경은 call site를 함께 고치는 방식으로 처리한다.

## 기대 효과

- `hasCachedSnapshot` 누락이 컴파일 단계에서 드러난다.
- repository 테스트가 seed/prod source 선택까지 검증하지 않고, persistence/cache 조합 검증에 집중한다.
- source 선택 테스트가 독립되어 demo/prod wiring 변경을 작게 검증할 수 있다.
- `DefaultStationRepository` constructor가 짧아지고, `Optional<SeedStationRemoteDataSource>`가 repository 책임 밖으로 이동한다.

## 위험과 대응

| 위험 | 대응 |
| --- | --- |
| `StationSearchResult` 기본값 제거로 feature 테스트가 대량 컴파일 실패 | 실패를 의도한 RED로 보고 모든 fake result에 명시값을 넣는다. 값 선택 기준은 `fetchedAt`이 아니라 테스트가 표현하려는 cache state다. |
| Dagger binding 충돌 | 기존 `@Binds StationRemoteDataSource`를 제거하고 companion `@Provides` 하나만 남긴다. `DefaultStationRemoteDataSource`는 concrete constructor injection으로 provider parameter에 직접 주입받는다. |
| demo seed source가 prod에서도 요구될 위험 | `SeedStationRemoteDataSourceModule`의 `@BindsOptionalOf`는 유지한다. prod graph에서는 `Optional.empty()`가 들어온다. |
| repository retry 이벤트가 source wrapper로 이동할 위험 | source wrapper는 delegate 선택만 한다. retry는 계속 `DefaultStationRepository.refreshNearbyStationsInternal()`의 `retryPolicy.withRetry`가 감싼다. |

## 검증 기준

필수:

```bash
./gradlew \
  :domain:station:test \
  :data:station:testDebugUnitTest \
  verifyModuleBoundaries
```

보강:

```bash
./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

이유:

- `:domain:station:test`: `StationSearchResult` contract surface 변경 확인.
- `:data:station:testDebugUnitTest`: source selector, repository persistence/cache/watchlist 테스트 확인.
- `verifyModuleBoundaries`: data/domain/app 경계가 새 binding 때문에 깨지지 않았는지 확인.
- app flavor tests: optional seed binding과 prod graph가 flavor별로 깨지지 않았는지 확인.
