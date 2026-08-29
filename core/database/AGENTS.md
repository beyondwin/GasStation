# Database Agent Contract

`core:database`를 바꿀 때 루트 `AGENTS.md`를 보완한다.

## Schema

- entity, column, index, table 변경에는 schema version, `exportSchema = true`, migration이 필요하다. 기준 파일은 `core/database/schemas/com.gasstation.core.database.GasStationDatabase/`이고 지금 버전은 5다. 1–5를 같이 보존한다.
- 제품 결정으로 persistence가 바뀌지 않는 한 `fallbackToDestructiveMigration`을 넣지 않는다.
- Android backup 제한을 유지한다. 사용자 DB를 테스트 증거로 열람하거나 공개하지 않는다.

## Snapshot

- 성공한 빈 스냅샷과 캐시 없음을 구분한다.
- 캐시 있음은 `StationSearchResult.hasCachedSnapshot`이다. `fetchedAt != null`로 우회하지 않는다.
- 교체나 pruning은 원자 버킷, 현재 행, 빈 마커, 히스토리, 관심 fallback을 지켜야 한다.

## 검증

- 바꾸기 전에 `StationCacheDaoTest`와 `GasStationDatabaseMigrationTest`를 읽는다.
- [검증 매트릭스의 station data correctness](../../docs/verification-matrix.md#station-data-correctness-집중-회귀)에서 database/schema 검증을 고른다.
- repository나 watchlist fallback에 닿으면 [오프라인 전략](../../docs/offline-strategy.md)과 data 테스트도 본다.
- 연결된 기기가 없으면 compile/asset 증거와 미실행 사유를 나눠 남긴다.
