# 검증 매트릭스

어떤 명령을 돌릴지는 여기서만 고른다. 테스트가 무엇을 막는지는 [테스트 전략](test-strategy.md)이다.

## 전제

- Gradle과 Robolectric은 Java 21 이상. 앱 bytecode는 JVM 17.
- `prod` 실행에는 `opinet.apikey`가 필요하다. `demo` 빌드와 assemble에는 키가 없다.
- benchmark는 `demo` 데이터를 쓴다.

## 문서 변경

### 1. 이력만 바꿈

`docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`만 바꿨다면 그 파일만 본다.

```bash
git diff --check -- <changed files>
```

Gradle은 기본이 아니다. 지금 동작이나 명령을 새로 주장하면 아래 live 기준으로 올린다.

### 2. live 문서

코드를 안 바꾸고 계약 문서를 고쳤을 때 최소 확인이다.

```bash
git diff --check -- README.md AGENTS.md .impeccable.md CHANGELOG.md CONTRIBUTING.md docs/agent-workflow.md docs/project-reading-guide.md docs/architecture.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md docs/module-contracts.md docs/security-trade-offs.md docs/performance.md docs/deployment.md docs/adr/*.md docs/release-notes/*.md
```

경로, Gradle task, 모듈, CI job을 지목하면 실제 표면도 본다.

```bash
sed -n '1,220p' settings.gradle.kts
find docs -maxdepth 3 -type f | sort
```

키, cleartext, backup, 캐시, 위치, 브랜드 계약을 설명하면 관련 테스트를 고른다.

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

### 3. README, 릴리스, 성능

