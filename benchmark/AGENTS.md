# Benchmark Agent Contract

`benchmark`를 바꿀 때 루트 `AGENTS.md`를 보완한다.

## 증거

- 에뮬레이터는 smoke다. 커밋할 숫자는 물리 기기와 `demoBenchmark`가 필요하다.
- 기기가 여러 대면 명령 전에 `ANDROID_SERIAL`을 명시한다.
- 실패, 일부만 완료, warm state, 에뮬레이터 결과로 `docs/performance.md`나 README 숫자를 바꾸지 않는다.

## Selector

- `station-list-watch-toggle`, `bottom-nav-watchlist`, `watchlist-card`를 유지한다.
- selector 실패는 제품 문구를 바꾸기 전에 benchmark 계약으로 본다.
- `benchmark`는 앱 기능을 대신 구현하지 않는다.

## 메타데이터

- 커밋할 증거에는 device, Android/API, variant, 날짜, scenario, JSON/trace 경로를 적는다.
- connected 수집 전에 `./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark`를 실행한다.
- 명령은 `docs/verification-matrix.md`와 `docs/performance.md`다.
