# 검증 매트릭스

이 문서는 GasStation의 실제 검증 명령과 실행 범위를 설명하는 단일 출처입니다. "상황별로 어떤 명령을 어디까지 돌리면 되는가"를 바로 보여주는 실행 체크리스트로 사용합니다.

## 전제

- Gradle과 Robolectric 검증은 Java 21 이상 기준입니다. 앱의 Java/Kotlin bytecode target은 JVM 17입니다.
- `prod` 앱을 실제로 실행하려면 사용자 로컬 `opinet.apikey`가 필요합니다. `demo` 실행과 assemble에는 키가 필요 없습니다.
- benchmark 모듈은 `demo` 데이터를 대상으로 동작합니다.

## 문서/계약 설명 갱신 확인

문서 변경은 세 가지로 나눠 확인합니다.

### 1. 이력/근거 문서만 변경

`docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`처럼 현재 계약이 아닌 이력이나 근거 문서만 바꿨다면 수정한 파일만 diff check합니다.

```bash
git diff --check -- <changed files>
```

이 경우 Gradle 테스트는 기본 필수가 아닙니다. 다만 문서가 현재 동작, 현재 모듈 경계, 현재 명령을 새로 주장한다면 아래 live 문서 변경 기준으로 올려 봅니다.

### 2. live 계약 문서 변경

코드를 바꾸지 않고 architecture, state, offline, module contract, workflow, test strategy, verification matrix 같은 live 문서를 갱신했을 때 최소 확인입니다.

```bash
git diff --check -- README.md AGENTS.md .impeccable.md CHANGELOG.md CONTRIBUTING.md docs/agent-workflow.md docs/project-reading-guide.md docs/architecture.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md docs/module-contracts.md docs/security-trade-offs.md docs/performance.md docs/deployment.md docs/adr/*.md docs/release-notes/*.md
```

문서가 파일 경로, Gradle task, 활성 모듈, CI job을 언급한다면 실제 표면도 확인합니다.

```bash
sed -n '1,220p' settings.gradle.kts
find docs -maxdepth 3 -type f | sort
```

문서 갱신이 이미 구현된 key handling, cleartext, backup, cache/event/state, location, brand label 계약을 설명한다면 아래 관련 테스트도 선택합니다.

```bash
./gradlew \
  :core:model:test \
  :core:network:test \
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

### 3. README, demo story, 릴리스, 성능 문서 변경

README, release notes, deployment, performance 문서가 현재 실행 결과나 측정값을 말한다면 diff check에 더해 해당 명령을 실행하거나 기존 증거를 명시합니다.

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md docs/deployment.md docs/performance.md docs/verification-matrix.md docs/release-notes/*.md
```

대표 기준:

- README의 빠른 검증 명령을 바꿨다면 같은 명령이나 더 좁은 관련 명령을 실행합니다.
- demo story나 screenshot 전제를 바꿨다면 `:app:assembleDemoDebug` 또는 관련 UI test/benchmark 전제를 확인합니다.
- 릴리스/배포 절차를 바꿨다면 `docs/deployment.md`의 절차와 이 문서의 릴리스/배포 확인 명령을 함께 봅니다.
- 성능 수치나 benchmark journey를 바꿨다면 `docs/performance.md`와 이 문서의 Hero Benchmark Evidence 기준을 함께 봅니다.

## 빠른 로컬 확인

문서/리팩터링/가벼운 변경 후 가장 먼저 돌릴 조합입니다.

live 계약 문서, 링크, 경로, toolchain/version/module 계약은 Gradle 없이 다음 checker로 확인합니다.

```bash
scripts/agent/check-contracts.sh
```

| Scope | 실행 범위 |
| --- | --- |
| `docs` | live 문서 링크, 경로, toolchain/version/module 계약 |
| `fast` | 가벼운 host-side 회귀와 demo assemble |
| `ui` | designsystem/feature UI test와 Roborazzi |
| `data` | model/domain/data/database 회귀와 module boundary |
| `app` | demo/prod app test와 debug assemble, benchmark assemble |
| `release` | 기존 머지 전 회귀와 prod release assemble |
| `auto` | changed path를 보수적으로 위 scope에 매핑 |

