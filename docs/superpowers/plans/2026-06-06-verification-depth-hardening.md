# 검증 깊이 고도화 (Verification Depth Hardening) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GasStation 검증을 "숫자가 있는 상태"에서 "테스트의 결함 탐지력과 의존성 신선도까지 측정·기록하는 상태"로 끌어올린다. 사용자 대면 동작은 불변.

**Architecture:** 두 개의 독립 트랙을 각각 별도 commit으로 추가한다. Track 2는 `domain:station`에 mutation testing(pitest)을 report-only/on-demand로 붙인다. Track 4는 의존성 신선도 스캔(ben-manes versions-plugin)을 root에 붙이고 비차단 CI job으로 노출한다. Track 3은 벤치마크 실기기 측정 대기 상태를 문서로만 점검한다. **Track 1(Kover Android 계측+게이트)은 보류** — Kover 0.9.1이 AGP 9.1.1의 Android variant를 계측하지 못하는 툴체인 호환성 한계가 스파이크로 확인됨(설계문서 Track 1 보류 노트 참조). AGP 9.x 지원 Kover 릴리스가 나오기 전까지 이 plan 범위 밖.

**Tech Stack:** Gradle Kotlin DSL, 버전 카탈로그(`gradle/libs.versions.toml`), `info.solidsoft.pitest` 1.19.0, `com.github.ben-manes.versions` 0.54.0, GitHub Actions.

**기준 사실 (구현 전 확인됨):**
- `domain:station`은 `gasstation.jvm.library` 컨벤션을 쓰는 순수 JVM 모듈. 패키지 `com.gasstation.domain.station`. 테스트는 `kotlin.test`(JUnit4 백엔드, `useJUnitPlatform()` 미사용).
- root `build.gradle.kts`는 최상단 `buildscript {}` → `plugins {}`(버전 카탈로그 alias) → `dependencies {}`(kover) → `kover {}` 순서. Kotlin DSL이므로 `import` 문은 파일 최상단(`buildscript` 위)에 둔다.
- `settings.gradle.kts`의 `pluginManagement.repositories`에 `gradlePluginPortal()` 존재 → 플러그인 alias 해석 가능.
- CI(`.github/workflows/android.yml`)는 `static-analysis`/`unit-tests`/`screenshot-tests`/`assemble`/`release-assemble`/`coverage` job 구성. `coverage`만 main/tag push 전용.

---

## Track 4: 의존성 신선도 스캔 (CI report-only)

가장 작고 위험이 없는 트랙. 먼저 구현해 독립 commit한다.

**Files:**
- Modify: `gradle/libs.versions.toml` ( `[plugins]` 에 항목 추가 )
- Modify: `build.gradle.kts` (root — import + plugins alias + DependencyUpdatesTask 설정)
- Modify: `.github/workflows/android.yml` (비차단 job 추가)

### Task 4.1: 버전 카탈로그에 ben-manes versions 플러그인 등록

- [ ] **Step 1: `gradle/libs.versions.toml`의 `[plugins]` 블록에 항목 추가**

`[plugins]` 블록 맨 끝(`kover = ...` 다음 줄)에 추가:

```toml
benManesVersions = { id = "com.github.ben-manes.versions", version = "0.54.0" }
```

- [ ] **Step 2: 카탈로그 파싱 검증**

Run: `./gradlew help --console=plain`
Expected: BUILD SUCCESSFUL (카탈로그 문법 오류 없음).

### Task 4.2: root build에 플러그인 적용 + 안정 채널 reject 규칙

- [ ] **Step 1: `build.gradle.kts` 최상단에 import 추가**

파일 첫 줄(`buildscript {` 위)에 추가:

```kotlin
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
```

- [ ] **Step 2: `plugins {}` 블록에 alias 추가**

기존 `alias(libs.plugins.kover)` 다음 줄에 추가:

```kotlin
    alias(libs.plugins.benManesVersions)
```

- [ ] **Step 3: `dependencies {}`(kover) 블록과 `kover {}` 블록 사이(또는 파일 끝)에 reject 규칙 추가**

