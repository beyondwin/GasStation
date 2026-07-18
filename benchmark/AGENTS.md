# Benchmark Agent Contract

이 파일은 `benchmark` 변경에 대해 루트 `AGENTS.md`를 보완한다.

## Evidence Boundary

- emulator 실행은 smoke 증거로만 사용한다. commit할 성능 수치에는 physical device와 `demoBenchmark` target이 필요하다.
- 여러 device가 연결돼 있으면 connected benchmark 명령 전에 `ANDROID_SERIAL`을 명시한다.
- 실패, 일부만 완료, warm state 전용, emulator 실행 결과로 `docs/performance.md`나 `README.md`의 수치를 교체하지 않는다.

## Selector Contract

- resource로 노출된 selector `station-list-watch-toggle`, `bottom-nav-watchlist`, `watchlist-card`를 보존한다.
- selector 실패는 production UI copy나 semantics를 바꾸기 전에 benchmark 계약 회귀로 다룬다.
- `benchmark`는 앱 runtime을 사용하며 제품 동작을 구현하는 대체 경로가 아니다.

## Required Metadata

- commit할 증거에는 device model, Android/API version, build variant, 측정일, scenario, benchmark JSON/trace artifact 경로를 기록한다.
- connected 증거 수집 전에 `./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark`를 실행한다.
- 정확한 physical-device 명령과 보고 경계는 `docs/verification-matrix.md`와 `docs/performance.md`를 따른다.