Codex/Claude hook은 Gradle을 실행하지 않습니다. 무거운 테스트와 assemble은 명시적인 `scripts/agent/verify.sh <scope>` 호출이 소유합니다.

```bash
./gradlew \
  :core:model:test \
  :core:network:test \
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
  :tools:demo-seed:verifyDemoSeedAsset \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

`verifyDemoSeedAsset`는 `opinet.apikey`나 네트워크 없이 체크인된 `app/src/demo/assets/demo-station-seed.json`의 15개 query matrix, origin/version, history key·가격·timestamp, RTO/ETC portfolio station을 검증합니다. `:tools:demo-seed:test`도 실제 체크인 asset을 읽어 같은 계약을 CI에서 보호합니다. 반면 `generateDemoSeed`는 실제 Opinet 데이터를 갱신하는 운영자용 live refresh이므로 로컬 `opinet.apikey`가 필요하며 자동화 gate에 포함하지 않습니다.

## Nearby 고밀도 UI 집중 회귀

Nearby 요약·필터·가격 이력, 알뜰 그룹 migration, icon-only navigation처럼 이번 경계에 직접 닿는 계약은 다음 조합으로 확인합니다.

```bash
./gradlew \
  verifyModuleBoundaries \
  :core:model:test \
  :core:designsystem:testDebugUnitTest \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:compileDemoDebugAndroidTestKotlin
```

## Settings readiness와 persistence 집중 회귀

DataStore 첫 emission readiness, committed preference mutation, Nearby query gating, Settings/Nearby 동기화와 activity recreation을 함께 확인할 때는 다음 명령을 실행합니다.

```bash
./gradlew \
  :core:datastore:testDebugUnitTest \
  :domain:settings:test \
  :data:settings:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  --warning-mode fail
```

`StationPortfolioFlowTest.demoSettingsAndNearby_sharePersistedPreferencesAcrossNavigationAndRecreation`은 connected demo 경로에서 Nearby와 Settings의 committed 값 공유 및 recreation을 보호합니다. `assembleProdDebug`는 keyless build 확인만 합니다. 실제 prod runtime launch에는 사용자 로컬 `opinet.apikey`가 필요하며, 이 명령은 live Opinet을 검증하지 않습니다.

Screenshot 골든을 의도적으로 갱신할 때는 영향 모듈을 명시해 record한 뒤 같은 모듈을 verify합니다.

```bash
./gradlew \
  :core:designsystem:recordRoborazziDebug \
  :feature:station-list:recordRoborazziDebug \
  :feature:settings:recordRoborazziDebug

./gradlew \
  :core:designsystem:verifyRoborazziDebug \
  :feature:station-list:verifyRoborazziDebug \
  :feature:settings:verifyRoborazziDebug
```

## 머지 전 권장 회귀 세트

모듈 단위 회귀를 폭넓게 확인하는 조합입니다. 공유 값 객체/enum/label 이동, 주소 정규화, observability 경계, settings dependency cleanup, station retry/pruning, station-list 상태 projection 회귀를 함께 막습니다.

```bash
./gradlew \
  spotlessCheck lint \
  :core:model:test \
  :core:network:test \
  :domain:location:test \
  :core:observability:test \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyRoborazziDebug \
  coverageXmlReport \
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

## 검증 깊이 측정 (온디맨드 / report-only)

라인 커버리지 숫자 너머의 신호를 측정·기록하는 명령입니다. 빌드를 깨는 게이트가 아니라 신호 수집용이며, 기본 PR gate에는 넣지 않습니다.

```bash
# JVM 모듈 변이 테스트 — 온디맨드. 리포트는 각 모듈 build/reports/pitest/.
# domain:station 은 mutationThreshold 40 floor 게이트라 점수가 떨어지면 실패한다.
# domain:settings / domain:location 은 report-only 베이스라인이다.
./gradlew :domain:station:pitest
./gradlew :domain:settings:pitest
./gradlew :domain:location:pitest
```

의존성 신선도는 `.github/dependabot.yml`이 Gradle과 GitHub Actions 생태계를 매주 확인해 그룹 PR로 보고합니다. 로컬 `dependencyUpdates` 태스크는 최신 플러그인도 Gradle 10에서 제거될 `Task.project` API를 실행하므로 제거했습니다.

