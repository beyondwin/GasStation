# 오프라인 전략

이 문서는 GasStation의 cache, stale, refresh 실패, watchlist fallback 의미를 설명하는 단일 출처입니다. 오프라인 전략은 "마지막 성공 스냅샷을 버리지 않고, 실패와 빈 결과를 구분한 채 계속 보여준다"로 요약할 수 있습니다. 핵심 구현은 `data:station/DefaultStationRepository.kt`와 `core:database` 스키마입니다.

## 핵심 원칙

| 질문 | 현재 답 |
| --- | --- |
| 네트워크가 실패하면 목록을 비우나 | 아니요. 기존 스냅샷을 유지합니다. |
| 결과가 0건이면 실패로 간주하나 | 아니요. 성공한 빈 결과도 별도 스냅샷 마커로 남깁니다. |
| stale 기준은 무엇인가 | `StationCachePolicy`의 5분 |
| 오래된 캐시는 언제 지우나 | refresh 성공 후 `StationCachePolicy.retainFor` 기본 7일보다 오래된 스냅샷/행을 정리합니다. |
| 가격 변화는 어떻게 계산하나 | `station_price_history` 최신 이력으로 계산 |
| watchlist는 현재 목록에 없거나 선택 유종 가격이 없으면 사라지나 | 아니요. 저장 identity를 유지하고 선택 유종 가격이 없음을 명시합니다. |

## 저장 모델

Room은 네 개의 저장 단위를 씁니다.

| 테이블 | 역할 |
| --- | --- |
| `station_cache` | 특정 쿼리 버킷에 속한 최신 주유소 행 |
| `station_cache_snapshot` | 그 버킷이 마지막으로 언제 성공적으로 갱신됐는지 나타내는 마커 |
| `station_price_history` | 주유소/유종별 최근 가격 기록 |
| `watched_station` | 사용자가 저장한 watchlist 항목 |

`station_cache_snapshot`이 따로 있다는 점이 중요합니다. 이 테이블이 없으면 "성공했지만 0건"과 "아직 캐시가 없음"을 구분하기 어렵습니다.

## 캐시 키

캐시 키는 `StationQueryCacheKey`로 표현되며 아래 값만 포함합니다.

- 위치 버킷(`latitudeBucket`, `longitudeBucket`)
- 검색 반경
- 유종

현재 버킷 크기는 250m입니다.

다음 값들은 캐시 키에 포함되지 않습니다.

- 브랜드 필터
- 정렬 순서
- 외부 지도 제공자

이 값들은 스냅샷을 다시 받아오지 않고 읽기 모델 단계에서 적용할 수 있기 때문입니다.

다만 목록 화면의 active query는 브랜드 필터와 정렬도 포함합니다. 현재 좌표가 유지된 상태에서 조건이 바뀌면 feature는 새 active query로 refresh를 다시 요청합니다. 캐시 키에는 브랜드/정렬이 없으므로 기존 스냅샷은 즉시 재사용해 필터/정렬할 수 있고, 원격 refresh가 성공하면 같은 버킷 스냅샷이 최신 데이터로 교체됩니다.

## observeNearbyStations 동작

저장소는 `station_cache`와 `station_cache_snapshot` 양쪽 invalidation을 관찰합니다. invalidation마다 마커와 `stationId ASC`로 정렬한 행을 **하나의 Room transaction** 안에서 읽어 `StationBucketSnapshot` 하나만 내보냅니다. 두 DAO `Flow`를 `combine`하는 것은 원자 관찰이 아니므로 사용하지 않습니다.

### 경우 1. 스냅샷 마커가 없음

- `stations = emptyList()`
- `freshness = Stale`
- `fetchedAt = null`
- `hasCachedSnapshot = false`

이 상태는 "아직 보여줄 캐시가 없음"을 뜻합니다.

마커가 없으면 관찰자는 행을 빈 목록으로 정규화합니다. 따라서 이전 행이 잠시 남아 있어도 no-cache 상태에 섞이지 않습니다.

### 경우 2. 스냅샷 마커는 있지만 캐시 행이 0건

- `stations = emptyList()`
- `fetchedAt = 스냅샷 마커가 기록한 마지막 성공 시각`
- `hasCachedSnapshot = true`

이 상태는 "성공적으로 조회했지만 결과가 0건"을 뜻합니다. 전면 오류와 같은 상태가 아닙니다.

### 경우 3. 캐시 행이 존재함

