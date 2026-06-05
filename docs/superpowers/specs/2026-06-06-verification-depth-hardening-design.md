# GasStation 검증 깊이 고도화 (Verification Depth Hardening) 설계

> 작성일: 2026-06-06
> 기준 커밋: `0bc97b1`
> 범위: 커버리지 진실성·게이트, mutation testing, 의존성 신선도 스캔
> 사용자 플로우 영향: 없음 (주유소 비교/watchlist/외부 지도 handoff 동작 불변)

## 목표

GasStation의 검증을 "숫자가 있는 상태"에서 "숫자가 진실되고 회귀를 막는 상태"로 끌어올린다. 세 가지 독립 트랙으로 구성하며, 각 트랙은 독립적으로 commit 가능한 단위다. 어떤 트랙도 `feature:*`/`domain:*`/`data:*`의 사용자 대면 동작을 바꾸지 않는다.

## 배경: 탐색에서 확인한 사실

2026-06-06 코드 확인 결과:

1. **Kover가 Android 모듈을 계측하지 않는다.** `gasstation.kover` 컨벤션 플러그인은 모든 모듈에 적용돼 있으나, JVM 모듈(`core:model`, `core:network`, `domain:location/settings/station`, `tools:demo-seed`)만 `:test`를 통해 계측된다. Android 모듈(`app`, `feature:*`, `data:*`, `core:database/datastore/designsystem/location`)은 debug variant가 Kover에 연결되지 않아 리포트가 0/0으로 비어 있다.
2. **그 결과 루트 집계가 허수다.** `./gradlew koverXmlReport` 루트 집계는 LINE 총 514줄(83.07%)만 잡으며, 이는 JVM 5개 모듈만 반영한다. 화면 상태·저장소·캐시 정책 로직이 있는 Android 모듈은 분모에 들어가지 않는다. CI `coverage` job이 Codecov에 올리는 숫자도 동일한 한계를 가진다.
3. **벤치마크 코드는 이미 존재한다.** `benchmark/.../StationListBenchmark.kt`에 `openWatchlistFrameTiming`이 이미 구현돼 있다. 남은 것은 물리 기기 측정과 README 행 추가뿐이며, 이는 하드웨어가 필요해 이 작업 범위 밖이다.
4. **JVM-only 모듈만 mutation testing에 적합하다.** Pitest는 Android 모듈에서 불안정하므로 JVM 모듈로 한정한다. `domain:station`이 라인 커버리지 48.57%로 가장 약해 1순위 대상이다.

## 비목표 (Out of Scope)

- 벤치마크 신규 시나리오 구현 (코드가 이미 존재; 실기기 측정은 별도 기기 작업).
- 사용자 대면 기능 추가나 UI 변경.
- mutation 점수·의존성 신선도를 빌드 실패 게이트로 강제하는 것(이번엔 report-only로 시작).
- Pitest를 Android 모듈로 확장하는 것.

---

## Track 1: 진실된 커버리지 측정 + fail-under 게이트

**소유:** `build-logic/convention` (Kover 컨벤션), 루트 `build.gradle.kts`, `.github/workflows/android.yml`

**문제:** 커버리지 게이트를 추가하기 전에 커버리지 자체가 진실되어야 한다. 현재 분모는 Android 모듈을 누락한다.

### 1a. Android variant 계측 연결

- `build-logic/convention/src/main/kotlin/GasStationKoverConventionPlugin.kt`에서 Android 모듈에 대해 Kover 0.9의 variant API로 `debug` 변형을 계측 대상으로 등록한다. 결과적으로 `feature:*`, `data:*`, `core:database/datastore/designsystem/location`, `app`이 `testDebugUnitTest` 실행 기준 실제 커버리지를 보고해야 한다.
- 정확한 Kover 0.9.1 variant API(예: `currentProject`/variant 선언)는 구현 단계에서 context7 또는 Kover 공식 문서로 핀 고정한다. JVM 모듈 동작은 변경하지 않는다.

**완료 기준:** `./gradlew koverXmlReport` 루트 집계 분모가 Android 모듈을 포함해 514줄보다 크게 증가하고, `feature:*`/`data:*` 모듈 리포트가 0/0이 아니다.

### 1b. 진짜 전체 커버리지 재측정

- 1a 적용 후 `koverXmlReport`를 실행해 18개 모듈 전체 기준 실수치(LINE/BRANCH)를 기록한다.
- 이 수치를 게이트 임계값 산정의 근거로 `docs/`에 남긴다.

**완료 기준:** Android 모듈 포함 실수치가 문서에 기록된다.

### 1c. fail-under 게이트

- 루트 `build.gradle.kts`의 `kover { reports { ... } }`에 `verify` 규칙(라인 기준)을 추가한다.
- 임계값은 **1b에서 측정한 실수치보다 3~5%p 낮게** 보수적으로 잡아 비-flaky 마진을 확보한다. 목표는 향상 강제가 아니라 회귀 차단이다.
- 기존 Kover `excludes` 필터(Hilt/Factory/Compose 생성 코드 제외)는 유지한다.

**완료 기준:** `./gradlew koverVerify`가 현재 코드에서 통과하고, 의도적으로 임계값을 높이면 실패한다.

