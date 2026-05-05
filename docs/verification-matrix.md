# 검증 매트릭스

이 문서는 GasStation의 실제 검증 명령과 실행 범위를 설명하는 단일 출처입니다. "상황별로 어떤 명령을 어디까지 돌리면 되는가"를 바로 보여주는 실행 체크리스트로 사용합니다.

## 전제

- Java 17 기준입니다.
- `prod` 앱을 실제로 실행하려면 사용자 로컬 `opinet.apikey`가 필요합니다. `demo` 실행과 assemble에는 키가 필요 없습니다.
- benchmark 모듈은 `demo` 데이터를 대상으로 동작합니다.

## 문서/계약 설명 갱신 확인

코드를 바꾸지 않고 architecture, state, offline, module contract 문서를 갱신했을 때 최소 확인입니다.

```bash
git diff --check -- README.md docs/architecture.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md docs/module-contracts.md docs/improvement-analysis.md
```

문서 갱신이 이미 구현된 key handling, cleartext, backup, cache/event/state, location, brand label 계약을 설명한다면 아래 관련 테스트도 선택합니다.

```bash
./gradlew \
  :domain:station:test \
  :core:database:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest
```

이 조합은 `StationEvent` 계약, retry/pruning 정책, station-list 상태 분리, watchlist event, 주소 lookup, 브랜드 label, cleartext resource, Android backup 비활성화, prod secret fail-fast 의미를 다시 확인합니다.

## 빠른 로컬 확인

문서/리팩터링/가벼운 변경 후 가장 먼저 돌릴 조합입니다.

```bash
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :benchmark:assemble
```

## 경로별 신뢰 확인

`demo`, `prod`, demo seed 도구까지 같이 확인해야 할 때 권장합니다.

```bash
./gradlew \
  :tools:demo-seed:test \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

## 머지 전 권장 회귀 세트

모듈 단위 회귀를 폭넓게 확인하는 조합입니다. 공유 enum/label 이동, settings dependency cleanup, station retry/pruning, station-list 상태 projection 회귀를 함께 막습니다.

```bash
./gradlew \
  :domain:location:test \
  :core:model:test \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :tools:demo-seed:test \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

## 기기 기반 UI 확인

demo 실제 흐름을 기기나 에뮬레이터에서 확인합니다.

```bash
./gradlew :app:connectedDemoDebugAndroidTest
```

대표 시나리오:

- seed를 적재한 목록 화면 진입
- 북마크 저장
- watchlist 화면 이동

## 성능/프로파일 확인

매크로벤치마크와 baseline profile 수집이 필요할 때 사용합니다.

```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

현재 benchmark는 다음 흐름을 기준으로 합니다.

- cold start
- watchlist 열기
- baseline profile 수집 시 새로고침과 watchlist 진입

## 참고

- `./benchmark/run-demo-benchmark.sh`는 빠른 assemble 확인용 래퍼입니다.
- 앱 모듈의 사용 가능한 variant/task 표면은 `./gradlew :app:tasks --all`로 다시 확인할 수 있습니다.