```kotlin
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !regex.matches(version)
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isNonStable(candidate.version) }
}
```

- [ ] **Step 4: 설정 검증**

Run: `./gradlew tasks --group help --console=plain`
Expected: BUILD SUCCESSFUL, 그리고 `dependencyUpdates` 태스크가 목록에 보인다.

### Task 4.3: dependencyUpdates 실행 + 리포트 생성 확인

- [ ] **Step 1: 스캔 실행**

Run: `./gradlew dependencyUpdates --console=plain`
Expected: BUILD SUCCESSFUL, 콘솔에 "The following dependencies have later ... versions" 또는 "up to date" 섹션 출력. alpha/beta/rc 후보는 reject 규칙으로 제외됨.

- [ ] **Step 2: 리포트 파일 확인**

Run: `ls build/dependencyUpdates/`
Expected: `report.txt` 존재.

### Task 4.4: 비차단 CI job 추가

- [ ] **Step 1: `.github/workflows/android.yml`에 job 추가**

`coverage:` job 블록 바로 다음(파일 끝)에 추가:

```yaml
  dependency-freshness:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Dependency Updates Report
        # Report-only: 신선도 신호일 뿐 빌드를 깨지 않는다.
        continue-on-error: true
        run: ./gradlew dependencyUpdates
      - name: Upload dependency report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: dependency-updates
          path: build/dependencyUpdates/report.txt
```

- [ ] **Step 2: 워크플로 YAML 문법 검증(로컬)**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/android.yml'))" && echo OK`
Expected: `OK` (파싱 성공).

### Task 4.5: Track 4 commit

- [ ] **Step 1: 회귀 안전망 — 기존 fast 경로가 깨지지 않는지 확인**

