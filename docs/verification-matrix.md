# 검증 매트릭스

이 문서는 GasStation의 실제 검증 명령과 실행 범위를 설명하는 단일 출처입니다. "상황별로 어떤 명령을 어디까지 돌리면 되는가"를 바로 보여주는 실행 체크리스트로 사용합니다.

## 전제

- Java 17 기준입니다.
- `prod` 앱을 실제로 실행하려면 사용자 로컬 `opinet.apikey`가 필요합니다. `demo` 실행과 assemble에는 키가 필요 없습니다.
- benchmark 모듈은 `demo` 데이터를 대상으로 동작합니다.

## 문서/계약 설명 갱신 확인

코드를 바꾸지 않고 architecture, state, offline, module contract 문서를 갱신했을 때 최소 확인입니다.

```bash
git diff --check -- README.md AGENTS.md .impeccable.md CHANGELOG.md CONTRIBUTING.md docs/agent-workflow.md docs/project-reading-guide.md docs/architecture.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md docs/module-contracts.md docs/security-trade-offs.md docs/performance.md docs/deployment.md docs/adr/*.md docs/release-notes/*.md
```

`docs/superpowers/specs/`와 `docs/superpowers/plans/`는 과거 설계/계획 이력이므로 current contract 확인 명령에는 기본 포함하지 않습니다. 해당 이력 문서를 직접 수정했다면 수정한 파일 경로를 위 명령에 명시적으로 추가합니다.

문서 갱신이 이미 구현된 key handling, cleartext, backup, cache/event/state, location, brand label 계약을 설명한다면 아래 관련 테스트도 선택합니다.

```bash
./gradlew \
  :core:model:test \
  :domain:location:test \
  :domain:station:test \
  :core:observability:test \
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
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
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

모듈 단위 회귀를 폭넓게 확인하는 조합입니다. 공유 값 객체/enum/label 이동, 주소 정규화, observability 경계, settings dependency cleanup, station retry/pruning, station-list 상태 projection 회귀를 함께 막습니다.

```bash
./gradlew \
  spotlessCheck lint \
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyRoborazziDebug \
  koverXmlReport \
  :app:assembleProdRelease
```

## 기본 Fast Path와 Opt-in 확장

기본 lint 명령은 production source 중심으로 돌고, test source lint는 `-Pgasstation.lintTestSources=true`로 명시합니다.

기본 unit-test 명령은 Roborazzi screenshot class를 제외합니다. Screenshot 회귀는 `verifyRoborazziDebug`가 소유합니다.

Compose compiler report와 metric은 기본 생성하지 않습니다. 분석이 필요할 때만 명시적으로 켭니다.

```bash
./gradlew lint -Pgasstation.lintTestSources=true --continue
./gradlew :core:designsystem:testDebugUnitTest -Pgasstation.includeRoborazziInUnitTests=true
./gradlew :feature:station-list:compileDebugKotlin -Pgasstation.composeCompilerReports=true
```

## CI 연결

GitHub Actions는 PR 피드백 시간을 줄이기 위해 PR과 release 성격의 push를 다르게 검증합니다. 자세한 내용은 `.github/workflows/android.yml`을 참고합니다.

| Trigger | 실행 범위 |
| --- | --- |
| `pull_request` | `static-analysis` (spotlessCheck + lint), `unit-tests` (전 모듈 단위 테스트), `screenshot-tests` (verifyRoborazziDebug), `assemble` (demo/prod debug + benchmark) |
| `push` to `main` | PR 범위 + `release-assemble` (`:app:assembleProdRelease`) + `coverage` (`koverXmlReport`, unit-tests 완료 후 실행) |
| `push` tag `v*` | PR 범위 + `release-assemble` + `coverage` |

`prodRelease` assemble과 coverage는 기본 PR matrix에 포함하지 않습니다. R8/minify 회귀나 coverage report가 PR마다 필요하다고 판단하면, 이 문서와 `.github/workflows/android.yml`을 같은 변경에서 갱신합니다.
`assemble` job은 GitHub runner의 메모리 피크를 낮추기 위해 demo debug, prod debug, benchmark assemble을 별도 Gradle 호출로 실행합니다.

## 릴리스/배포 확인

새 버전을 발행할 때는 [`docs/deployment.md`](deployment.md)의 절차를 따른 뒤 아래 명령을 최소 확인으로 사용합니다.

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts docs/deployment.md docs/verification-matrix.md docs/release-notes/*.md
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
./gradlew :app:assembleProdRelease
```

physical-device 성능 수치를 갱신하는 릴리스라면 "Hero Benchmark Evidence" 명령을 추가로 실행하고 `docs/performance.md`와 해당 릴리즈 노트에 기기/variant/측정일을 남깁니다.

## 기기 기반 UI 확인

demo 실제 흐름을 기기나 에뮬레이터에서 확인합니다.

```bash
./gradlew :app:connectedDemoDebugAndroidTest
```

대표 시나리오:

- seed를 적재한 목록 화면 진입
- 북마크 저장
- watchlist 화면 이동

## 위치 Geocoder 기기 smoke

API 33+ Geocoder callback path를 실제 기기나 에뮬레이터에서 확인합니다. Provider 출력은 환경별로 달라질 수 있으므로 test는 주소 문자열이 아니라 terminal domain result만 검증합니다.

```bash
./gradlew :core:location:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.core.location.AndroidAddressResolverDeviceTest
```

## 성능/프로파일 확인

매크로벤치마크와 baseline profile 수집이 필요할 때 사용합니다.

```bash
./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark
ANDROID_SERIAL=<device serial> ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

현재 benchmark는 다음 흐름을 기준으로 합니다.

- startup to first station-list content
- station-list scroll
- seeded refresh
- station save 후 watchlist 진입
- baseline profile 수집 시 startup, refresh, scroll, watchlist 진입

## Hero Benchmark Evidence

Hero benchmarks require a physical device for committed performance numbers. Emulator runs are allowed only as smoke checks. Use the `benchmark` build variant (forks `release` with `isDebuggable=false`, `isProfileable=true`, debug signing) so the same minified APK macrobenchmark expects can be installed and traced without a release keystore.

```bash
./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark
ANDROID_SERIAL=<device serial> ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

After a successful run, inspect generated JSON and trace artifacts:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
find benchmark/build/outputs/connected_android_test_additional_output -name '*.perfetto-trace' -print
```

Do not add this command to the default PR gate. It depends on a connected physical device and is part of release or portfolio evidence collection. The committed reference numbers and known limitations live in [`docs/performance.md`](performance.md).

## 참고

- `./benchmark/run-demo-benchmark.sh`는 빠른 assemble 확인용 래퍼입니다.
- 앱 모듈의 사용 가능한 variant/task 표면은 `./gradlew :app:tasks --all`로 다시 확인할 수 있습니다.
