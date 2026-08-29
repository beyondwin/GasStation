# 오프라인 전략

마지막 성공 스냅샷을 버리지 않는다. 실패와 빈 결과를 구분한 채 계속 보여준다. 구현은 `DefaultStationRepository`와 `core:database`다.

## 답

| 질문 | 답 |
| --- | --- |
| 네트워크가 실패하면 목록을 비우나 | 아니요. 기존 스냅샷을 유지한다 |
| 0건이면 실패인가 | 아니요. 성공한 빈 결과도 마커로 남긴다 |
| stale 기준은 | 아래 계약의 `freshness` |
| 오래된 캐시는 언제 지우나 | refresh 성공 후 7일보다 오래된 행과 마커 |
| 가격 변화는 | `station_price_history` 최신 이력 |
| 관심은 현재 목록에 없으면 사라지나 | 아니요. 저장 identity를 유지하고 가격 없음을 보여준다 |

## 저장

| 테이블 | 역할 |
| --- | --- |
| `station_cache` | 버킷의 최신 주유소 행 |
| `station_cache_snapshot` | 그 버킷의 마지막 성공 시각 마커 |
| `station_price_history` | 주유소/유종별 최근 가격 |
| `watched_station` | 저장한 관심 항목 |

마커가 없으면 성공한 0건과 캐시 없음을 구분하기 어렵다.

## 캐시 키

`StationQueryCacheKey`는 위치 버킷, 반경, 유종만 넣는다. 버킷은 250m다. 브랜드, 정렬, 지도 앱은 키에 없다. 읽기 모델에서 적용한다.

목록의 active query에는 브랜드와 정렬도 들어간다. 좌표가 있는 상태에서 조건이 바뀌면 refresh를 다시 요청한다. 기존 스냅샷은 바로 다시 쓸 수 있고, 원격 성공 시 같은 버킷이 최신으로 바뀐다.

## 관찰

invalidation마다 마커와 `stationId ASC`로 정렬한 행을 **하나의 Room transaction** 안에서 읽어 `StationBucketSnapshot` 하나만 내보낸다. 두 DAO Flow를 `combine`하지 않는다.

마커 없음: `stations` 빈 목록, `hasCachedSnapshot = false`. 보여줄 캐시가 없다. 마커가 없으면 관찰자는 행을 빈 목록으로 정규화한다.

마커만 있고 행 0건: `hasCachedSnapshot = true`. 성공한 빈 결과다. 전면 오류가 아니다.

행이 있으면 watch와 가격 히스토리를 붙인다. 브랜드·정렬은 클라이언트에서 적용한다. 좌표나 가격이 잘못된 행은 목록에서 빠진다.

## stale

`StationCachePolicy`가 아래 계약의 `freshness`로 `Fresh`/`Stale`을 나눈다. stale이라고 결과를 버리지 않는다. UI는 배너와 마지막 갱신 시각을 보여준다.

## 성공한 새로고침

같은 키에서는 가장 나중에 시작한 generation만 저장에 들어간다. 승인된 generation에서만 `fetchedAt`은 guarded write 안에서, entity 생성과 transaction 직전에 잡는다.

1. 기존 `station_cache` 행 삭제
2. 새 스냅샷 행 저장
3. 마커 갱신
4. 가격 히스토리 추가
5. 주유소/유종별 최신 10건으로 자르기
6. 7일보다 오래된 행·마커 정리
7. `StationEvent.SearchRefreshed`

실패한 refresh는 기존 캐시를 지우지 않는다. participant tombstone은 모든 generation이 끝날 때까지 남고, opaque entry identity가 replacement entry에 대한 stale ticket의 ABA 재사용을 막는다.

## 실패한 새로고침

실패 종류는 `InvalidPayload`, `Timeout`, `Network`, `Http(statusCode)`, `Unknown`이다. 빈 목록은 성공이다. 요청 유종과 같지 않은 proxy 행은 거부한다. 재시도 횟수와 HTTP 범위는 아래 계약의 `retry`다. 늦은 generation은 조용히 끝난다. 기존 스냅샷은 남는다.

## 기계 판독 정책 계약

[`station-data-policy.json`](station-data-policy.json)과 이 block은 같은 값이어야 한다.