실행 결과나 측정값을 말하면 diff check에 더해 그 명령을 실행하거나 기존 증거를 적는다.

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md docs/deployment.md docs/performance.md docs/verification-matrix.md docs/release-notes/*.md
```

- README 명령을 바꿨으면 그 명령이나 더 좁은 관련 명령을 돌린다.
- demo story나 screenshot을 바꿨으면 `:app:assembleDemoDebug` 또는 관련 UI test를 본다.
- 배포 절차는 `docs/deployment.md`와 이 문서의 릴리스 절을 같이 본다.
- 성능 숫자는 `docs/performance.md`와 이 문서의 Hero Benchmark Evidence를 같이 본다.

## 빠른 로컬 확인

문서/리팩터링/가벼운 변경 후 가장 먼저 돌릴 조합입니다.

<!-- command-owner: verification.fast -->

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

## Station data correctness 집중 회귀

typed transport/retry owner, safe diagnostics, atomic bucket observation, time-driven freshness, latest-started persistence, exported Room schema를 함께 바꿨거나 live 계약으로 설명할 때는 다음 focused regression을 실행합니다.

<!-- command-owner: verification.data -->

```bash
./gradlew \
  :domain:station:test \
  :core:network:test \
  :core:observability:test \
  :core:database:testDebugUnitTest \
  :core:database:compileDebugAndroidTestKotlin \
  :core:database:mergeDebugAndroidTestAssets \
  :core:location:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  verifyModuleBoundaries \
  --warning-mode fail

scripts/agent/verify-room-schemas.sh
for version in 1 2 3 4 5; do
  cmp \
    "core/database/schemas/com.gasstation.core.database.GasStationDatabase/$version.json" \
    "core/database/build/intermediates/assets/debugAndroidTest/mergeDebugAndroidTestAssets/com.gasstation.core.database.GasStationDatabase/$version.json"
done
PYTHONDONTWRITEBYTECODE=1 scripts/agent/test.sh
PYTHONDONTWRITEBYTECODE=1 scripts/agent/verify.sh docs
python3 scripts/docs/validate.py --check-gradle-tasks
git diff --check
```

`verify-room-schemas.sh`는 versions 1–5 canonical artifact를 검사한 뒤 별도 temporary output으로 current v5를 강제 생성해 byte-compare합니다. `compileDebugAndroidTestKotlin`은 instrumented `MigrationTestHelper` source를 컴파일하고, `mergeDebugAndroidTestAssets`와 이어지는 `cmp` loop가 versions 1–5의 packaged asset을 canonical JSON과 byte 단위로 대조합니다. 어느 것도 device 실행은 아닙니다. target이 연결돼 있지 않다면 `connectedDebugAndroidTest`를 실행했다고 쓰지 말고 host/compile/merged-asset evidence와 미실행 사유를 남깁니다.

이 변경이 branch final HEAD라면 위 결과 뒤 `scripts/agent/verify.sh auto`를 실행합니다. known 2GiB parallel benchmark OOM이 재현될 때만 문서화된 process-level resource control로 재시도하며 repository memory policy는 이 문서 변경만으로 바꾸지 않습니다.

## Kotlin 및 convention 정책

compiler/Test/Roborazzi convention 자체의 TestKit gate와 현재 strict module compile gate는 다음 명령이 소유합니다.

```bash
./gradlew :build-logic:convention:test --no-configuration-cache --warning-mode fail
./gradlew \
  :domain:station:compileKotlin \
  :domain:location:compileKotlin \
  :domain:settings:compileKotlin \
  :core:model:compileKotlin \
  :core:observability:compileKotlin \
  --warning-mode fail
```

이 convention command는 [테스트 전략](test-strategy.md#build-input-integrity와-reproducibility-ownership)에 정의한 단일 `Test` task의 52-owner/90-test, five-fork, nested `--max-workers=2`, no-filter/no-retry 계약을 그대로 실행합니다. Outer suite는 configuration cache를 끄고 hosted main CI에서 확인된 27분/35분 timeout을 넘길 수 있도록 50분 blocking timeout을 사용하며, nested TestKit module `Test` timeout은 15분입니다.

`gasstation.kotlinWarningsAsErrors`의 유효값과 effective policy는 다음과 같습니다.

| convention-owned module | 속성 생략 | `false` | `true` |
| --- | --- | --- | --- |
| `domain:*`, `core:model`, `core:observability` | warning blocking | warning blocking | warning blocking |
| 그 밖의 모듈 | report-only | report-only | warning blocking |

`gasstation.includeRoborazziInUnitTests`와 task selection은 다음 정책을 사용합니다.

| 실행 경로 | 속성 생략 / `false` | `true` |
| --- | --- | --- |
| 일반 unit-test task | `Roborazzi*Test` 제외 | `Roborazzi*Test` 포함 |
| 해당 프로젝트의 정확한 `recordRoborazzi*`, `verifyRoborazzi*`, `compareRoborazzi*`, `verifyAndRecordRoborazzi*` lifecycle | `Roborazzi*Test` 포함 | `Roborazzi*Test` 포함 |

두 속성 모두 정확한 소문자 `true`/`false`만 허용하며 다른 대소문자, 앞뒤 공백, 오타, 빈 할당은 configuration failure입니다. Kotlin compile target은 JVM 17이고 application/library/JVM convention-owned `Test` task timeout은 15분이며 retry는 없습니다.

## Station-list 상태 동시성 집중 회귀

<!-- command-owner: station-state-concurrency -->

location generation, observation recovery, latest watch intent, acknowledged FIFO command, refresh work identity, 순수 projection 또는 얇은 ViewModel 조합을 변경했을 때의 canonical regression입니다.

```bash
./gradlew \
  :domain:station:test \
  :core:database:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyRoborazziDebug \
  verifyModuleBoundaries \
  --warning-mode fail
```

이 명령의 증거 범위는 host coroutine 상태/동시성, Room/Robolectric, demo/prod app graph, screenshot, module-edge 회귀입니다. 연결된 기기나 에뮬레이터 실행을 증명하지 않습니다. opt-in connected 검증은 기존 전용 owner section에서 별도로 선택합니다.

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)

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

### Refined launcher and static splash droplet

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :app:assembleDemoRelease \
  :app:assembleProdRelease \
  --warning-mode fail
```

API 30과 API 37 emulator에서 cold launch를 녹화하고 기본 animator scale과 0배를 각각 확인합니다. 두 API 모두 shared `ic_brand_drop`을 정적으로 표시하며, API 37은 v31 animated override를 두지 않습니다. 기본 scale은 기존 180ms app-owned exit로 콘텐츠에 연결되고 animations-off는 custom exit residue 없이 즉시 제거되어야 합니다. 최종 evidence는 `app/build/reports/app-icon-refinement/app-icon-api{30,37}-scale{0,1}.mp4`, 같은 이름의 contact sheet, 독립 PNG burst, `launcher-api30.png`, `launcher-api37.png`, `launcher-api37-themed-home.png`가 소유합니다. Adaptive/legacy mask와 themed monochrome에서 물방울이 중앙에 있고 잘리지 않으며 식별 가능해야 합니다.

Startup gate는 같은 cold-boot API 37-only 세션에서 pre-runtime commit과 최종 static HEAD를 각각 정확히 10회 측정한 matched pair를 사용합니다. `startupToFirstContent` median의 initial/full 어느 하나도 10%보다 느려지면 실패입니다. 최종 evidence는 `/tmp/gasstation-app-icon-before.json`과 `/tmp/gasstation-app-icon-after.json`이며, 2026-07-28 matched result는 initial 529.93ms → 432.01ms(-18.48%), full 721.12ms → 595.50ms(-17.42%)로 통과했습니다. 첫 비대응 측정은 외부 host 부하와 부팅 세션 차이로 분산이 커졌으므로 gate 판정에 사용하지 않고 별도 진단 JSON으로 보존합니다.

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

## Permission parity와 explicit-action 집중 회귀

`demo`와 `prod`의 공통 permission state machine, denied-first gate, demo fixed-coordinate 공급 시점, permission/GPS 안내 분리를 확인합니다.

```bash
./gradlew \
  :domain:location:test \
  :core:location:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:station-list:verifyRoborazziDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  --warning-mode fail
```

권한 controller까지 확인할 수 있는 연결된 기기 또는 에뮬레이터에서는 focused demo class를 실행합니다. 이 class는 Compose semantics와 UI Automator를 함께 사용해 denied first entry의 자동 dialog 부재, explicit deny 후 guidance 유지, explicit grant 뒤 deterministic demo 목록을 확인합니다. Android Test Orchestrator가 test별 target data를 지워 다른 instrumentation class의 권한 grant가 섞이지 않게 합니다.

```bash
ANDROID_SERIAL=<connected-serial> ./gradlew :app:connectedDemoDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.DemoPermissionFlowTest \
  --warning-mode fail
```

연결된 target이 없으면 이 command는 실행하지 않고 host/flavor 검증 결과와 미실행 사유를 남깁니다. 이 focused run은 physical-device evidence가 아니며 live `prod`, Opinet 조회, OEM별 permission-controller UI를 보장하지 않습니다. 수동 runtime revocation/relaunch smoke가 필요하면 테스트 외부에서 fine/coarse permission revoke -> app force-stop -> relaunch -> guidance와 no-nearby-content를 확인하고 기기/OS 결과를 별도 기록합니다. 이 수동 절차나 connected class는 terminal-denial request-count가 cold launch 또는 프로세스 재시작 뒤에도 유지된다는 것을 증명하지 않습니다.

## Watchlist 유종과 외부 지도 설정 집중 회귀

선택 유종만 쓰는 watchlist cache/history, 가격 없는 저장 identity 유지, KakaoMap legacy migration, provider package/URI/fallback을 host에서 확인합니다.

```bash
./gradlew \
  :core:model:test \
  :core:datastore:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :domain:settings:test \
  :domain:station:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  --warning-mode fail
```

연결된 demo target에서는 운영 `ExternalMapModule`을 기록 launcher로 교체한 `StationPortfolioFlowTest`가 설정 유종의 관심 화면 소비와 설정 지도 provider의 Nearby handoff 소비를 검증합니다.

```bash
./gradlew :app:compileDemoDebugAndroidTestKotlin --warning-mode fail
ANDROID_SERIAL=<connected-serial> ./gradlew :app:connectedDemoDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.StationPortfolioFlowTest \
  --warning-mode fail
```

TMAP 목적지 화면을 직접 확인하려면 먼저 target에 package가 있는지 확인합니다.

```bash
ADB="$ANDROID_HOME/platform-tools/adb"
"$ADB" -s <connected-serial> shell pm list packages com.skt.tmap.ku
```

패키지가 없으면 설치하지 않고 TMAP 실제 목적지 화면은 미검증으로 기록합니다. Host unit test는 계속 package/URI 직렬화와 route -> Play Store app URI -> HTTPS Store fallback을 보호하지만, connected demo는 기록 launcher를 사용하므로 외부 앱 화면 자체를 증명하지 않습니다. `assembleProdDebug`도 live Opinet 또는 prod 기기 handoff를 증명하지 않습니다.

Screenshot 골든을 의도적으로 갱신할 때는 영향 모듈을 명시해 record한 뒤 같은 모듈을 verify합니다.

```bash
./gradlew \
  :core:designsystem:recordRoborazziDebug \
  :feature:station-list:recordRoborazziDebug \
  :feature:settings:recordRoborazziDebug \
  :feature:watchlist:recordRoborazziDebug

./gradlew \
  :core:designsystem:verifyRoborazziDebug \
  :feature:station-list:verifyRoborazziDebug \
  :feature:settings:verifyRoborazziDebug \
  :feature:watchlist:verifyRoborazziDebug
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
  coverageXmlReport verifyCoverageReport \
  -Pgasstation.coverageSourceCommit="$(git rev-parse HEAD)" \
  -Pgasstation.coverageEvent=local \
  :app:assembleProdRelease
```

## Android Lint 분리 경로

production과 test-source Android Lint는 서로 다른 blocking CI job과 artifact로 검증합니다. 두 명령 모두 demo/prod app flavor와 root Android-library `lint`를 명시하고, `--continue`는 모든 실패를 수집할 뿐 실패 exit를 성공으로 바꾸지 않습니다. `--warning-mode fail`은 Gradle warning 정책이며 Android Lint warning 승격은 convention의 `warningsAsErrors=true`가 담당합니다.

```bash
# production source: CI static-analysis와 동일
./gradlew \
  spotlessCheck \
  :app:lintDemoDebug \
  :app:lintProdDebug \
  lint \
  verifyModuleBoundaries \
  verifyNoDeprecatedComposeTestApis \
  verifyCiRobolectricRuntime \
  -Pgasstation.lintTestSources=false \
  --warning-mode fail \
  --continue

# unit/instrumented test source 포함: CI lint-tests와 동일
./gradlew \
  :app:lintDemoDebug \
  :app:lintProdDebug \
  lint \
  -Pgasstation.lintTestSources=true \
  --warning-mode fail \
  --continue
```

각 job은 성공/실패와 무관하게 `**/build/reports/lint-results-*`를 올립니다. artifact 이름은 각각 `lint-production-reports`, `lint-test-source-reports`이고 XML/text/HTML/SARIF를 포함합니다. 두 job 모두 warning까지 blocking이며 `main` Android CI에서 실행됩니다. JVM-only `gasstation.jvm.library` 모듈과 `benchmark`는 이 Android Lint 경로가 커버한다고 주장하지 않습니다.

## 기본 Fast Path와 Opt-in 확장

기본 lint 명령은 production source 중심으로 돌고, test source lint는 위 분리 경로처럼 `-Pgasstation.lintTestSources=true`로 명시합니다. 이 속성은 정확한 `true`/`false`만 허용합니다.

기본 unit-test 명령은 Roborazzi screenshot class를 제외합니다. Screenshot 회귀는 `verifyRoborazziDebug`가 소유합니다.

Compose compiler report와 metric은 기본 생성하지 않습니다. 분석이 필요할 때만 명시적으로 켭니다.

```bash
./gradlew :app:lintDemoDebug :app:lintProdDebug lint -Pgasstation.lintTestSources=true --warning-mode fail --continue
./gradlew :core:designsystem:testDebugUnitTest -Pgasstation.includeRoborazziInUnitTests=true
./gradlew :feature:station-list:compileDebugKotlin -Pgasstation.composeCompilerReports=true
```

## 검증 깊이 측정

JVM mutation은 아래의 `Sealed JVM mutation verification` 절에 있는 canonical runner로만 실행합니다. plugin-created `pitest` task 직접 호출은 guard가 거부합니다. 최종 blocking commit에서는 station 45/location 75 floor와 settings integrity/no-coverage 판정을 수행하고 tag release prerequisite로 동작합니다.

의존성 신선도는 `.github/dependabot.yml`이 Gradle과 GitHub Actions 생태계를 매주 확인해 그룹 PR로 보고합니다. Gradle Wrapper는 distribution URL과 `distributionSha256Sum`, wrapper JAR, `config/quality/build-inputs.json`을 함께 검토해야 하므로 그룹에서 제외합니다. 로컬 `dependencyUpdates` 태스크는 최신 플러그인도 Gradle 10에서 제거될 `Task.project` API를 실행하므로 제거했습니다.

## Production dependency and public ABI verification

```text
:core:model:checkKotlinAbi
:core:observability:checkKotlinAbi
:domain:location:checkKotlinAbi
:domain:settings:checkKotlinAbi
:domain:station:checkKotlinAbi
verifyPublicApiBoundaries
verifyModuleBoundaries
productionDependencyInventory
```

직접 모듈/외부 의존성 경계, 다섯 공개 ABI baseline/compiled surface, Compose v1 test API, CI Java/Robolectric 호환성 가드는 CI `static-analysis`의 차단형 게이트입니다. `verifyPublicApiBoundaries`는 `config/quality/public-api-signatures.txt`의 exact reviewed Signature expectation도 입력으로 검증하고 report에 policy SHA-256과 expectation을 기록합니다. Resolved graph는 명시적으로 실행하고 보관하는 보고 전용 evidence입니다.

```bash
# 다섯 baseline과 compiled public surface, exact direct scope, resolved graph를 검증/기록한다.
./gradlew \
  :core:model:checkKotlinAbi \
  :core:observability:checkKotlinAbi \
  :domain:location:checkKotlinAbi \
  :domain:settings:checkKotlinAbi \
  :domain:station:checkKotlinAbi \
  verifyPublicApiBoundaries \
  verifyModuleBoundaries \
  productionDependencyInventory \
  --warning-mode fail

