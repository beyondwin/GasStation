# 검증 매트릭스

이 문서는 "상황별로 어떤 명령을 어디까지 돌리면 되는가"를 바로 보여주는 실행 체크리스트입니다.

## 전제

- Java 17 기준입니다.
- `prod` 앱을 실제로 실행하려면 `opinet.apikey`가 필요합니다.
- benchmark 모듈은 `demo` 데이터를 대상으로 동작합니다.

## 빠른 로컬 확인

문서/리팩터링/가벼운 변경 후 가장 먼저 돌릴 조합입니다.

```bash
./gradlew \
  :domain:location:test \
  :domain:settings:test \
  :domain:station:test \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
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

모듈 단위 회귀를 폭넓게 확인하는 조합입니다.

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