저장소는 여기에 watch 상태와 가격 히스토리를 결합해 `StationListEntry` 목록을 만듭니다.

- 브랜드 필터는 클라이언트에서 적용
- 정렬도 클라이언트에서 적용
- 가격 변화는 같은 유종 히스토리만 사용

이때 좌표가 유효하지 않거나 가격이 양수가 아닌 캐시 행은 DB→domain 읽기 경계에서 제외됩니다. `StationCacheEntity.toDomainStation()`이 `Coordinates.ofOrNull`/`MoneyWon.ofOrNull` 실패 시 `null`을 돌려주고, `StationSearchResultAssembler`가 이 행들을 `mapNotNull`로 건너뛰므로 불량 행은 예외 없이 목록에서 빠집니다.

## stale 판정

`StationCachePolicy`는 현재 5분 기준으로 `Fresh`와 `Stale`를 나누고, 오래된 캐시 정리 cutoff도 같은 정책 객체에서 계산합니다.

- age `<= 5분`: `Fresh`
- age `> 5분`: `Stale`
- millisecond 저장 정밀도에서 첫 stale 순간은 `5분 + 1ms`
- 보관 기본값: refresh 성공 시각 기준 7일

각 atomic snapshot은 하나의 cancellable `StationFreshnessTicker`를 소유합니다. ticker는 즉시 현재 freshness를 내보내고 fresh일 때만 남은 경계까지 기다렸다가 Stale을 한 번 내보냅니다. 새 마커가 오면 이전 ticker를 취소하고 새로 예약합니다. watch/history metadata 변경은 현재 freshness로 읽기 모델만 다시 만들며 ticker나 metadata subscription을 다시 시작하지 않습니다. 이 시간 경과 emission은 **Room mutation이나 database invalidation 없이** 일어납니다.

stale이라고 해서 결과를 버리지는 않습니다. UI는 stale 배너를 띄우고 마지막 갱신 시각을 보여줍니다.

## 새로고침 성공 시

`refreshNearbyStations()`는 정확한 `StationQueryCacheKey`별 latest-started gate를 먼저 등록합니다. 같은 key의 원격 I/O는 겹칠 수 있고 다른 key는 독립적이지만, **최신 등록 generation**만 guarded transaction에 들어갈 수 있습니다. 이미 transaction에 들어간 commit은 나중 등록이 같은 key mutex를 기다리는 동안 먼저 선형화됩니다.

승인된 generation에서만 `fetchedAt`은 guarded write 안에서, entity 생성과 transaction 직전에 잡습니다. 그 뒤 성공 시 저장소는 한 버킷 단위로 아래 작업을 합니다.

1. 기존 `station_cache` 행 삭제
2. 새 스냅샷 행 저장
3. `station_cache_snapshot` 마커 갱신
4. 새 가격을 `station_price_history`에 추가
5. 주유소/유종별 히스토리를 최신 10건으로 자르기
6. `StationCachePolicy.pruneCutoff()`보다 오래된 `station_cache` 행과 `station_cache_snapshot` 마커 정리
7. `StationEvent.SearchRefreshed` 기록

즉 스냅샷 교체는 "행 + 마커"가 함께 움직이는 구조입니다. pruning은 성공한 persistence 뒤에만 실행되므로 실패한 refresh가 기존 캐시를 지우지 않습니다. participant tombstone은 모든 generation이 끝날 때까지 남고, opaque entry identity가 replacement entry에 대한 stale ticket의 ABA 재사용을 막습니다.

## 새로고침 실패 시

원격 경계는 다음 typed failure vocabulary를 `StationRefreshException(reason)`으로 옮깁니다: `InvalidPayload`, `Timeout`, `Network`, `Http(statusCode)`, `Unknown`. direct/proxy는 같은 semantic validation을 따르고, 요청 유종과 같지 않은 proxy 행은 거부합니다. 원시 빈 목록은 `Success(emptyList())`인 성공이며, 모두 거부된 non-empty payload만 `InvalidPayload`입니다.

`StationRetryPolicy`는 `Timeout`, `Network`, HTTP 408, HTTP 429, HTTP 500–599만 500ms 뒤 한 번 재시도합니다. 다른 HTTP 4xx, `InvalidPayload`, `Unknown`, cancellation, 그리고 이미 superseded된 작업은 재시도하지 않습니다. OkHttp가 HTTP 408의 숨은 두 번째 소유자가 되지 않도록 station data retry policy가 application retry를 단독으로 소유합니다.