모듈 경계 가드, Compose v1 test API 가드, CI Java/Robolectric 호환성 가드는 빠르고 config-cache-safe하므로 빌드를 깨는 게이트입니다. CI `static-analysis` job에 포함되며 로컬에서도 단독 실행할 수 있습니다.

```bash
# 금지된 production 모듈 의존성 엣지를 검증한다. 의도된 core:location→domain:location 예외는 제외.
./gradlew verifyModuleBoundaries
./gradlew verifyNoDeprecatedComposeTestApis
./gradlew verifyCiRobolectricRuntime
```

> `coverageXmlReport`는 JaCoCo 0.8.15로 전체 unit-test matrix를 실행하고 app, core Android, data, feature, JVM 모듈의 authored class를 `build/reports/coverage/report.xml`에 집계합니다. 현재 coverage는 신호 수집용이며, 의미 있는 모듈별 floor가 별도로 설계되기 전까지 blocking threshold로 승격하지 않습니다. Kover는 0.9.8의 미해결 Gradle 10 deprecation 때문에 제거했습니다.

## CI 연결

GitHub Actions는 PR 피드백 시간을 줄이기 위해 PR과 release 성격의 push를 다르게 검증합니다. 자세한 내용은 `.github/workflows/android.yml`을 참고합니다.

| Trigger | 실행 범위 |
| --- | --- |
| `pull_request` | `agent-contracts` (agent contract tests + full checker), `static-analysis` (spotlessCheck + lint + verifyModuleBoundaries + verifyNoDeprecatedComposeTestApis + verifyCiRobolectricRuntime), `unit-tests` (전 모듈 단위 테스트 + demo instrumentation test 컴파일), `screenshot-tests` (verifyRoborazziDebug), `assemble` (demo/prod debug + benchmark) |
| `push` to `main` | PR 범위(`agent-contracts` 포함) + `release-assemble` (`:app:assembleProdRelease`) + `coverage` (`coverageXmlReport`, unit-tests 완료 후 실행) |
| `push` tag `v*` | PR 범위(`agent-contracts` 포함) + `release-assemble` + `coverage` |

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
- `station-list-watch-toggle`로 관심 저장
- `bottom-nav-watchlist`로 관심 화면 이동 후 `watchlist-card` 확인

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
ANDROID_SERIAL=<device serial> ./gradlew :app:installDemoBenchmark :benchmark:connectedBenchmarkAndroidTest
```

The connected command installs the `demoBenchmark` target APK before running the benchmark APK. The watchlist benchmark launches `com.gasstation.demo/com.gasstation.MainActivity` explicitly and uses Compose test tags exposed as resource IDs: `station-list-watch-toggle`, `bottom-nav-watchlist`, and `watchlist-card`. If those selectors fail, treat it as a benchmark contract regression before changing production UI copy.

`verifyRoborazziDebug`는 designsystem icon-only navigation, Nearby populated light/dark와 shared states, `radius_menu_open_state`·`fuel_menu_open_state`·`brand_menu_open_state`, Watchlist 5행, Settings overview/detail과 BrandFilter light/dark snapshot을 검증합니다. 320dp menu containment와 큰 글꼴 summary/station metadata, Watchlist/Settings의 200% font scale은 Compose 접근성 테스트가 소유합니다. record 후에는 생성 이미지를 직접 검사한 다음 verify를 실행합니다.

After a successful run, inspect generated JSON and trace artifacts:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
find benchmark/build/outputs/connected_android_test_additional_output -name '*.perfetto-trace' -print
```

Do not add this command to the default PR gate. It depends on a connected physical device and is part of release or portfolio evidence collection. The committed reference numbers and known limitations live in [`docs/performance.md`](performance.md).

## 참고

- `docs/build-velocity.md`는 Gradle parallel/cache/configuration-cache 기본값과 release assemble gate 위치를 timing 근거와 함께 설명합니다.
- `./benchmark/run-demo-benchmark.sh`는 빠른 assemble 확인용 래퍼입니다.
- 앱 모듈의 사용 가능한 variant/task 표면은 `./gradlew :app:tasks --all`로 다시 확인할 수 있습니다.