./gradlew verifyNoDeprecatedComposeTestApis
./gradlew verifyCiRobolectricRuntime
```

## Quality report upload paths

```text
build/reports/quality/module-boundaries.json
build/reports/quality/production-dependency-graph.json
build/reports/quality/public-api-boundaries.json
```

## ABI baseline operator mutation, not verification

```text
:core:model:updateKotlinAbi
:core:observability:updateKotlinAbi
:domain:location:updateKotlinAbi
:domain:settings:updateKotlinAbi
:domain:station:updateKotlinAbi
```

### ABI baseline 운영자 갱신 (검증 명령 아님)

아래 명령은 reviewed source HEAD에서 공개 계약을 의도적으로 바꿀 때 운영자가 한 번 실행해 다섯 diff, UTF-8/LF byte count와 SHA-256을 검토하는 baseline mutation입니다. CI, `check`, agent script나 다른 verification task에 연결하지 않습니다. 각 `updateKotlinAbi` 경로를 CLI에 직접 나열한 운영자 호출만 허용되며, aggregate나 다른 task가 updater를 끌어오면 즉시 실패합니다.

```bash
./gradlew \
  :core:model:updateKotlinAbi \
  :core:observability:updateKotlinAbi \
  :domain:location:updateKotlinAbi \
  :domain:settings:updateKotlinAbi \
  :domain:station:updateKotlinAbi \
  --warning-mode fail