`StationRetryPolicy`는 실패 시 기존 스냅샷을 지우거나 바꾸지 않습니다. 두 번째 시도가 성공하면 `StationEvent.RetryAttempted(succeeded=true)`, 재시도 후 `StationRefreshException`으로 끝나면 `succeeded=false`를 남깁니다. 예기치 않은 두 번째 예외와 cancellation은 retry 이벤트로 포장하지 않고 그대로 전파합니다.

새 generation에 superseded된 성공이나 실패는 side effect를 남기지 않는 정상 종료입니다. 즉 retry, failure report, snapshot/history/prune, `SearchRefreshed`, `RetryAttempted`를 남기지 않습니다.

하지만 기존 `station_cache`와 `station_cache_snapshot`은 지우지 않습니다. 이 덕분에 UI는 실패 중에도 마지막 성공 결과를 계속 렌더링할 수 있습니다.

## watchlist fallback

watchlist는 현재 목록보다 더 방어적으로 동작합니다.

1. `watched_station`에서 저장 항목을 watched-time 순으로 읽습니다.
2. `WatchlistQuery.fuelType`과 같은 유종의 station별 최신 캐시가 있으면 그 가격/표시 정보를 우선 사용합니다. DAO SQL은 요청 유종만 대상으로 station별 한 행을 반환하고, timestamp tie는 반경과 위치 버킷 순서로 고정합니다. 캐시 행이 좌표/가격 검증을 통과하지 못해 `toDomainStation()`이 `null`이면 무효로 취급합니다.
3. 유효 캐시가 없으면 같은 stationId·선택 유종의 최신 `station_price_history` 가격을 사용하고 저장된 좌표/브랜드/이름으로 대체 모델을 만듭니다.
4. 선택 유종의 캐시와 히스토리가 모두 없더라도 저장 당시 identity·좌표·브랜드·이름을 유지하고 `price = null`인 요약을 만듭니다. 화면은 행을 제거하지 않고 `선택 유종 가격 없음`을 표시합니다.

반경, 브랜드 필터, Nearby 정렬은 watchlist query에 들어가지 않으므로 저장 항목을 숨기거나 watched-time 순서를 바꾸지 않습니다. 즉 사용자가 저장한 항목은 현재 검색 결과에서 사라지거나 선택 유종 가격이 없어도 비어 버리지 않습니다.

## demo와 prod의 의미

| 경로 | 오프라인 관점에서 하는 일 |
| --- | --- |
| `demo` | 앱 시작 때 승인된 seed JSON을 DB에 다시 적재해 같은 스냅샷/히스토리 상태로 시작 |
| `prod` | 실제 위치와 Opinet 조회 결과를 같은 캐시 규칙으로 저장 |

`demo`는 "가짜 화면 모드"가 아닙니다. `prod`와 같은 캐시/stale/watchlist 규칙을 재현 가능한 데이터로 보여주는 정식 실행 경로입니다.

## 운영 메모

- `prod` 검색과 demo seed 생성은 모두 `opinet.apikey`만 사용합니다.
- `CrashReporter.recordNonFatalSafely`는 ordinary reporter failure가 원래 recoverable station/location error를 바꾸지 않게 하며 cancellation과 fatal `Error`는 보존합니다. SDK-neutral observability 계약은 `core:observability`가 소유하고 flavor별 SDK binding은 `app`에 남습니다.
- Room은 `exportSchema = true`이며 canonical checked-in schema는 `core/database/schemas/com.gasstation.core.database.GasStationDatabase/`의 versions 1–5입니다. v5 현재 생성물은 version-introducing commit/toolchain에서 남긴 historical v5와 byte-identical이고, CI schema gate는 tracked/untracked drift와 generated current schema의 불일치를 막습니다.
- `MigrationTestHelper` 계약은 모든 지원 시작점 1/2/3/4→5, v2→v3의 의도된 disposable price-history reset, 그리고 v4의 성공한 빈 snapshot marker 보존을 다룹니다. v2→v3에서도 cache와 watch 행은 보존됩니다.
- host Robolectric의 production-builder/index 및 migration tests는 항상 실행합니다. Task 6 당시 connected device가 없었으므로 instrumented migration tests는 compile/assets 검증만 되었고 device-executed라고 주장하지 않습니다.
- 문서나 UI에서 "캐시 있음"을 말할 때는 `fetchedAt != null`보다 `hasCachedSnapshot` 의미를 기준으로 이해하는 편이 더 정확합니다.
