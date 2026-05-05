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
| watchlist는 현재 목록에 없으면 사라지나 | 아니요. 저장 항목과 최신 히스토리로 최대한 복원합니다. |

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

저장소는 먼저 현재 쿼리 버킷의 스냅샷 마커와 캐시 행을 같이 읽습니다.

### 경우 1. 스냅샷 마커가 없음

- `stations = emptyList()`
- `freshness = Stale`
- `fetchedAt = null`
- `hasCachedSnapshot = false`

이 상태는 "아직 보여줄 캐시가 없음"을 뜻합니다.

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

## stale 판정

`StationCachePolicy`는 현재 5분 기준으로 `Fresh`와 `Stale`를 나누고, 오래된 캐시 정리 cutoff도 같은 정책 객체에서 계산합니다.

- 5분 이내: `Fresh`
- 5분 초과: `Stale`
- 보관 기본값: refresh 성공 시각 기준 7일

stale이라고 해서 결과를 버리지는 않습니다. UI는 stale 배너를 띄우고 마지막 갱신 시각을 보여줍니다.

## 새로고침 성공 시

`refreshNearbyStations()` 성공 시 저장소는 한 버킷 단위로 아래 작업을 합니다.

1. 기존 `station_cache` 행 삭제
2. 새 스냅샷 행 저장
3. `station_cache_snapshot` 마커 갱신
4. 새 가격을 `station_price_history`에 추가
5. 주유소/유종별 히스토리를 최신 10건으로 자르기
6. `StationCachePolicy.pruneCutoff()`보다 오래된 `station_cache` 행과 `station_cache_snapshot` 마커 정리
7. `StationEvent.SearchRefreshed` 기록

즉 스냅샷 교체는 "행 + 마커"가 함께 움직이는 구조입니다. pruning은 성공한 persistence 뒤에만 실행되므로 실패한 refresh가 기존 캐시를 지우지 않습니다.

## 새로고침 실패 시

원격 조회가 실패하면 저장소는 `StationRefreshException(reason)`을 던집니다.

- `Timeout`
- `Network`
- `InvalidPayload`
- `Unknown`

`StationRetryPolicy`는 실패 시 기존 스냅샷을 지우거나 바꾸지 않습니다. `Timeout`과 `Network` 실패만 500ms 뒤 한 번 재시도하고, 두 번째 시도가 성공하면 `StationEvent.RetryAttempted(succeeded=true)`, `StationRefreshException`으로 끝나면 `succeeded=false`를 남깁니다. 예기치 않은 두 번째 예외와 cancellation은 retry 이벤트로 포장하지 않고 그대로 전파합니다. `InvalidPayload`와 `Unknown`은 재시도하지 않습니다.

하지만 기존 `station_cache`와 `station_cache_snapshot`은 지우지 않습니다. 이 덕분에 UI는 실패 중에도 마지막 성공 결과를 계속 렌더링할 수 있습니다.

## watchlist fallback

watchlist는 현재 목록보다 더 방어적으로 동작합니다.

1. `watched_station`에서 저장 항목을 읽습니다.
2. 같은 `stationId`의 최신 캐시가 있으면 그 정보를 우선 사용합니다. 이 최신 행 선택은 DAO SQL이 station별 한 행만 반환하며, timestamp tie는 유종, 반경, 위치 버킷 순서로 고정합니다.
3. 최신 캐시가 없으면 `station_price_history` 최신 행과 저장된 좌표/브랜드/이름으로 대체 모델을 만듭니다.
4. 둘 다 없으면 해당 항목은 요약에서 빠집니다.

즉 사용자가 저장한 항목은 현재 검색 결과에서 사라져도 바로 비어 버리지 않습니다.

## demo와 prod의 의미

| 경로 | 오프라인 관점에서 하는 일 |
| --- | --- |
| `demo` | 앱 시작 때 승인된 seed JSON을 DB에 다시 적재해 같은 스냅샷/히스토리 상태로 시작 |
| `prod` | 실제 위치와 Opinet 조회 결과를 같은 캐시 규칙으로 저장 |

`demo`는 "가짜 화면 모드"가 아닙니다. `prod`와 같은 캐시/stale/watchlist 규칙을 재현 가능한 데이터로 보여주는 정식 실행 경로입니다.

## 운영 메모

- `prod` 검색과 demo seed 생성은 모두 `opinet.apikey`만 사용합니다.
- `GasStationDatabaseMigrationTest`는 `station_cache_snapshot` 도입과 stationId 선행 최신 캐시 index를 포함한 현재 DB version 5 migration을 검증합니다.
- 문서나 UI에서 "캐시 있음"을 말할 때는 `fetchedAt != null`보다 `hasCachedSnapshot` 의미를 기준으로 이해하는 편이 더 정확합니다.