### 1d. CI 배선

- `.github/workflows/android.yml`의 기존 `coverage` job(현재 `main`/`v*` tag push 전용)에 `koverVerify` 실행을 추가한다. PR 게이트로 올리지 않아 PR 빌드 시간은 그대로 유지한다.
- `koverVerify`는 `koverXmlReport` 전/후 어디서 돌든 같은 binary report에 기반하므로 job 내에서 한 번의 Gradle 호출로 묶을 수 있다.

**완료 기준:** `coverage` job이 `koverVerify`를 포함하고 main push에서 통과한다.

**Track 1 검증:**
```bash
./gradlew koverXmlReport koverVerify
```

---

## Track 2: domain:station mutation testing (report-only)

**소유:** `domain:station`, `gradle/libs.versions.toml`, `docs/`

**목적:** 라인 커버리지 숫자만으로는 테스트가 실제 결함을 잡는지 알 수 없다. 가장 약한 JVM 모듈에 변이 테스트를 적용해 테스트의 결함 탐지력을 측정·기록한다.

### 설계

- `gradle-pitest-plugin`을 `domain:station`에만 적용한다(`libs.versions.toml`에 plugin 등록). 기존 테스트가 JUnit4 기반이므로 기본 plugin으로 동작한다.
- **report-only:** mutation 점수 임계값으로 빌드를 깨지 않는다. `./gradlew :domain:station:pitest`로 HTML/XML 리포트를 생성한다.
- **CI에 넣지 않는다.** 변이 테스트는 느리므로 로컬/온디맨드로 둔다. 포트폴리오 신호로는 "측정·기록했다"로 충분하며, 게이트화는 점수 안정화 후 별도 결정한다.
- 변이가 드러낸 약한 테스트(살아남은 mutant)가 있으면 그중 1~2건의 테스트를 보강한다. 보강은 `domain:station`의 기존 동작 계약을 바꾸지 않는 범위로 한정한다.

### 산출물

- `docs/`에 mutation testing 근거 노트: 대상 선정 이유, 실행 명령, `domain:station` 현재 변이 점수, report-only 결정.

**완료 기준:** `./gradlew :domain:station:pitest`가 성공하고 리포트가 생성되며, 변이 점수가 문서에 기록된다. 보강 테스트가 있으면 `:domain:station:test`가 통과한다.

**Track 2 검증:**
```bash
./gradlew :domain:station:pitest
./gradlew :domain:station:test
```

---

## Track 4: 의존성 신선도 스캔 (CI report-only)

**소유:** 루트 `build.gradle.kts` 또는 `build-logic`, `.github/workflows/android.yml`, `gradle/libs.versions.toml`

**목적:** 의존성 유지보수 신호를 CI에 가볍게 추가한다. 보안 CVE 스캔(OWASP)은 NVD 다운로드로 CI가 느리고 불안정하므로 이번엔 채택하지 않는다.

### 설계

- `com.github.ben-manes.versions`(gradle-versions-plugin)를 등록한다(`libs.versions.toml`).
- `./gradlew dependencyUpdates`가 오래된 의존성 리포트를 생성한다.
- CI에 **report-only job**으로 추가한다. job 실패가 빌드를 깨지 않도록 하거나(예: `continue-on-error`) 정보성 단계로 둔다. PR 필수 게이트로 만들지 않는다.
- 안정 채널만 보도록 reject 규칙(예: alpha/beta/rc 후보 제외)을 설정해 노이즈를 줄인다.

**완료 기준:** `./gradlew dependencyUpdates`가 성공하고 리포트를 만들며, CI에 비차단 job으로 존재한다.

**Track 4 검증:**
```bash
./gradlew dependencyUpdates
```

---

## Track 3 처리 (구현 없음, 문서만)

- `openWatchlistFrameTiming`은 코드에 이미 존재한다. `docs/performance.md`가 실기기 측정 대기 상태를 정직하게 유지하도록 점검만 한다. 이번 스펙에서 벤치마크 코드 변경은 없다.

---

## 트랙 간 독립성

세 트랙은 서로 독립적으로 commit 가능하다. Track 1(빌드/CI 커버리지), Track 2(domain:station 테스트), Track 4(CI 의존성 job)를 한 묶음으로 batch하지 않는다. v1.2 hardening과 동일한 "엄브렐러 스펙, 독립 commit" 패턴을 따른다.

## 최종 검증 (전체)

```bash
# Track 1
./gradlew koverXmlReport koverVerify
# Track 2
./gradlew :domain:station:pitest :domain:station:test
# Track 4
./gradlew dependencyUpdates
# 회귀 안전망 (기존 fast 경로)
./gradlew spotlessCheck lint \
  :domain:station:test :core:model:test \
  :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

## 문서 갱신 후보

- `docs/verification-matrix.md`: koverVerify 게이트 위치, dependencyUpdates 명령, pitest 온디맨드 명령 추가.
- `docs/test-strategy.md`: 커버리지 진실성(Android variant 계측)·mutation testing 의도 명시.
- `CHANGELOG.md` Unreleased: 검증 깊이 고도화 항목.
- `docs/performance.md`: Track 3 실기기 측정 대기 상태 점검(변경 없을 수 있음).