<!-- station-data-policy:start -->
```json
{
  "schemaVersion": 1,
  "contractId": "station-data-correctness-v1",
  "retry": {
    "maxRetries": 1,
    "delayMs": 500,
    "retryableReasons": ["Timeout", "Network"],
    "retryableHttpStatuses": [408, 429],
    "retryableHttpRanges": [
      {"minInclusive": 500, "maxInclusive": 599}
    ],
    "nonRetryableReasons": ["InvalidPayload", "Unknown", "Cancellation", "Superseded"],
    "nonRetryableHttpRanges": [
      {"minInclusive": 400, "maxInclusive": 407},
      {"minInclusive": 409, "maxInclusive": 428},
      {"minInclusive": 430, "maxInclusive": 499}
    ],
    "unlistedHttpRetryable": false
  },
  "freshness": {
    "storagePrecisionMs": 1,
    "fresh": {"operator": "<=", "ageMs": 300000},
    "stale": {"operator": ">", "ageMs": 300000},
    "firstStaleAgeMs": 300001,
    "timeCrossingWithoutRoomMutation": true,
    "tickerOwnership": "one_cancellable_ticker_per_atomic_snapshot",
    "metadataSubscriptionsRestartOnTimeCrossing": false
  },
  "superseded": {
    "completion": "normal_silent",
    "forbiddenSideEffects": [
      "Retry",
      "FailureReport",
      "SnapshotWrite",
      "HistoryWrite",
      "Prune",
      "SearchRefreshed",
      "RetryAttempted"
    ]
  },
  "schema": {
    "exportedVersions": [
      {"version": 1, "introducingCommit": "e64634f", "room": "2.6.1", "ksp": "1.9.23-1.0.20"},
      {"version": 2, "introducingCommit": "a705fdb", "room": "2.8.4", "ksp": "2.3.6"},
      {"version": 3, "introducingCommit": "9b070ab", "room": "2.8.4", "ksp": "2.3.6"},
      {"version": 4, "introducingCommit": "014127f", "room": "2.8.4", "ksp": "2.3.6"},
      {"version": 5, "introducingCommit": "da96a5f", "room": "2.8.4", "ksp": "2.3.7"}
    ],
    "currentVersion": 5,
    "currentV5MatchesHistorical": true,
    "supportedMigrationStarts": [1, 2, 3, 4],
    "v2ToV3PriceHistory": "intentional_disposable_reset",
    "v4SuccessfulEmptyMarkerPreserved": true,
    "migrationEvidence": {
      "status": "compiled_assets_verified",
      "hostRobolectricExecuted": true,
      "instrumentedCompiled": true,
      "assetsCompared": true,
      "connectedDeviceAvailable": false,
      "connectedDeviceExecuted": false,
      "unavailableReason": "no_connected_device"
    }
  }
}
```
<!-- station-data-policy:end -->

## 관심 fallback

1. `watched_station`을 저장 시각 순으로 읽는다.
2. 선택 유종의 최신 캐시가 있으면 그 가격을 쓴다.
3. 없으면 같은 유종 히스토리와 저장 좌표/브랜드/이름을 쓴다.
4. 둘 다 없어도 행을 남기고 `선택 유종 가격 없음`을 보여준다.

반경, 브랜드, Nearby 정렬은 관심 query에 없다.

## demo / prod

| 경로 | 하는 일 |
| --- | --- |
| `demo` | 시작 때 seed JSON을 DB에 다시 넣는다 |
| `prod` | 실제 위치와 Opinet 결과를 같은 규칙으로 저장한다 |

`demo`는 가짜 화면이 아니다. 같은 캐시 규칙을 고정 데이터로 보여준다.

## 운영

- `prod` 검색과 demo seed 생성은 `opinet.apikey`만 쓴다.
- `CrashReporter.recordNonFatalSafely`는 원래 오류를 바꾸지 않는다. cancellation과 fatal Error는 삼키지 않는다.
- Room schema는 `exportSchema = true`다. 현재 버전은 계약의 `schema`가 맞는다. connected device가 없으면 실행했다고 쓰지 않는다.
- "캐시 있음"은 `fetchedAt != null`이 아니라 `hasCachedSnapshot`이다.