```

> `coverageXmlReport`는 JaCoCo 0.8.15로 settings의 18개 명시 모듈 중 reviewed `benchmark` 제외를 적용하고 JVM/Android/app demo·prod 보고서 18개와 provenance manifest를 생성합니다. `verifyCoverageReport`는 authored source, exact production/test Git blob, exact-one class/exec/XML raw identity, Kotlin/Python full structural semantic identity, attributable denominator, 20개 coverage unit floor와 changed 8000/7000bp를 검증하고 항상 다시 실행됩니다. CI는 두 lifecycle을 하나의 차단형 호출로 실행합니다. Kover는 0.9.8의 미해결 Gradle 10 deprecation 때문에 제거했습니다.

```bash
./gradlew coverageXmlReport verifyCoverageReport \
  -Pgasstation.coverageSourceCommit="$(git rev-parse HEAD)" \
  -Pgasstation.coverageEvent=local \
  --warning-mode fail
```

PR과 local changed 판정에는 `-Pgasstation.coverageBaseRef=<40-hex-base>`를 추가합니다. PR은 base가 없거나 유효하지 않으면 실패하고, main의 unavailable/zero before와 tag/local의 생략 base는 changed coverage를 `N/A`로 두되 provenance·floor·baseline ratchet은 계속 판정합니다. Evidence 위치는 root index/summary와 `**/build/reports/coverage/*/{manifest-entry.json,report.xml}`입니다.

Baseline 교체는 architecture commit의 exact 40-hex HEAD에서 새 evidence를 생성한 뒤 `verify_coverage.py capture --predecessor-commit <same HEAD>`로 수행합니다. 새 baseline은 그 HEAD의 기존 baseline/policy blob hash를 고정하며 ancestry와 policy lineage를 검증하고 floor 감소 또는 200bp 초과 인상을 거부합니다. 첫 baseline만 predecessor가 없습니다.

## CI 연결

GitHub Actions는 PR 피드백 시간을 줄이기 위해 PR과 release 성격의 push를 다르게 검증합니다. 자세한 내용은 `.github/workflows/android.yml`을 참고합니다.

| Trigger | 실행 범위 |
| --- | --- |
| `pull_request` | `agent-contracts` (agent contract tests + full checker), blocking `static-analysis` (demo/prod production lint + root Android-library lint + contract guards + convention TestKit), blocking `lint-tests` (동일 lint surface + test source), `unit-tests` (전 모듈 단위 테스트 + demo instrumentation test 컴파일), `screenshot-tests` (verifyRoborazziDebug), `assemble` (demo/prod debug + benchmark), 독립 blocking `coverage` (report + ratchet) |
| `push` to `main` | PR 범위(`agent-contracts` 포함) + `release-assemble` (독립 2회 빌드로 검증된 unsigned APK와 source-bound receipt를 그대로 바인딩); coverage는 full-history에서 main before 기준 evidence 생성 |
| `push` tag `v*` | main 범위 + demo/prod release artifact 보관 + 모든 선행 job 성공 뒤 `release-publish`가 GitHub Release, demo debug APK, unsigned prod release APK, `SHA256SUMS.txt` 게시 |

`prodRelease` assemble은 기본 PR matrix에 포함하지 않습니다. Coverage는 PR/main/tag 모두에서 독립 실행되어 unit-tests와 병렬로 report와 ratchet evidence를 차단형으로 검증합니다.
`assemble` job은 GitHub runner의 메모리 피크를 낮추기 위해 demo debug, prod debug, benchmark assemble을 별도 Gradle 호출로 실행합니다.
저장소 기본 workflow 권한은 read-only이며 `contents: write`는 tag-only `release-publish`에만 둡니다. `scripts/agent/check-contracts.sh --ci`는 release job이 모든 검증 job을 `needs`로 두고 release note, 다운로드 artifact, GitHub CLI 인증과 APK 게시 경로를 유지하는지 확인합니다.

## 릴리스/배포 확인

새 버전을 발행할 때는 [`docs/deployment.md`](deployment.md)의 절차를 따른 뒤 아래 명령을 최소 확인으로 사용합니다.

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts .github/workflows/android.yml docs/deployment.md docs/test-strategy.md docs/verification-matrix.md docs/release-notes/*.md
scripts/agent/tests/check_contracts_test.sh
scripts/agent/check-contracts.sh
scripts/agent/verify.sh release
```

로컬 산출물은 아래 경로에 각각 하나의 APK가 생성되는지 확인합니다. prod release는 서명되지 않은 R8/minify 산출물입니다.

```bash
find app/build/outputs/apk/demo/debug -maxdepth 1 -type f -name "*.apk" -print
find app/build/outputs/apk/prod/release -maxdepth 1 -type f -name "*.apk" -print
```

Tag push 뒤에는 tag workflow와 GitHub Release가 같은 tag를 가리키고, 세 자산이 게시됐는지 확인합니다.

```bash
gh run list --workflow android.yml --branch vX.Y.Z --limit 1
gh run watch <tag-run-id> --exit-status
gh release view vX.Y.Z --json tagName,url,assets
gh release download vX.Y.Z --pattern "GasStation-*.apk" --pattern SHA256SUMS.txt --dir <empty-directory>
(cd <empty-directory> && sha256sum -c SHA256SUMS.txt)
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

## Sealed JVM mutation verification

`verifyPitestConfiguration`은 생성된 route와 route receipt를 입력으로 요구하므로 독립 실행하지 않습니다. 로컬 실행과 판정은 아래 한 entry만 사용합니다. 이것이 route, route receipt, configuration gate, attempt, canonical Gradle, completion, strict XML measurement, observation/verification summary와 final receipt를 순서대로 소유합니다. baseline 갱신용 `--capture-kind`는 수동 검토 때만 추가하며 ordinary CI/agent에서는 사용하지 않습니다.

```bash
/usr/bin/env -i \
  GASSTATION_PITEST_BOOTSTRAP=sealed-v1 \
  LANG=C LC_ALL=C TZ=UTC TERM=dumb CI=true \
  /bin/bash --noprofile --norc \
  scripts/quality/run_pitest.sh \
  --event local-all --java-home "$JAVA_HOME"
```

최종 blocking phase의 동일 command는 `verify`와 `build/reports/pitest/verification-receipt.json`까지 요구합니다. `observe`는 blocking-phase configuration을 명시적으로 거부합니다. PR은 `--event pull-request --base <immutable-base-sha>`로 변경된 domain module만 선택하고, main/tag/schedule/local-all은 세 모듈을 모두 선택합니다.

주요 증거 경로:

- `build/reports/pitest/route.json`, `route-receipt.json`, `attempt.json`, `completion.json`
- `build/reports/pitest/measurement.json`, `verification-summary.json`, blocking phase의 `verification-receipt.json`
- `domain/*/build/reports/quality/pitest-configuration.json`
- `domain/*/build/reports/pitest/mutations.xml`과 HTML report
- `config/quality/mutation-baseline.json`과 `config/quality/mutation-captures/<candidate-sha256>.json`

Canonical Gradle flags는 configuration cache, configuration-cache-problems fail, no build cache, rerun tasks, no parallel, warning-mode fail입니다. 모듈은 순차 실행하고 PIT만 module당 2 thread를 사용하며 workflow timeout은 60분입니다. retry, `--continue`, exclusion, history input/output, dry-run은 없습니다. configuration-cache 검증은 격리된 project/user cache에서 동일 command를 두 번 실행해 첫 run 저장과 둘째 run 재사용을 모두 확인합니다.

Actions의 primary와 weekly job은 `ubuntu-24.04`만 허용하며 `Linux/x86_64`, `ImageOS=ubuntu24`, 검토된 pin `ImageVersion=20260816.277.1`(runner-images tag `ubuntu24/20260816.277`, commit `3b5f596ffecb076aa5f3c3ded95b145f6daeb016`, `internal.ubuntu24.json` SHA-256 `35b3696018cc49cc1b307943091be1578a18771ee3e375632495d3a027216f19`)을 gate합니다. 후속 `20260823.283.1`은 `config/quality/linux-runner-image-successors.json`으로 recapture 없이 허용합니다. Python locator `/usr/bin/python3 -> python3.12`를 확인한 뒤 canonical `/usr/bin/python3.12`만 실행합니다. Linux tool full numeric mode와 content/version hash는 매 실행 receipt에 관측하며 고정 executable-byte claim이 아닙니다. Darwin은 별도의 reviewed fixed-hash profile입니다.

두 workflow는 top-level `CI_JAVA_VERSION: "21"`을 정확히 한 번 선언하고, `mutation_java`를 포함한 모든 `actions/setup-java@v5`가 정확히 `java-version: ${{ env.CI_JAVA_VERSION }}`를 사용합니다. Android workflow의 공통 Java/Robolectric 관계는 `verifyCiRobolectricRuntime`가 검사하고, mutation structural checker는 primary/weekly 양쪽의 top-level 값·canonical reference·중복 및 job/step shadow 금지를 검사합니다. 이 값은 설치할 Java major를 선택할 뿐 Java-home 경로를 운반하지 않습니다.

`actions/setup-java@v5`의 `steps.mutation_java.outputs.path`는 env로 전달하지 않습니다. sanitized custom shell에서 mode 077 directory를 만들고 `set -C`로 `build/quality/pitest-runtime/bootstrap/java-home.selector`를 한 번 생성합니다. selector는 0600 이하 regular non-symlink 단일 line인지 검증한 뒤 삭제되며, mutation run step은 `GASSTATION_PITEST_BOOTSTRAP=sealed-v1`을 포함한 absolute `/usr/bin/env -i` → `/bin/bash --noprofile --norc -euo pipefail {0}` shell을 사용합니다. pre-existing/retargeted/중복 selector, workflow/job/step env Java transport, PATH-selected Python/Git/Gradle, hostile JVM/Gradle/Git/Python environment는 fail closed입니다.

Hosted execution, artifact upload, image availability는 로컬에서 검증했다고 주장하지 않습니다. image release가 바뀌면 실행을 멈추고 reviewed profile/recapture transition을 갱신합니다. runner-images inventory metadata와 runtime-observed hashes는 signed VM/binary attestation이 아니며 최종 supply-chain pin은 Task 9가 소유합니다.

## Build input provenance와 unsigned release 재현성

운영 설명과 갱신 순서는 [Build Input Provenance](runbooks/build-input-provenance.md)가 소유한다. 아래 명령만 runnable matrix를 소유한다. Linux x64 evidence host는 정책이 설치한 exact Temurin 17/21과 fresh `GRADLE_USER_HOME`을 사용하며 raw developer Gradle 실행은 accepted receipt가 아니다.

정적 정책·action closure·wrapper/JDK/SDK identity와 dynamic dependency version 검사:

```bash
python3 scripts/quality/verify_build_inputs.py verify \
  --policy config/quality/build-inputs.json
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s scripts/quality/tests -p 'test_build_inputs.py' -v
scripts/agent/check-contracts.sh --ci
```

이 샘플은 dependency verification metadata와 별도 capture/strict matrix를 운영하지 않는다. 대신 동일한 격리 환경에서 configuration-cache 저장/재사용을 확인하고, 일반 CI build/test/lint가 dependency 호환성을 검증한다.

```bash
python3 scripts/quality/verify_build_inputs.py configuration-cache \
  --policy config/quality/build-inputs.json
```

Governed docs entry는 stable bridge 하나뿐이다.

```bash
python3 scripts/quality/build_inputs/docs_gradle_validation_bridge.py \
  --check-gradle-tasks
```

Evidence session allowlist는 다음 네 argv와 정확히 같아야 하며 suffix나 fifth command는 거부한다.

```bash
python3 scripts/quality/verify_build_inputs.py evidence-session --policy config/quality/build-inputs.json -- python3 scripts/quality/build_inputs/docs_gradle_validation_bridge.py --check-gradle-tasks
python3 scripts/quality/verify_build_inputs.py evidence-session --policy config/quality/build-inputs.json -- scripts/agent/verify-room-schemas.sh
python3 scripts/quality/verify_build_inputs.py evidence-session --policy config/quality/build-inputs.json -- scripts/agent/verify.sh auto
python3 scripts/quality/verify_build_inputs.py evidence-session --policy config/quality/build-inputs.json -- scripts/agent/verify.sh docs
```

Clean source의 unsigned prod release two-copy probe와 receipt capture:

```bash
python3 scripts/quality/verify_build_inputs.py reproduce \
  --policy config/quality/build-inputs.json \
  --source-commit <source-commit> \
  --output build/reports/build-inputs/reproducible-build.json
python3 scripts/quality/verify_build_inputs.py capture \
  --policy config/quality/build-inputs.json \
  --source-commit <source-commit> \
  --evidence build/reports/build-inputs/reproducible-build.json \
  --output build/reports/build-inputs/build-input-receipt.json
```

Workflow blocking phase는 위 strict matrix, configuration-cache, probe, capture를 `ubuntu-24.04`의 독립 `build-inputs` job에서 실패 완화 없이 실행한다. `release-assemble`과 `release-publish`는 source-bound receipt를 필수 prerequisite로 내려받고 APK upload/checksum/release mutation 전에 release binding을 통과해야 한다.

Release candidate는 upload/publish 전에 source-bound probe receipt와 다시 묶는다.

```bash
python3 scripts/quality/verify_build_inputs.py release-bind \
  --policy config/quality/build-inputs.json \
  --receipt <reproducible-prod-release-receipt.json> \
  --apk <GasStation-X.Y.Z-prod-release-unsigned.apk> \
  --source-commit <source-commit> \
  --artifact-name reproducible-prod-release-receipt-<source-commit>
```

동일한 size/SHA-256만 same-host/workspace-independent unsigned prod-release 재현성 `PASS`다. demo-debug, signed APK, cross-OS/runner 재현성은 이 판정에 포함하지 않는다. Hosted build-input evidence와 Task 8 device lane을 실행하지 않았으면 각각 `NOT RUN`으로 남긴다.

현재 macOS arm64 controller에서 필수 Linux/amd64 package를 만들 때는 아래 하나의 closed entrypoint만 사용한다. 이 명령은 clean HEAD, literal local main base, 전용 VZ+Rosetta profile/context, mount 없는 two-ref bundle clone, exact Ubuntu/Android/JDK inputs와 ordered `--data --force` cleanup을 내부에서 모두 검사한다. child row를 직접 실행하거나 profile/context/attempt를 선택하면 accepted evidence가 아니다.

Entrypoint는 profile 생성 전에 host의 logical CPU, physical CPU, physical memory를 fixed `sysctl` argv로 다시 관측하고 정책 최소 14/14/`51539607552`와 비교한다. 전용 profile의 literal argv와 persisted config는 14 vCPU/32 GiB여야 하고 default profile은 입력이나 증거가 아니다. 보존된 historical Metadata TestKit failure package는 당시의 timeout과 five-fork/90-test contract, canonical redacted JUnit suite/case timing, worker, exception/nested-log owner와 hash를 담은 원인 분석 증거일 뿐 현재 Linux aggregate `PASS`가 아니다.

Image gate는 index/selected-manifest/config/sole-layer descriptor를 서로 다른 역할로 검사한다. `image inspect`는 Docker 29.2.1 containerd store의 exact familiar singleton `RepoDigests=ubuntu@sha256:…`를 포함한 store 관측값을 요구하며, 이를 full pull name으로 정규화하면 실패한다. 이 index/store 관측은 `manifest inspect --verbose`에서 선택한 exact `linux/amd64` manifest 및 그 config/layer를 대신할 수 없다. Container는 selected manifest digest가 아니라 exact index reference로 `--platform linux/amd64` 생성한다. Start 전 container inspect는 별도로 `.Image=index digest`, `.Config.Image=full index reference`, `.Platform=linux`, marker-bound owned label 6개와 inherited Ubuntu version label 1개를 요구한다. Volume은 inherited label 없이 owned 6개만 허용한다. Start 후 exact `uname=x86_64`와 `dpkg=amd64`를 별도 검사하며, OCI config identity는 manifest descriptor에서만 증명한다.

```bash
python3 scripts/quality/build_inputs/local_colima_evidence.py \
  --policy config/quality/build-inputs.json \
  --source-commit "$(git rev-parse HEAD)"
```

성공 시 `build/reports/build-inputs/local-linux-host.json`과 `build/reports/build-inputs/local-linux-evidence-package.json`이 같은 source/policy/attempt를 가져야 한다. 이 결과는 `local-colima-vz-rosetta-emulated-linux-amd64`이며 hosted/native-x64/cross-host/hermetic 또는 device evidence가 아니다.

현재 `4173dd05...`에서 정규 entrypoint는 inherited `SSH_AUTH_SOCK` 금지로 attempt allocation 전에 exit 2를 반환했습니다. 판정은 `INFRASTRUCTURE PREFLIGHT / NOT_MEASURED`, attempt 0이며 코드 `FAIL`도 `PASS`도 아닙니다. Linux 90/90, package seal, recovery, cleanup, default-Colima noninterference는 모두 `NOT_MEASURED`이고 같은 코드 retry는 수행되지 않았으며 승인되지 않았습니다. 경과 시간은 [Build Velocity](build-velocity.md#convention-quality-gate-timing-snapshot)가 소유합니다.

## Bounded Android device evidence

Task 8의 host-only 구현 준비 gate는 실제 device `PASS`와 분리합니다.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/quality/tests -v
scripts/agent/test.sh
scripts/agent/check-contracts.sh --ci
python3 scripts/docs/validate.py --check-gradle-tasks
./gradlew \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :app:compileDemoDebugAndroidTestKotlin \
  :core:database:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin \
  :core:location:testDebugUnitTest :core:location:compileDebugAndroidTestKotlin \
  verifyRoborazziDebug :benchmark:assemble \
  --warning-mode fail
./gradlew :app:tasks :core:database:tasks :core:location:tasks \
  --group verification --warning-mode fail
```

지원 Linux x86_64/KVM 호스트에서 runtime status를 만들 때는 다음 정규 entry만 사용합니다.

```bash
scripts/quality/device/run_gmd_lane.sh --lane api28-pr-smoke
scripts/quality/device/run_api24_avd.sh --lane api24-scheduled
scripts/quality/device/run_gmd_lane.sh --lane api28-scheduled
scripts/quality/device/run_gmd_lane.sh --lane api36-scheduled
```

산출물은 `build/device-evidence/<lane>/<run-id>-<attempt>/`, 각 module의 `build/outputs/androidTest-results/{managedDevice,connected}`, `build/reports/androidTests/{managedDevice,connected}`, 정책에 열거한 APK root에서 확인합니다. 정확한 identity/count, raw-derived API/image/serial/ABI/fingerprint/locale/permission package revision, observed cleanup, hash가 결합된 `verification.json`만 runtime `PASS`를 만들 수 있습니다. 실제 test command nonzero는 수집된 JUnit failure와 정확한 failure PNG/diagnostic을 끝까지 검증한 구조화된 `FAIL`만 만들 수 있고, 누락 output은 collection failure입니다. host compile, task discovery, fixture/parser 성공 또는 artifact 업로드만으로는 `PASS`가 아니며 실행할 수 없으면 이유와 함께 `NOT RUN`입니다. 모든 executable phase와 workflow setup/upload/summary는 정책의 닫힌 timeout 합계 안에서 제한됩니다. 전체 lane/timeout/triage/promotion 계약은 [Android 기기 검증 런북](runbooks/device-verification.md)을 따릅니다.