Run: `./gradlew spotlessCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts .github/workflows/android.yml
git commit -m "$(cat <<'EOF'
chore: add dependency freshness scan (ben-manes versions, report-only)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

**Track 4 검증:**
```bash
./gradlew dependencyUpdates
```

---

## Track 2: domain:station mutation testing (report-only)

JVM-only 모듈 중 라인 커버리지가 가장 약한 `domain:station`에 변이 테스트를 붙여 테스트의 결함 탐지력을 측정·기록한다. report-only/on-demand이며 CI에 넣지 않는다.

**Files:**
- Modify: `gradle/libs.versions.toml` ( `[plugins]` 에 pitest 추가 )
- Modify: `domain/station/build.gradle.kts` (plugin alias + `pitest {}` 설정)
- Create/Modify: `docs/test-strategy.md` (mutation testing 근거 노트)
- (조건부) Modify: `domain/station/src/test/...` (살아남은 mutant 보강)

### Task 2.1: 버전 카탈로그에 pitest 플러그인 등록

- [ ] **Step 1: `gradle/libs.versions.toml`의 `[plugins]` 블록에 항목 추가**

Task 4.1에서 추가한 `benManesVersions` 다음 줄에 추가:

```toml
pitest = { id = "info.solidsoft.pitest", version = "1.19.0" }
```

- [ ] **Step 2: 카탈로그 파싱 검증**

Run: `./gradlew help --console=plain`
Expected: BUILD SUCCESSFUL.

### Task 2.2: domain:station에 pitest 적용 + 설정

- [ ] **Step 1: `domain/station/build.gradle.kts`의 `plugins {}` 블록에 alias 추가**

```kotlin
plugins {
    id("gasstation.jvm.library")
    alias(libs.plugins.pitest)
}
```

- [ ] **Step 2: 같은 파일 끝(`dependencies {}` 다음)에 `pitest {}` 설정 추가**

```kotlin
pitest {
    targetClasses.set(setOf("com.gasstation.domain.station.*"))
    targetTests.set(setOf("com.gasstation.domain.station.*"))
    threads.set(2)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // report-only: mutationThreshold 게이트를 두지 않는다.
}
```

- [ ] **Step 3: 설정 검증**

Run: `./gradlew :domain:station:tasks --all --console=plain`
Expected: BUILD SUCCESSFUL, `pitest` 태스크가 목록에 보인다.

### Task 2.3: pitest 실행 + 변이 점수 캡처

- [ ] **Step 1: 변이 테스트 실행**

Run: `./gradlew :domain:station:pitest --console=plain`
Expected: BUILD SUCCESSFUL. 콘솔 끝에 `>> Generated N mutations Killed K (X%)` 및 `>> Line Coverage ...` 출력.

  - **만약 `0 tests` / `no mutations` 로 끝나면** (kotlin.test 백엔드 미해석): `domain/station/build.gradle.kts`의 `dependencies {}`에 `testImplementation(libs.junit)` 한 줄을 추가하고 Step 1을 재실행한다. (현재 `unit-tests` CI에서 `:domain:station:test`가 통과하므로 JUnit4 백엔드는 이미 존재 — 이 경우 보통 불필요.)

- [ ] **Step 2: HTML/XML 리포트 생성 확인 + 점수 기록**

Run: `ls domain/station/build/reports/pitest/ && grep -oE 'Killed [0-9]+ \([0-9.]+%\)' domain/station/build/reports/pitest/*.html 2>/dev/null | head -1`
Expected: `index.html`(및 mutations.xml) 존재. 출력된 변이 점수(%)를 다음 Task에서 문서에 기록한다.

### Task 2.4: (조건부) 살아남은 mutant 1~2건 테스트 보강

- [ ] **Step 1: 살아남은 mutant 확인**

`domain/station/build/reports/pitest/index.html`을 열어(또는 `mutations.xml`에서 `status="SURVIVED"` 검색) 살아남은 변이를 1~2건 고른다.

Run: `grep -c 'status="SURVIVED"' domain/station/build/reports/pitest/mutations.xml 2>/dev/null || echo 0`

  - **SURVIVED가 0이면 이 Task 전체를 건너뛴다** (보강 불필요).

- [ ] **Step 2: 살아남은 mutant를 잡는 테스트 추가**

선택한 SURVIVED 변이가 위치한 `com.gasstation.domain.station`의 함수에 대해, 기존 동작 계약을 바꾸지 않는 범위에서 단언을 추가하거나 새 테스트 케이스를 `domain/station/src/test/...`의 해당 테스트 클래스에 추가한다. (구체 코드는 리포트가 가리키는 실제 함수에 따라 결정 — 기존 테스트 스타일(`kotlin.test`, Turbine)을 따른다.)

- [ ] **Step 3: 테스트 통과 확인**

Run: `./gradlew :domain:station:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 변이 점수 개선 재측정**

Run: `./gradlew :domain:station:pitest --console=plain`
Expected: BUILD SUCCESSFUL, 변이 점수가 Task 2.3 대비 동일하거나 상승.

### Task 2.5: mutation testing 근거 문서화

- [ ] **Step 1: `docs/test-strategy.md`에 mutation testing 섹션 추가**

다음 내용을 담는 섹션을 추가(또는 파일이 없으면 생성):
- **대상 선정 이유:** JVM-only 모듈 중 라인 커버리지 최약(48.57%)인 `domain:station`. Pitest는 Android 모듈에서 불안정하므로 JVM 한정.
- **실행 명령:** `./gradlew :domain:station:pitest`
- **현재 변이 점수:** Task 2.3에서 캡처한 실제 % (예: `Killed N (X%)`).
- **report-only 결정:** mutation 점수 임계값으로 빌드를 깨지 않는다. 느리므로 CI 미포함, 로컬/온디맨드. 게이트화는 점수 안정화 후 별도 결정.
- (Task 2.4를 수행했다면) 보강한 테스트 요약.

- [ ] **Step 2: 문서 포맷 검증**

Run: `./gradlew spotlessCheck --console=plain`
Expected: BUILD SUCCESSFUL (스포트리스가 md를 검사하면 통과, 아니면 무관).

### Task 2.6: Track 2 commit

- [ ] **Step 1: commit**

```bash
git add gradle/libs.versions.toml domain/station/build.gradle.kts docs/test-strategy.md
# Task 2.4를 수행했다면 보강한 테스트 파일도 추가:
# git add domain/station/src/test
git commit -m "$(cat <<'EOF'
test: add mutation testing on domain:station (pitest, report-only)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

**Track 2 검증:**
```bash
./gradlew :domain:station:pitest
./gradlew :domain:station:test
```

---

## Track 3: 벤치마크 실기기 측정 대기 상태 점검 (문서만)

`openWatchlistFrameTiming` 벤치마크 코드는 이미 존재한다. 코드 변경 없이 `docs/performance.md`가 실기기 측정 대기 상태를 정직하게 유지하는지 점검만 한다.

**Files:**
- (조건부) Modify: `docs/performance.md`

### Task 3.1: performance.md 정직성 점검

- [ ] **Step 1: 현재 상태 확인**

Run: `grep -nE "openWatchlist|실기기|device|pending|대기|TODO" docs/performance.md`

- [ ] **Step 2: 필요 시 한 줄 보정**

`openWatchlistFrameTiming`이 "코드 존재 / 실기기 측정 대기"로 정직하게 표기돼 있지 않으면, 해당 한 줄만 보정한다. 이미 정직하면 **변경하지 않는다**(이 Task는 no-op로 끝날 수 있음).

- [ ] **Step 3: (변경이 있었다면) commit**

```bash
git add docs/performance.md
git commit -m "$(cat <<'EOF'
docs: clarify openWatchlist benchmark awaits device measurement

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Track 1: 보류 (구현 없음)

Kover 0.9.1 ↔ AGP 9.1.1 호환성 한계로 Android 모듈 계측이 불가하여 보류. 설계문서(`docs/superpowers/specs/2026-06-06-verification-depth-hardening-design.md`)의 Track 1 보류 노트가 재개 조건(AGP 9.x 지원 Kover 릴리스)을 기록한다. 이 plan에서 빌드/CI 변경 없음.

---

## 문서 갱신 (마무리 commit)

**Files:**
- Modify: `docs/verification-matrix.md`
- Modify: `CHANGELOG.md`

### Task D.1: verification-matrix.md 갱신

- [ ] **Step 1: 명령 추가**

`docs/verification-matrix.md`에 다음을 추가:
- `./gradlew dependencyUpdates` — 의존성 신선도 스캔(비차단 CI job `dependency-freshness`).
- `./gradlew :domain:station:pitest` — domain:station 변이 테스트(온디맨드, report-only).
- Track 1(koverVerify 게이트)은 **보류** 상태임을 한 줄로 명시.

### Task D.2: CHANGELOG.md Unreleased 갱신

- [ ] **Step 1: 항목 추가**

`CHANGELOG.md`의 `Unreleased` 섹션에 추가:
- `domain:station` 변이 테스트(pitest, report-only) 추가.
- 의존성 신선도 스캔(ben-manes versions, 비차단 CI) 추가.
- 커버리지 진실성 게이트(Track 1)는 Kover/AGP9 호환성 한계로 보류.

### Task D.3: 문서 commit

- [ ] **Step 1: commit**

```bash
git add docs/verification-matrix.md CHANGELOG.md
git commit -m "$(cat <<'EOF'
docs: record verification depth hardening (pitest, dependency scan)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## 트랙 간 독립성

Track 2(pitest)와 Track 4(versions)는 서로 독립적으로 commit한다. 한 묶음으로 batch하지 않는다. v1.2 hardening과 동일한 "엄브렐러 스펙, 독립 commit" 패턴.

## 최종 검증 (전체)

```bash
# Track 4
./gradlew dependencyUpdates
# Track 2
./gradlew :domain:station:pitest :domain:station:test
# 회귀 안전망 (기존 fast 경로)
./gradlew spotlessCheck lint \
  :domain:station:test :core:model:test \
  :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```
