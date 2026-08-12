# Database Agent Contract

이 파일은 `core:database` 변경에 대해 루트 `AGENTS.md`를 보완한다.

## Schema Contract

- entity, column, index, table 변경에는 명시적인 Room schema version 결정, `exportSchema = true` exported schema 검토, migration coverage가 필요하다. canonical evidence는 `core/database/schemas/com.gasstation.core.database.GasStationDatabase/`이고 schema version 5에서 versions 1–5를 함께 보존한다.
- 승인된 제품 결정으로 persistence 계약이 바뀌지 않는 한 `fallbackToDestructiveMigration`이나 다른 data-loss 우회로를 추가하지 않는다.
- Android backup 제한을 유지하고 사용자 database 내용을 테스트 증거로 열람하거나 공개하지 않는다.

## Snapshot Contract

- 성공한 빈 snapshot과 cached snapshot이 없는 상태의 구분을 유지한다.
- cache 존재 의미는 `StationSearchResult.hasCachedSnapshot`이다. 이를 `fetchedAt != null` 우회 판단으로 바꾸지 않는다.
- snapshot 교체나 pruning 변경은 atomic bucket replacement, 현재 row, 빈 snapshot marker, history, watchlist fallback 동작을 보존해야 한다.

## Required Verification

- schema나 DAO 동작을 바꾸기 전에 `StationCacheDaoTest`와 `GasStationDatabaseMigrationTest`를 읽는다.
- database 변경에는 [검증 매트릭스의 station data correctness 집중 회귀](../../docs/verification-matrix.md#station-data-correctness-집중-회귀)에서 변경 위험에 맞는 database/schema/asset 검증을 선택한다.
- repository assembly, pruning, cache, watchlist fallback 영향이 있으면 [오프라인 전략](../../docs/offline-strategy.md)과 data repository 회귀를 함께 점검한다.
- dependency 변경에는 모듈 경계 검증을 포함한다. connected device가 없으면 compile/merged-asset evidence와 미실행 사유를 구분해 남긴다.
