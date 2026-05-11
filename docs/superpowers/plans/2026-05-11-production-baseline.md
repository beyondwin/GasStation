# GasStation Production Baseline (v1.1) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GasStation 1.0.2를 정적 분석/화면 회귀/i18n/장애 보고/CI/문서 baseline을 갖춘 1.1.0 production 기준선으로 격상한다.

**Architecture:** 신규 인프라는 모두 `build-logic/convention`의 plugin으로 추가해 모듈별 build script를 단순하게 유지한다. 사용자 노출 문자열은 `StringResource` 추상화를 통해 ViewModel → Compose 경계에서 ID로 흐른다. CrashReporter는 도메인 인터페이스로 두고 flavor module에서 demo/prod 바인딩을 분기한다. Roborazzi/Kover는 모듈 opt-in convention plugin으로 적용한다.

**Tech Stack:** Kotlin 2.3.20, AGP 9.1.1, Compose BOM 2026.03.01, Hilt 2.59.2, Room 2.8.4, Spotless 7.x + ktlint 1.5.x, Roborazzi 1.x, Kover 0.9.x, GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-05-11-production-baseline-design.md`](../specs/2026-05-11-production-baseline-design.md)

**커밋 정책:** 한 task = 한 커밋. 메시지는 `chore:`, `feat:`, `refactor:`, `docs:`, `test:` 접두 사용. 각 phase 종료 직후 검증 명령을 돌려 회귀가 없는지 확인한 후 다음 phase로 진입.

---

## Phase 0: 사전 체크

### Task 0.1: 작업 브랜치 분기와 초기 상태 검증

**Files:** N/A (git 작업)

- [ ] **Step 1: 작업 브랜치 생성**

```bash
git fetch origin
git checkout -b chore/production-baseline-v1.1 origin/main
git status --short    # clean이어야 함
```

- [ ] **Step 2: 현 상태 검증**

```bash
./gradlew :app:assembleDemoDebug :app:testDemoDebugUnitTest
```

Expected: BUILD SUCCESSFUL. 회귀 없는 baseline 확인.

- [ ] **Step 3: 커밋 불필요**

---

## Phase 1: OSS 위생 + 잔재 청소

### Task 1.1: MIT LICENSE 추가

**Files:**
- Create: `LICENSE`

- [ ] **Step 1: 파일 생성**

```
MIT License

Copyright (c) 2026 kws

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 2: 커밋**

```bash
git add LICENSE
git commit -m "chore: add MIT LICENSE"
```

### Task 1.2: .editorconfig 추가

**Files:**
- Create: `.editorconfig`

- [ ] **Step 1: 파일 생성**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ktlint_standard_filename = disabled
ktlint_standard_no-wildcard-imports = enabled
max_line_length = 140

[*.{json,yml,yaml,toml,md}]
indent_size = 2
```

- [ ] **Step 2: 커밋**

```bash
git add .editorconfig
git commit -m "chore: add .editorconfig for Kotlin official style"
```

### Task 1.3: PR 템플릿

**Files:**
- Create: `.github/PULL_REQUEST_TEMPLATE.md`

- [ ] **Step 1: 파일 생성**

```markdown
## 요약

<!-- 변경의 목적과 사용자/개발자 영향 -->

## 변경

- [ ] 사용자 노출 문자열 변경 (있다면 strings.xml 반영)
- [ ] 새 의존성 추가 (있다면 catalog 등록)
- [ ] 새 모듈 추가 또는 모듈 경계 변경

## 검증

```bash
# 실행한 명령 붙여넣기
```

## 문서

- [ ] `docs/architecture.md`
- [ ] `docs/module-contracts.md`
- [ ] `docs/state-model.md`
- [ ] `docs/test-strategy.md`
- [ ] `docs/verification-matrix.md`
- [ ] `CHANGELOG.md`
- [ ] 해당 없음

## 스크린샷 / 영상

<!-- UI 변경 시 첨부 -->
```

- [ ] **Step 2: 커밋**

```bash
git add .github/PULL_REQUEST_TEMPLATE.md
git commit -m "chore: add pull request template"
```

### Task 1.4: 이슈 템플릿 두 개

**Files:**
- Create: `.github/ISSUE_TEMPLATE/bug_report.md`
- Create: `.github/ISSUE_TEMPLATE/feature_request.md`
- Create: `.github/ISSUE_TEMPLATE/config.yml`

- [ ] **Step 1: bug_report.md**

```markdown
---
name: 버그 리포트
about: 재현 가능한 결함 보고
title: "[BUG] "
labels: bug
---

## 환경

- 앱 버전: (Play Store 또는 빌드 flavor)
- 디바이스: (OEM/모델/Android 버전)
- 네트워크: (Wi-Fi / LTE / offline)

## 재현 단계

1.
2.
3.

## 기대 동작

## 실제 동작

## 로그 / 스크린샷
```

- [ ] **Step 2: feature_request.md**

```markdown
---
name: 기능 제안
about: 새 사용자 시나리오 또는 개선 제안
title: "[FR] "
labels: enhancement
---

## 해결하려는 사용자 문제

## 제안 동작

## 대안

## 추가 컨텍스트
```

- [ ] **Step 3: config.yml**

```yaml
blank_issues_enabled: false
```

- [ ] **Step 4: 커밋**

```bash
git add .github/ISSUE_TEMPLATE/
git commit -m "chore: add issue templates"
```

### Task 1.5: dependabot.yml

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 1: 파일 생성**

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "daily"
    open-pull-requests-limit: 5
    groups:
      androidx:
        patterns:
          - "androidx.*"
      kotlinx:
        patterns:
          - "org.jetbrains.kotlinx:*"
      compose:
        patterns:
          - "androidx.compose:*"
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

- [ ] **Step 2: 커밋**

```bash
git add .github/dependabot.yml
git commit -m "chore: enable dependabot for gradle and github-actions"
```

### Task 1.6: CONTRIBUTING.md

**Files:**
- Create: `CONTRIBUTING.md`

- [ ] **Step 1: 파일 생성**

```markdown
# Contributing to GasStation

GasStation은 한국 운전자가 현재 위치 기반으로 가까운 주유소를 비교하는 Android 앱입니다. 외부 기여를 환영합니다.

## 시작하기

1. JDK 17, Android SDK 35.
2. `~/.gradle/gradle.properties`에 `opinet.apikey`를 둘 수 있습니다. `demo` 빌드는 키 없이 동작합니다.
3. 처음에는 `demo`로 검증하세요.

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest
```

## 운영 계약

- 모든 작업자는 `AGENTS.md`를 먼저 읽습니다.
- 작업 순서와 체크리스트는 `docs/agent-workflow.md`.
- 모듈 경계 판단은 `docs/module-contracts.md`.

## 머지 전 검증

`docs/verification-matrix.md`의 머지 전 회귀 세트를 통과해야 합니다.

```bash
./gradlew spotlessCheck ktlintCheck lint \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :app:assembleDemoDebug :app:assembleProdDebug :app:assembleProdRelease \
  verifyRoborazziDebug koverXmlReport
```

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/)을 따릅니다.

- `feat:` 사용자 기능
- `fix:` 버그 수정
- `refactor:` 동작 변경 없는 구조 정리
- `chore:` 빌드/도구/메타데이터
- `docs:` 문서 전용
- `test:` 테스트 전용

## 코드 스타일

`./gradlew spotlessApply ktlintFormat`을 커밋 전에 실행합니다.

## 행동 강령

존중과 건설적 토론을 원칙으로 합니다. 사용자 데이터/위치 처리에 영향을 주는 PR은 보안 영향을 명시합니다.
```

- [ ] **Step 2: 커밋**

```bash
git add CONTRIBUTING.md
git commit -m "docs: add CONTRIBUTING guide"
```

### Task 1.7: 미사용 디렉토리 제거

**Files:**
- Delete: `core/common/`, `core/ui/`

- [ ] **Step 1: 존재 확인 후 제거**

```bash
test -d core/common && rm -rf core/common
test -d core/ui && rm -rf core/ui
git status --short
```

Expected: 빈 디렉토리만 있던 경우 git status에는 변경 없음. 추적 파일이 있었다면 deletion 표시.

- [ ] **Step 2: settings.gradle.kts 재확인**

```bash
grep -n "core:common\|core:ui" settings.gradle.kts
```

Expected: 출력 없음 (이미 include되지 않음).

- [ ] **Step 3: 변경이 있으면 커밋**

```bash
git add -A
git commit -m "chore: remove unused core:common and core:ui directories" || true
```

---

## Phase 2: 정적 분석 파이프라인

### Task 2.1: ktlint와 Spotless 의존성 catalog 등록

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: versions/plugins 추가**

`[versions]` 섹션에 추가:

```toml
spotless = "7.0.2"
ktlint = "1.5.0"
```

`[plugins]` 섹션에 추가:

```toml
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

`[libraries]` 섹션에 추가:

```toml
spotless-gradlePlugin = { module = "com.diffplug.spotless:spotless-plugin-gradle", version.ref = "spotless" }
```

- [ ] **Step 2: build-logic 의존성**

`build-logic/convention/build.gradle.kts`의 `dependencies`에 추가:

```kotlin
implementation(libs.spotless.gradlePlugin)
```

- [ ] **Step 3: 커밋**

```bash
git add gradle/libs.versions.toml build-logic/convention/build.gradle.kts
git commit -m "chore: register spotless and ktlint in version catalog"
```

### Task 2.2: Spotless convention plugin 추가

**Files:**
- Create: `build-logic/convention/src/main/kotlin/GasStationSpotlessConventionPlugin.kt`
- Modify: `build-logic/convention/build.gradle.kts` (gradlePlugin block)

- [ ] **Step 1: plugin 코드**

```kotlin
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class GasStationSpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            extensions.configure(SpotlessExtension::class.java) { spotless ->
                val ktlintVersion = "1.5.0"

                spotless.kotlin { kt ->
                    kt.target("src/**/*.kt")
                    kt.targetExclude("**/build/**", "**/generated/**")
                    kt.ktlint(ktlintVersion)
                        .editorConfigOverride(
                            mapOf(
                                "android" to "true",
                                "ktlint_standard_filename" to "disabled",
                            ),
                        )
                    kt.trimTrailingWhitespace()
                    kt.endWithNewline()
                }

                spotless.kotlinGradle { kt ->
                    kt.target("*.gradle.kts", "src/**/*.gradle.kts")
                    kt.ktlint(ktlintVersion)
                }

                spotless.format("misc") { misc ->
                    misc.target("*.md", ".gitignore")
                    misc.trimTrailingWhitespace()
                    misc.endWithNewline()
                }
            }
        }
    }
}
```

- [ ] **Step 2: gradlePlugin 등록**

`build-logic/convention/build.gradle.kts`의 `gradlePlugin.plugins` 블록에 추가:

```kotlin
register("androidSpotless") {
    id = "gasstation.spotless"
    implementationClass = "GasStationSpotlessConventionPlugin"
}
```

- [ ] **Step 3: 단독 동작 확인**

```bash
./gradlew tasks --all | grep spotless
```

Expected: 아직은 plugin이 모듈에 적용되지 않아 출력이 없을 수 있음.

- [ ] **Step 4: 커밋**

```bash
git add build-logic/convention/src/main/kotlin/GasStationSpotlessConventionPlugin.kt \
        build-logic/convention/build.gradle.kts
git commit -m "feat(build-logic): add GasStation Spotless convention plugin"
```

### Task 2.3: 모든 모듈 convention plugin에 Spotless 적용

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`

- [ ] **Step 1: 각 plugin의 `override fun apply(target: Project)` 본문 첫 줄에 추가**

```kotlin
with(target) {
    pluginManager.apply("gasstation.spotless")
    // ...기존 코드 유지
}
```

(Compose plugin은 Library plugin을 chain하면 한 번만 적용하면 됨. 중복 적용은 spotless가 idempotent함.)

- [ ] **Step 2: 전 모듈에 적용되는지 확인**

```bash
./gradlew :core:designsystem:tasks --all | grep -i spotless
./gradlew :feature:station-list:tasks --all | grep -i spotless
./gradlew :domain:station:tasks --all | grep -i spotless
```

Expected: `spotlessCheck`, `spotlessApply`가 모든 모듈에 노출.

- [ ] **Step 3: 초기 포맷 적용**

```bash
./gradlew spotlessApply
```

대량 diff가 생긴다. 다음 step에서 단일 커밋으로 처리.

- [ ] **Step 4: ignore-revs 등록 준비**

```bash
git diff --stat | tail -1
```

대량 변경 확인 후 별도 커밋으로 분리.

- [ ] **Step 5: 적용 커밋 (포맷 전용)**

```bash
git add -A
git commit -m "style: spotless apply across all modules"
```

- [ ] **Step 6: .git-blame-ignore-revs 갱신**

```bash
echo "$(git rev-parse HEAD)" >> .git-blame-ignore-revs
git add .git-blame-ignore-revs
git commit -m "chore: ignore spotless reformat commit in git blame"
```

- [ ] **Step 7: convention plugin 적용 자체 커밋**

위 Step 1에서 만든 plugin 적용 변경은 Step 5 직전에 별도 커밋했어야 한다. 순서 정정:

```
Step 1 변경 -> commit: "feat(build-logic): apply spotless to all modules"
Step 3-5: spotlessApply -> commit: "style: spotless apply across all modules"
Step 6: blame ignore
```

이 순서를 지켜서 두 commit으로 분리한다.

### Task 2.4: spotlessCheck CI 호환 검증

- [ ] **Step 1: 명령 통과 확인**

```bash
./gradlew spotlessCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 커밋 없음**

### Task 2.5: Android lint 엄격화

**Files:**
- Modify: 모든 Android 모듈의 `build.gradle.kts` (또는 convention plugin)
- Modify: `lint.xml`

- [ ] **Step 1: convention plugin에서 lint 옵션 일괄 설정**

`build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`의 android `extensions.configure` 블록에 추가:

```kotlin
lint {
    warningsAsErrors = false
    abortOnError = true
    checkDependencies = true
    sarifReport = true
    htmlReport = true
    xmlReport = false
}
```

동일 옵션을 `GasStationAndroidApplicationComposeConventionPlugin`에도 적용.

- [ ] **Step 2: lint.xml 정비**

기존:

```xml
<issue id="OldTargetApi" severity="ignore" />
<issue id="GradleDependency" severity="ignore" />
<issue id="AndroidGradlePluginVersion" severity="ignore" />
<issue id="IconLauncherShape" severity="ignore" />
```

다음 항목 추가:

```xml
<issue id="HardcodedText" severity="error" />
<issue id="MissingTranslation" severity="warning" />
<issue id="UnusedResources" severity="warning" />
<issue id="UnknownNullness" severity="ignore" />
<issue id="ObsoleteSdkInt" severity="ignore" />
```

전체 파일:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="OldTargetApi" severity="ignore" />
    <issue id="GradleDependency" severity="ignore" />
    <issue id="AndroidGradlePluginVersion" severity="ignore" />
    <issue id="IconLauncherShape" severity="ignore" />
    <issue id="HardcodedText" severity="error" />
    <issue id="MissingTranslation" severity="warning" />
    <issue id="UnusedResources" severity="warning" />
    <issue id="UnknownNullness" severity="ignore" />
    <issue id="ObsoleteSdkInt" severity="ignore" />
</lint>
```

- [ ] **Step 3: 첫 실행 — 현재 위반 식별**

```bash
./gradlew :app:lintDemoDebug --no-daemon
```

`HardcodedText` 위반 다수 예상. 이는 Phase 4에서 처리하므로, 임시로 `HardcodedText` severity를 `warning`으로 낮춰 Phase 2 종료. Phase 4에서 다시 `error`로 올린다.

```xml
<issue id="HardcodedText" severity="warning" />
```

- [ ] **Step 4: lint 통과**

```bash
./gradlew lint
```

Expected: BUILD SUCCESSFUL (warning만 출력).

- [ ] **Step 5: 커밋**

```bash
git add build-logic/convention/src/main/kotlin/ lint.xml
git commit -m "chore: tighten Android lint configuration"
```

### Phase 2 검증

- [ ] `./gradlew spotlessCheck ktlintCheck lint` cold cache 통과
- [ ] 단위 테스트가 깨지지 않음: `./gradlew :app:testDemoDebugUnitTest`

---

## Phase 3: 문서 리프레이밍

> 이 phase는 문서에서 "포트폴리오/reference/reviewer/interviewer/면접" 표현을 제거한다. 각 task는 단일 파일을 다루고, 의미와 정보 위계는 보존한다.

### Task 3.1: README.md 리프레이밍

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 첫 단락 교체**

기존:

> 주유주유소는 Jetpack Compose, Hilt, Coroutines, ... 멀티모듈 Android 프로젝트입니다.

신규:

> 주유주유소는 한국 운전자가 현재 위치 기반으로 가까운 주유소를 가격, 거리, 브랜드, 유종, 북마크 상태, 외부 지도 연결 기준으로 비교하는 Android 앱입니다. Jetpack Compose, Hilt, Coroutines, Flow, Room, ViewModel, MVVM 아키텍처를 사용하며, `demo` 빌드는 재현 가능한 고정 실행 경로를, `prod` 빌드는 Opinet Open API 실호출 경로를 제공합니다.

- [ ] **Step 2: "포트폴리오/reference" 단어 검색 후 모두 제거**

```bash
rg -n "포트폴리오|portfolio|reference 앱|reference/portfolio|reviewer|interviewer|면접" README.md
```

각 hit를 다음 규칙으로 치환:
- "포트폴리오/reference 앱" → "Android 앱"
- "reviewer/interviewer가 봤을 때 ..." → 해당 문장 자체 삭제 또는 "사용자가 ..." 로 치환
- "포트폴리오 review" → "검토"

- [ ] **Step 3: README 첫 인상 5줄 (영문 elevator) 삽입은 Phase 6 R9에서 처리하므로 여기서는 미적용**

- [ ] **Step 4: 커밋**

```bash
git add README.md
git commit -m "docs: reframe README from portfolio to product context"
```

### Task 3.2: AGENTS.md 리프레이밍

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: "Product And UI Invariants" 섹션 수정**

기존:

> GasStation은 포트폴리오/reference 성격의 Android 앱이며, 실제 운전자가 ...

신규:

> GasStation은 한국 운전자가 현재 위치 기반으로 가까운 주유소를 가격, 거리, 브랜드, 유종, 북마크 상태, 외부 지도 연결 기준으로 빠르게 비교하는 Android 앱이다.

- [ ] **Step 2: 잔여 검색 후 정리**

```bash
rg -n "포트폴리오|portfolio|reviewer|interviewer" AGENTS.md
```

남은 hit를 정리한다. AGENTS.md는 운영 계약 문서이므로 의미를 그대로 보존한다.

- [ ] **Step 3: 커밋**

```bash
git add AGENTS.md
git commit -m "docs: reframe AGENTS.md product definition"
```

### Task 3.3: .impeccable.md 리프레이밍

**Files:**
- Modify: `.impeccable.md`

- [ ] **Step 1: "Users" 섹션 교체**

기존:

> GasStation is primarily a portfolio/reference Android app for reviewers and interviewers, with real driver usability kept intact as the product baseline. Reviewers should quickly see that the app has production-grade Android structure, coherent UI contracts, and a polished presentation layer. Drivers use the app to compare ...

신규:

> GasStation is a Korean Android app that helps drivers compare nearby gas stations by current location, price, distance, brand, fuel type, watchlist state, and external map handoff. The product baseline targets fast, trustworthy comparison under driving pressure.

- [ ] **Step 2: "Maintenance Priority" 섹션의 "review value visible" 같은 표현 정리**

기존 design principle 5번:

> Make review value visible: clear state handling, ...

신규:

> Make production quality visible: clear state handling, consistent components, accessible interaction targets, and predictable typography roles.

- [ ] **Step 3: README/demo story가 "portfolio-review speed"로 평가되는 부분 정리**

기존:

> Use visual QA screenshots to confirm the README/demo story still reads well at portfolio-review speed after visible UI changes.

신규:

> Use visual QA screenshots to confirm the README/demo story still reads well at glanceable speed after visible UI changes.

- [ ] **Step 4: 커밋**

```bash
git add .impeccable.md
git commit -m "docs: reframe design context away from portfolio framing"
```

### Task 3.4: docs/architecture.md 리프레이밍

**Files:**
- Modify: `docs/architecture.md`

- [ ] **Step 1: 검색**

```bash
rg -n "포트폴리오|portfolio|reference|reviewer|interviewer" docs/architecture.md
```

- [ ] **Step 2: 각 hit를 다음 규칙으로 정리**

- "reference 앱" → "Android 앱"
- "portfolio/reference" → 삭제 또는 "Android 앱"
- 단어 단독이 아닌 맥락 의존 hit는 문장 단위로 검토

- [ ] **Step 3: 커밋**

```bash
git add docs/architecture.md
git commit -m "docs: reframe architecture.md product framing"
```

### Task 3.5: docs/agent-workflow.md 리프레이밍

**Files:**
- Modify: `docs/agent-workflow.md`

- [ ] **Step 1: 검색 후 정리**

```bash
rg -n "포트폴리오|portfolio|reviewer|interviewer" docs/agent-workflow.md
```

각 hit를 동일 규칙으로 정리.

- [ ] **Step 2: 커밋**

```bash
git add docs/agent-workflow.md
git commit -m "docs: reframe agent-workflow product framing"
```

### Task 3.6: 나머지 live docs 일괄 검토

**Files:** `docs/module-contracts.md`, `docs/state-model.md`, `docs/offline-strategy.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`, `docs/project-reading-guide.md`

- [ ] **Step 1: 일괄 검색**

```bash
rg -n "포트폴리오|portfolio|reviewer|interviewer|면접" docs/ -g '!docs/superpowers/**' -g '!docs/release-notes/**'
```

- [ ] **Step 2: 파일별 정리**

각 파일을 열어 단어 교체:
- "portfolio/reference" → "Android 앱" 또는 삭제
- "reviewer가 ..." → "사용자가 ..." 또는 "검토자가 ..."

- [ ] **Step 3: 커밋**

파일별로 또는 묶어서 한 번에:

```bash
git add docs/module-contracts.md docs/state-model.md docs/offline-strategy.md \
        docs/test-strategy.md docs/verification-matrix.md docs/project-reading-guide.md
git commit -m "docs: reframe live docs to product framing"
```

### Task 3.7: improvement-analysis와 deep-analysis-report 이동

**Files:**
- Move: `docs/improvement-analysis.md` → `docs/history/improvement-analysis.md`
- Move: `docs/deep-analysis-report.md` → `docs/history/deep-analysis-report.md`
- Modify: `README.md`, `docs/project-reading-guide.md` 링크

- [ ] **Step 1: 디렉터리 생성과 이동**

```bash
mkdir -p docs/history
git mv docs/improvement-analysis.md docs/history/improvement-analysis.md
git mv docs/deep-analysis-report.md docs/history/deep-analysis-report.md
```

- [ ] **Step 2: 이동된 두 문서 상단에 안내 추가**

각 파일 첫 줄에 삽입:

```markdown
> 이 문서는 1.0.2 시점의 분석 history입니다. 1.1.0 이후의 baseline 결정은 `docs/superpowers/specs/2026-05-11-production-baseline-design.md`가 단일 출처입니다.
```

- [ ] **Step 3: README/project-reading-guide의 링크 갱신**

```bash
rg -n "improvement-analysis|deep-analysis-report" README.md docs/project-reading-guide.md
```

각 hit의 경로를 `docs/history/`로 갱신.

- [ ] **Step 4: 커밋**

```bash
git add docs/history/ README.md docs/project-reading-guide.md
git commit -m "docs: archive 1.0.2 analysis reports under docs/history"
```

### Task 3.8: CHANGELOG.md 리프레이밍

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: 1.0.x 섹션에서 "포트폴리오" 표현 정리**

기존 1.0.0 항목:

> 현재 위치 기반 주유소 탐색, stale cache fallback, watchlist 비교, 외부 지도 handoff, demo/prod flavor 경로를 갖춘 초기 reference 앱 기준선입니다.

신규:

> 현재 위치 기반 주유소 탐색, stale cache fallback, watchlist 비교, 외부 지도 handoff, demo/prod flavor 경로를 갖춘 1.0 기준선입니다.

- [ ] **Step 2: 커밋**

```bash
git add CHANGELOG.md
git commit -m "docs: reframe CHANGELOG 1.0.x notes"
```

### Phase 3 검증

- [ ] `rg -n "포트폴리오|portfolio|reference 앱|reviewer|interviewer|면접" -g '!docs/superpowers/**' -g '!docs/history/**' -g '!docs/release-notes/**'`의 결과가 0건

---

## Phase 4: 문자열 외부화 (i18n)

### Task 4.1: StringResource 추상화 도입 (TDD)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/string/StringResource.kt`
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/string/StringResourceTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.gasstation.core.designsystem.string

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gasstation.core.designsystem.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringResourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fromId_resolves_to_resource_value() {
        val resource = StringResource.fromId(R.string.test_hello)
        assertEquals("Hello", resource.resolve(context))
    }

    @Test
    fun fromId_with_args_substitutes() {
        val resource = StringResource.fromId(R.string.test_greeting, listOf("Kim"))
        assertEquals("Hello, Kim", resource.resolve(context))
    }

    @Test
    fun raw_returns_literal_value() {
        val resource = StringResource.raw("literal")
        assertEquals("literal", resource.resolve(context))
    }
}
```

테스트용 string은 `core/designsystem/src/test/res/values/strings.xml`에 추가:

```xml
<resources>
    <string name="test_hello">Hello</string>
    <string name="test_greeting">Hello, %1$s</string>
</resources>
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests StringResourceTest
```

Expected: `StringResource` 클래스 없음으로 컴파일 에러.

- [ ] **Step 3: 최소 구현**

```kotlin
package com.gasstation.core.designsystem.string

import android.content.Context
import androidx.annotation.StringRes

sealed interface StringResource {
    fun resolve(context: Context): String

    data class FromId(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : StringResource {
        override fun resolve(context: Context): String =
            if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
    }

    data class Raw(val value: String) : StringResource {
        override fun resolve(context: Context): String = value
    }

    companion object {
        fun fromId(@StringRes id: Int, args: List<Any> = emptyList()): StringResource = FromId(id, args)
        fun raw(value: String): StringResource = Raw(value)
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests StringResourceTest
```

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/string/ \
        core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/string/ \
        core/designsystem/src/test/res/
git commit -m "feat(designsystem): introduce StringResource abstraction"
```

### Task 4.2: feature:station-list 문자열 외부화

**Files:**
- Modify: `feature/station-list/src/main/res/values/strings.xml` (없으면 생성)
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt` (snackbar 렌더)

- [ ] **Step 1: strings.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="station_list_permission_denied">위치 권한을 허용해주세요.</string>
    <string name="station_list_location_timeout">현재 위치 확인이 지연되고 있습니다.</string>
    <string name="station_list_location_failed">현재 위치를 확인하지 못했습니다.</string>
    <string name="station_list_refresh_timeout">서버 응답이 늦어 가격을 새로고침하지 못했습니다.</string>
    <string name="station_list_refresh_failed">주유소 목록을 새로고침하지 못했습니다.</string>
</resources>
```

- [ ] **Step 2: StationListEffect 변경**

기존:

```kotlin
sealed interface StationListEffect {
    data class ShowSnackbar(val message: String) : StationListEffect
    // ...
}
```

신규:

```kotlin
import com.gasstation.core.designsystem.string.StringResource

sealed interface StationListEffect {
    data class ShowSnackbar(val message: StringResource) : StationListEffect
    // ...기타 유지
}
```

- [ ] **Step 3: ViewModel 변경**

`StationListViewModel.kt`에서 하드코딩된 문자열을 `StringResource.fromId(...)`로 교체:

```kotlin
import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.feature.stationlist.R

private fun StationRefreshFailureReason?.refreshFailureMessage(): StringResource = when (this) {
    StationRefreshFailureReason.Timeout -> StringResource.fromId(R.string.station_list_refresh_timeout)
    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    StationRefreshFailureReason.Unknown,
    null -> StringResource.fromId(R.string.station_list_refresh_failed)
}
```

`handleLocationResult`의 snackbar emit:

```kotlin
LocationAcquisitionResult.PermissionDenied -> {
    logLocationFailure(result)
    if (showPermissionDeniedFeedback) {
        mutableEffects.emit(
            StationListEffect.ShowSnackbar(
                StringResource.fromId(R.string.station_list_permission_denied),
            ),
        )
    }
    null
}
```

`onBlockingFailure`도 동일 패턴:

```kotlin
private suspend fun onBlockingFailure(
    reason: StationListFailureReason,
    messageRes: Int,
) {
    searchOrchestrator.onBlockingFailure(reason = reason)
    mutableEffects.emit(StationListEffect.ShowSnackbar(StringResource.fromId(messageRes)))
}
```

호출부도 `R.string.station_list_location_timeout` / `R.string.station_list_location_failed`로 교체.

- [ ] **Step 4: Compose 렌더 변경**

`StationListRoute.kt`(또는 snackbar 렌더 부분)에서:

```kotlin
LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
        when (effect) {
            is StationListEffect.ShowSnackbar -> {
                snackbarHostState.showSnackbar(effect.message.resolve(context))
            }
            // ...
        }
    }
}
```

`context = LocalContext.current` 확보 필요.

- [ ] **Step 5: ViewModel 테스트 업데이트**

기존 테스트가 `effect.message == "위치 권한을 허용해주세요."`를 검증하면, 이제는 `effect.message == StringResource.FromId(R.string.station_list_permission_denied)`를 검증한다.

```kotlin
assertEquals(
    StringResource.fromId(R.string.station_list_permission_denied),
    (effect as StationListEffect.ShowSnackbar).message,
)
```

- [ ] **Step 6: 테스트 실행**

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add feature/station-list/src/main/res/ feature/station-list/src/main/kotlin/ \
        feature/station-list/src/test/kotlin/
git commit -m "refactor(station-list): externalize user-facing strings to resources"
```

### Task 4.3: feature:settings, feature:watchlist 문자열 외부화

**Files:** 각 feature의 `src/main/res/values/strings.xml`, ViewModel/Effect/Screen 파일

- [ ] **Step 1: 검색**

```bash
rg -n "\"[가-힣]" feature/settings/src/main/kotlin feature/watchlist/src/main/kotlin
```

- [ ] **Step 2: 각 모듈의 strings.xml 생성/갱신**

발견된 문자열을 모두 id로 분리. 명명 규칙: `<module>_<area>_<purpose>`.

예시 (`feature/settings/src/main/res/values/strings.xml`):

```xml
<resources>
    <string name="settings_radius_title">검색 반경</string>
    <string name="settings_fuel_type_title">유종</string>
    <!-- ... -->
</resources>
```

- [ ] **Step 3: Compose `stringResource(...)` 호출로 교체**

```kotlin
Text(text = stringResource(id = R.string.settings_radius_title))
```

- [ ] **Step 4: 테스트 갱신**

테스트가 한국어 literal로 단언했다면 `stringResource` resource id로 변경.

- [ ] **Step 5: 모듈별 단위/Robolectric 테스트 통과 확인**

```bash
./gradlew :feature:settings:testDebugUnitTest :feature:watchlist:testDebugUnitTest
```

- [ ] **Step 6: 커밋**

```bash
git add feature/settings/ feature/watchlist/
git commit -m "refactor(features): externalize settings and watchlist strings"
```

### Task 4.4: app 모듈과 공용 문자열

**Files:** `app/src/main/res/values/strings.xml`, 관련 Kotlin/Compose 파일

- [ ] **Step 1: 검색**

```bash
rg -n "\"[가-힣]" app/src/main/java
```

- [ ] **Step 2: strings.xml 갱신**

```xml
<resources>
    <string name="app_name">주유주유소</string>
    <!-- 추가 라벨, 네비게이션 destination 라벨 등 -->
</resources>
```

- [ ] **Step 3: 코드 교체 후 단위/Robolectric 통과 확인**

```bash
./gradlew :app:testDemoDebugUnitTest
```

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/
git commit -m "refactor(app): externalize remaining user-facing strings"
```

### Task 4.5: values-en/strings.xml 동반 추가

**Files:** `*/src/main/res/values-en/strings.xml`

- [ ] **Step 1: 각 모듈에 values-en 디렉터리 생성**

ko default와 동일한 id, 영어 카피.

`feature/station-list/src/main/res/values-en/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="station_list_permission_denied">Location permission is required.</string>
    <string name="station_list_location_timeout">Locating your position is taking longer than expected.</string>
    <string name="station_list_location_failed">Could not determine your location.</string>
    <string name="station_list_refresh_timeout">Refresh timed out while updating prices.</string>
    <string name="station_list_refresh_failed">Could not refresh the station list.</string>
</resources>
```

`feature/settings`, `feature/watchlist`, `app`도 동일 패턴.

- [ ] **Step 2: lint MissingTranslation warning 확인**

```bash
./gradlew lint
```

Expected: MissingTranslation warning 0건.

- [ ] **Step 3: 커밋**

```bash
git add */src/main/res/values-en/
git commit -m "feat(i18n): add English translations for all user-facing strings"
```

### Task 4.6: HardcodedText lint를 다시 error로

**Files:**
- Modify: `lint.xml`

- [ ] **Step 1: severity 변경**

```xml
<issue id="HardcodedText" severity="error" />
```

- [ ] **Step 2: lint 통과 확인**

```bash
./gradlew lint
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add lint.xml
git commit -m "chore(lint): re-enable HardcodedText as error"
```

### Phase 4 검증

- [ ] `rg -n "\"[가-힣]" --type kt -g '!*/test/**' -g '!*/androidTest/**' feature app`의 결과가 0건
- [ ] `./gradlew lint` 통과
- [ ] `./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :feature:station-list:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:watchlist:testDebugUnitTest` 통과

---

## Phase 5: CrashReporter 추상화

### Task 5.1: 도메인 인터페이스 추가 (TDD)

**Files:**
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/CrashReporter.kt`
- Create: `domain/station/src/test/kotlin/com/gasstation/domain/station/CrashReporterContractTest.kt`

- [ ] **Step 1: 계약 테스트 작성**

```kotlin
package com.gasstation.domain.station

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReporterContractTest {
    @Test
    fun fake_reporter_records_nonfatal() {
        val reporter = FakeCrashReporter()
        val error = IllegalStateException("boom")
        reporter.recordNonFatal(error, mapOf("module" to "station"))
        assertEquals(1, reporter.records.size)
        assertEquals(error, reporter.records.first().throwable)
        assertEquals("station", reporter.records.first().metadata["module"])
    }

    @Test
    fun fake_reporter_logs_breadcrumb() {
        val reporter = FakeCrashReporter()
        reporter.log("refresh started")
        assertEquals(listOf("refresh started"), reporter.logs)
    }

    private class FakeCrashReporter : CrashReporter {
        data class Record(val throwable: Throwable, val metadata: Map<String, String>)
        val records = mutableListOf<Record>()
        val logs = mutableListOf<String>()
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
            records += Record(throwable, metadata)
        }
        override fun log(message: String) { logs += message }
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew :domain:station:test
```

Expected: `CrashReporter` 없음.

- [ ] **Step 3: 구현**

```kotlin
package com.gasstation.domain.station

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, metadata: Map<String, String> = emptyMap())
    fun log(message: String)
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew :domain:station:test
```

- [ ] **Step 5: 커밋**

```bash
git add domain/station/src/main/kotlin/com/gasstation/domain/station/CrashReporter.kt \
        domain/station/src/test/kotlin/com/gasstation/domain/station/CrashReporterContractTest.kt
git commit -m "feat(domain): add CrashReporter contract"
```

### Task 5.2: NoOp + Logcat 구현

**Files:**
- Create: `app/src/demo/java/com/gasstation/analytics/NoOpCrashReporter.kt`
- Create: `app/src/prod/java/com/gasstation/analytics/LogcatCrashReporter.kt`
- Create: `app/src/demo/java/com/gasstation/analytics/NoOpCrashReporterTest.kt`
- Create: `app/src/prod/java/com/gasstation/analytics/LogcatCrashReporterTest.kt`

- [ ] **Step 1: NoOp 구현 + 테스트**

`NoOpCrashReporter.kt`:

```kotlin
package com.gasstation.analytics

import com.gasstation.domain.station.CrashReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) = Unit
    override fun log(message: String) = Unit
}
```

테스트는 단순 instanciation만 검증.

- [ ] **Step 2: Logcat 구현 + 테스트**

`LogcatCrashReporter.kt`:

```kotlin
package com.gasstation.analytics

import com.gasstation.domain.station.CrashReporter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LogcatCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
        val metaString = metadata.entries.joinToString(separator = " ") { (k, v) -> "$k=$v" }
        Timber.tag(TAG).e(throwable, "non-fatal $metaString")
    }

    override fun log(message: String) {
        Timber.tag(TAG).i(message)
    }

    private companion object {
        const val TAG = "GasStationCrash"
    }
}
```

테스트는 `Timber.plant(testTree)` 패턴으로 ERROR 호출 캡처.

- [ ] **Step 3: 커밋**

```bash
git add app/src/demo/java/com/gasstation/analytics/ app/src/prod/java/com/gasstation/analytics/
git commit -m "feat(app): add NoOp and Logcat CrashReporter implementations"
```

### Task 5.3: Hilt 바인딩

**Files:**
- Create: `app/src/demo/java/com/gasstation/di/DemoCrashReporterModule.kt`
- Create: `app/src/prod/java/com/gasstation/di/ProdCrashReporterModule.kt`

- [ ] **Step 1: demo 바인딩**

```kotlin
package com.gasstation.di

import com.gasstation.analytics.NoOpCrashReporter
import com.gasstation.domain.station.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoCrashReporterModule {
    @Binds
    abstract fun bindCrashReporter(impl: NoOpCrashReporter): CrashReporter
}
```

- [ ] **Step 2: prod 바인딩**

```kotlin
package com.gasstation.di

import com.gasstation.analytics.LogcatCrashReporter
import com.gasstation.domain.station.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProdCrashReporterModule {
    @Binds
    abstract fun bindCrashReporter(impl: LogcatCrashReporter): CrashReporter
}
```

- [ ] **Step 3: assemble로 Hilt 검증**

```bash
./gradlew :app:assembleDemoDebug :app:assembleProdDebug
```

- [ ] **Step 4: 커밋**

```bash
git add app/src/demo/java/com/gasstation/di/ app/src/prod/java/com/gasstation/di/
git commit -m "feat(di): wire CrashReporter binding per flavor"
```

### Task 5.4: 호출 지점 연결

**Files:**
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt` (예외 처리 분기)
- Modify: 테스트 (Fake CrashReporter 주입)

- [ ] **Step 1: DefaultStationRepository 변경**

생성자에 `CrashReporter` 주입:

```kotlin
class DefaultStationRepository @Inject constructor(
    // ...기존 의존성
    private val crashReporter: CrashReporter,
) : StationRepository {
```

refresh 실패 catch에서 `StationRefreshException`이 아닌 throwable은 record:

```kotlin
} catch (cancellationException: CancellationException) {
    throw cancellationException
} catch (refreshException: StationRefreshException) {
    throw refreshException
} catch (throwable: Throwable) {
    crashReporter.recordNonFatal(
        throwable = throwable,
        metadata = mapOf(
            "module" to "data:station",
            "operation" to "refreshNearbyStations",
        ),
    )
    throw StationRefreshException(StationRefreshFailureReason.Unknown, throwable)
}
```

- [ ] **Step 2: AndroidAddressResolver 변경**

지오코더 IO/timeout이 아닌 예외 catch에서 record. 기존 catch 블록을 확장.

- [ ] **Step 3: 테스트 갱신**

`DefaultStationRepositoryTest`에 Fake CrashReporter 주입하고 unknown throwable 발생 시 record 호출을 단언.

```kotlin
@Test
fun refresh_unexpected_throwable_records_nonfatal_then_throws_StationRefreshException() = runTest {
    val crashReporter = FakeCrashReporter()
    val repository = DefaultStationRepository(/* ... */ crashReporter = crashReporter, clock = fixedClock)
    val remote = remoteThatThrows(IllegalStateException("boom"))
    // ...

    assertFailsWith<StationRefreshException> {
        repository.refreshNearbyStations(query)
    }
    assertEquals(1, crashReporter.records.size)
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew :data:station:testDebugUnitTest :core:location:testDebugUnitTest
```

- [ ] **Step 5: 커밋**

```bash
git add data/station/src/ core/location/src/
git commit -m "feat: route unexpected exceptions through CrashReporter"
```

### Phase 5 검증

- [ ] `./gradlew :app:assembleDemoDebug :app:assembleProdDebug`
- [ ] `./gradlew :data:station:testDebugUnitTest :core:location:testDebugUnitTest`

---

## Phase 6: README 정비

### Task 6.1: 영문 elevator + 배지 + 5분 코드 투어

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README 첫 섹션을 영문 elevator로 시작**

기존 첫 줄 위에 삽입:

```markdown
# 주유주유소 (GasStation)

[![CI](https://github.com/<owner>/GasStation/actions/workflows/android.yml/badge.svg)](https://github.com/<owner>/GasStation/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2026.03.01-4285F4.svg)](https://developer.android.com/jetpack/compose/bom)
[![minSdk](https://img.shields.io/badge/minSdk-24-3DDC84.svg)](https://developer.android.com/about/versions)

> GasStation is a Korean Android app that helps drivers compare nearby gas stations by current location, price, distance, brand, fuel type, and watchlist state, then hands off to the user's preferred external map for turn-by-turn navigation. The codebase ships a 17-module Clean Architecture setup with Jetpack Compose, Hilt, Room, and a deterministic `demo` flavor that mirrors the real Opinet API path.

---
```

`<owner>`는 실제 GitHub owner로 치환.

- [ ] **Step 2: 한국어 본문은 그대로 (Task 3.1에서 이미 reframing 완료)**

- [ ] **Step 3: 5분 코드 투어 섹션을 본문 마지막에 추가**

```markdown
## 5분 코드 투어

처음 보는 사람이 코드 흐름을 빠르게 따라가는 권장 경로입니다.

1. `app/src/main/java/com/gasstation/App.kt` — Hilt 진입과 startup hook.
2. `app/src/main/java/com/gasstation/MainActivity.kt` — Compose host와 system bar 정책.
3. `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt` — destination 그래프.
4. `feature/station-list/.../StationListRoute.kt` → `StationListViewModel.kt` — 화면 진입과 ViewModel.
5. `feature/station-list/.../StationSearchOrchestrator.kt` — 쿼리/캐시/실패 책임 분리.
6. `data/station/.../DefaultStationRepository.kt` — Room snapshot + remote fetch 조합과 재시도.
7. `core/network/.../NetworkStationFetcher.kt` — Opinet API와 KATEC 좌표 변환.

각 단계의 책임 분리 근거는 [`docs/architecture.md`](docs/architecture.md)에 있습니다.
```

- [ ] **Step 4: 커밋**

```bash
git add README.md
git commit -m "docs(README): add English elevator, badges, and 5-minute code tour"
```

### Task 6.2: 데모 GIF placeholder

**Files:**
- Create: `docs/readme-assets/demo.gif` (실제 GIF 또는 placeholder)
- Modify: `README.md`

- [ ] **Step 1: GIF 자산 추가**

`demo` 빌드를 emulator에서 캡처한 12~15초 GIF가 권장. 즉시 생성 불가하면 `docs/readme-assets/demo.gif.placeholder` 파일을 생성하고 GitHub Issue로 follow-up.

- [ ] **Step 2: README의 "미리보기" 섹션을 GIF + 기존 3장으로 교체**

```markdown
## 미리보기

<p align="center">
  <img src="docs/readme-assets/demo.gif" alt="GasStation demo" width="320" />
</p>

<p align="center">
  <img width="31%" alt="가까운 주유소 목록" src="docs/readme-assets/playstore_11.png">
  <img width="31%" alt="한 번의 터치 길 안내" src="docs/readme-assets/playstore_22.png">
  <img width="31%" alt="찾기 설정" src="docs/readme-assets/playstore_33.png">
</p>
```

- [ ] **Step 3: 커밋**

```bash
git add docs/readme-assets/ README.md
git commit -m "docs(README): add demo gif placeholder and update preview layout"
```

### Task 6.3: Module graph PNG

**Files:**
- Create: `docs/readme-assets/module-graph.png` (외부 도구로 export)
- Modify: `README.md`

- [ ] **Step 1: PNG 생성**

다음 중 한 방법:
1. Mermaid Live Editor에 README의 mermaid 블록을 붙여넣고 PNG export.
2. 또는 `gradle-dependency-graph-generator` plugin 적용 후 task 실행.

- [ ] **Step 2: README에 mermaid 옆 또는 아래에 PNG 첨부**

```markdown
<details><summary>PNG 보기</summary>

![Module graph](docs/readme-assets/module-graph.png)

</details>
```

- [ ] **Step 3: 커밋**

```bash
git add docs/readme-assets/module-graph.png README.md
git commit -m "docs(README): add module graph PNG for offline viewers"
```

### Phase 6 검증

- [ ] README가 GitHub에서 렌더링 시 elevator/배지/GIF/투어가 보임 (눈으로 확인)
- [ ] 모든 링크가 유효: `markdown-link-check README.md` 또는 수동 확인

---

## Phase 7: Roborazzi 화면 회귀

### Task 7.1: 의존성 catalog 등록

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: 추가**

```toml
[versions]
roborazzi = "1.50.1"

[libraries]
roborazzi-core = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }

[plugins]
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

`build-logic/convention/build.gradle.kts` 의존성에 추가:

```kotlin
implementation(libs.roborazzi.core)
```

(plugin 클래스 로딩용)

- [ ] **Step 2: 커밋**

```bash
git add gradle/libs.versions.toml build-logic/convention/build.gradle.kts
git commit -m "chore: register Roborazzi in version catalog"
```

### Task 7.2: Roborazzi convention plugin

**Files:**
- Create: `build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt`
- Modify: `build-logic/convention/build.gradle.kts` (gradlePlugin register)

- [ ] **Step 1: plugin**

```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class GasStationRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            dependencies {
                add("testImplementation", "io.github.takahirom.roborazzi:roborazzi:1.50.1")
                add("testImplementation", "io.github.takahirom.roborazzi:roborazzi-compose:1.50.1")
                add("testImplementation", "io.github.takahirom.roborazzi:roborazzi-junit-rule:1.50.1")
                add("testImplementation", "org.robolectric:robolectric:4.16.1")
                add("testImplementation", "androidx.compose.ui:ui-test-junit4")
            }
        }
    }
}
```

`gradlePlugin` register:

```kotlin
register("roborazzi") {
    id = "gasstation.roborazzi"
    implementationClass = "GasStationRoborazziConventionPlugin"
}
```

- [ ] **Step 2: 커밋**

```bash
git add build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt \
        build-logic/convention/build.gradle.kts
git commit -m "feat(build-logic): add Roborazzi convention plugin"
```

### Task 7.3: core:designsystem에 적용 (TDD)

**Files:**
- Modify: `core/designsystem/build.gradle.kts` (plugin 적용)
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/RoborazziDesignSystemTest.kt`
- 디렉토리: `core/designsystem/src/test/snapshots/` (자동 생성)

- [ ] **Step 1: plugin 적용**

```kotlin
plugins {
    id("gasstation.android.library.compose")
    id("gasstation.roborazzi")
}
```

- [ ] **Step 2: 첫 테스트 작성 — GasStationMetricBlock 골든**

```kotlin
package com.gasstation.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziDesignSystemTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun metric_block_price_emphasis_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationMetricBlock(
                        label = "리터당",
                        value = "1,712 원",
                        emphasis = GasStationMetricEmphasis.Price,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/metric-block-price.png")
    }
}
```

- [ ] **Step 3: 첫 record 실행**

```bash
./gradlew :core:designsystem:recordRoborazziDebug
```

골든 PNG가 `core/designsystem/src/test/snapshots/`에 생성됨. 수동 검토 후 OK면 commit.

- [ ] **Step 4: verify 동작 확인**

```bash
./gradlew :core:designsystem:verifyRoborazziDebug
```

Expected: PASS.

- [ ] **Step 5: 커밋 (테스트 + 골든)**

```bash
git add core/designsystem/build.gradle.kts \
        core/designsystem/src/test/kotlin/ \
        core/designsystem/src/test/snapshots/
git commit -m "test(designsystem): add Roborazzi screenshot for metric block"
```

### Task 7.4: designsystem 핵심 primitive 골든 확장

각 component 1~2 상태 골든 추가.

- [ ] **Step 1: 추가 골든**

`GasStationStatusBanner`, `GasStationGuidanceCard`, `GasStationRow`, `GasStationSupportingInfo`, station card 등 5개.

테스트당 1~3 variant. 각 variant마다 `captureRoboImage(...)`.

- [ ] **Step 2: record/verify**

```bash
./gradlew :core:designsystem:recordRoborazziDebug
./gradlew :core:designsystem:verifyRoborazziDebug
```

- [ ] **Step 3: 커밋**

```bash
git add core/designsystem/src/test/
git commit -m "test(designsystem): expand Roborazzi coverage to shared primitives"
```

### Task 7.5: feature:station-list 4개 상태 골든

**Files:**
- Modify: `feature/station-list/build.gradle.kts`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`

- [ ] **Step 1: plugin 적용**

```kotlin
plugins {
    id("gasstation.android.library.compose")
    id("gasstation.android.hilt")
    id("gasstation.roborazzi")
}
```

- [ ] **Step 2: 테스트 (4 state)**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziStationListScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun empty_state() = renderAndCapture("empty.png", uiState = StationListUiState(/* empty */))

    @Test
    fun loading_with_cache_state() = renderAndCapture("loading-with-cache.png", uiState = sampleLoadingState)

    @Test
    fun stale_state() = renderAndCapture("stale.png", uiState = sampleStaleState)

    @Test
    fun error_state() = renderAndCapture("error.png", uiState = sampleErrorState)

    private fun renderAndCapture(name: String, uiState: StationListUiState) {
        composeRule.setContent {
            GasStationTheme {
                StationListScreen(uiState = uiState, onAction = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name")
    }
}
```

샘플 state는 같은 파일 또는 `StationListUiStateFixtures.kt` 보조 파일에 정의.

- [ ] **Step 3: record + verify**

```bash
./gradlew :feature:station-list:recordRoborazziDebug
./gradlew :feature:station-list:verifyRoborazziDebug
```

- [ ] **Step 4: 커밋**

```bash
git add feature/station-list/build.gradle.kts \
        feature/station-list/src/test/kotlin/ \
        feature/station-list/src/test/snapshots/
git commit -m "test(station-list): add Roborazzi golden for four UI states"
```

### Phase 7 검증

- [ ] `./gradlew verifyRoborazziDebug` 모든 모듈 통과
- [ ] 골든 PNG가 repo에 commit
- [ ] 의도적 UI 변경 시 record 명령으로 갱신, PR에 diff 노출

---

## Phase 8: Compose stability metrics + Baseline profile

### Task 8.1: Compose metrics 출력 활성화

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`

- [ ] **Step 1: compose compiler 옵션 추가**

각 Compose convention plugin의 android extension 또는 `composeCompiler` 블록에 추가:

```kotlin
composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
}
```

- [ ] **Step 2: 빌드 후 결과 확인**

```bash
./gradlew :feature:station-list:assembleDemoDebug
ls feature/station-list/build/compose-metrics/
```

Expected: `<module>-debug-classes.txt`, `<module>-debug-composables.txt`.

- [ ] **Step 3: 커밋 (plugin 변경)**

```bash
git add build-logic/convention/src/main/kotlin/
git commit -m "chore(compose): enable stability metrics output"
```

### Task 8.2: stability 리포트 commit

**Files:**
- Create: `docs/compose-metrics/core-designsystem.md`
- Create: `docs/compose-metrics/feature-station-list.md`
- Create: `docs/compose-metrics/feature-watchlist.md`
- Create: `docs/compose-metrics/feature-settings.md`

- [ ] **Step 1: 빌드 후 4개 모듈의 `classes.txt`, `composables.txt`를 docs/compose-metrics 안에 모듈별 md로 정리**

각 md는 다음 구조:

```markdown
# Compose Stability Metrics — <module>

> 측정 환경: AGP 9.1.1 / Kotlin 2.3.20 / Compose Compiler 2.3.20
> 생성 명령: `./gradlew :<module>:assembleDemoDebug`
> 측정일: 2026-05-XX

## Classes

\`\`\`
<classes.txt 내용 복사>
\`\`\`

## Composables

\`\`\`
<composables.txt 내용 복사>
\`\`\`

## Unstable 분류 대응

(unstable로 표시된 항목에 대한 결정과 후속 작업)
```

- [ ] **Step 2: unstable 항목 파악과 후속 결정**

가장 흔한 케이스: `List<X>`, `Map<K, V>` → `ImmutableList`/`ImmutableMap` 도입 또는 `@Stable` 마킹.

`feature:station-list`의 `StationListUiState`가 unstable로 분류되면 다음 중 하나로 처리:
- `kotlin.compose.stability.config` 파일에 도메인 enum/value 객체 등록
- `@Immutable` 어노테이션
- `kotlinx.collections.immutable.PersistentList` 사용

가장 비침습적인 방법으로 결정해 적용.

- [ ] **Step 3: 커밋**

```bash
git add docs/compose-metrics/
git commit -m "docs: snapshot Compose stability metrics for design + feature modules"
```

### Task 8.3: Baseline profile 생성

**Files:**
- Modify: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt` (이미 존재)
- Create: `app/src/main/baseline-prof.txt`
- Modify: `app/build.gradle.kts` (baselineProfile 의존성)

- [ ] **Step 1: 생성 명령**

(현재 benchmark module 구성에 따라 다름. 통상)

```bash
./gradlew :app:generateDemoBaselineProfile
```

또는

```bash
./gradlew :benchmark:connectedDemoBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

산출물을 `app/src/main/baseline-prof.txt`로 복사.

- [ ] **Step 2: app build.gradle.kts에 baselineProfile consumer 설정**

```kotlin
plugins {
    id("androidx.baselineprofile")
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

dependencies {
    "baselineProfile"(project(":benchmark"))
}
```

(이미 설정되어 있다면 생략)

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/baseline-prof.txt app/build.gradle.kts
git commit -m "perf: commit baseline profile generated from demo benchmark"
```

### Task 8.4: 측정값을 README에 반영

**Files:**
- Modify: `README.md`

- [ ] **Step 1: macrobenchmark로 cold/warm/hot startup 측정**

```bash
./gradlew :benchmark:connectedDemoBenchmarkAndroidTest
```

`benchmark/build/outputs/.../*.json` 또는 logcat 결과에서 startup ms 추출.

- [ ] **Step 2: README에 표 추가**

```markdown
## Startup metric (참고)

| 시나리오 | p50 | p95 |
| --- | --- | --- |
| Cold start | XXX ms | XXX ms |
| Warm start | XXX ms | XXX ms |
| Hot start | XXX ms | XXX ms |

측정 환경: Pixel 6 / Android 14 / `demo` flavor / 2026-05-XX 기준.
```

- [ ] **Step 3: 커밋**

```bash
git add README.md
git commit -m "docs(README): publish startup metrics from baseline profile run"
```

### Phase 8 검증

- [ ] `docs/compose-metrics/` 4 파일 존재
- [ ] `app/src/main/baseline-prof.txt` 존재
- [ ] README에 startup 표 채움

---

## Phase 9: Kover (커버리지)

### Task 9.1: Kover plugin 도입

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/GasStationKoverConventionPlugin.kt`

- [ ] **Step 1: catalog 등록**

```toml
[versions]
kover = "0.9.1"

[plugins]
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }

[libraries]
kover-gradlePlugin = { module = "org.jetbrains.kotlinx:kover-gradle-plugin", version.ref = "kover" }
```

`build-logic` 의존성:

```kotlin
implementation(libs.kover.gradlePlugin)
```

- [ ] **Step 2: convention plugin**

```kotlin
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class GasStationKoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            extensions.configure(KoverProjectExtension::class.java) { kover ->
                kover.reports {
                    it.filters {
                        it.excludes {
                            it.classes(
                                "*Hilt_*",
                                "*_HiltModules*",
                                "*_Factory*",
                                "*_Provide*",
                                "*ComposableSingletons*",
                                "*Preview*Kt",
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: register**

```kotlin
register("kover") {
    id = "gasstation.kover"
    implementationClass = "GasStationKoverConventionPlugin"
}
```

- [ ] **Step 4: 모든 모듈 convention plugin에 `pluginManager.apply("gasstation.kover")` 추가**

(Spotless와 같은 위치)

- [ ] **Step 5: root build.gradle.kts에 plugin 적용**

```kotlin
plugins {
    alias(libs.plugins.kover) apply false
}
```

- [ ] **Step 6: 통합 리포트 생성**

```bash
./gradlew koverXmlReport koverHtmlReport
```

`build/reports/kover/html/index.html` 생성 확인.

- [ ] **Step 7: 커밋**

```bash
git add gradle/libs.versions.toml build.gradle.kts build-logic/convention/
git commit -m "chore(coverage): enable Kover with shared excludes"
```

### Phase 9 검증

- [ ] `./gradlew koverXmlReport`로 `build/reports/kover/report.xml` 생성

---

## Phase 10: CI 통합

### Task 10.1: workflow job 분리

**Files:**
- Modify: `.github/workflows/android.yml`

- [ ] **Step 1: 신규 workflow**

```yaml
name: Android CI

on:
  pull_request:
  push:
    branches:
      - main

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  static-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: spotlessCheck + lint
        run: ./gradlew spotlessCheck lint

  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Unit tests
        run: |
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
            :tools:demo-seed:test

  screenshot-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Screenshot verify
        run: ./gradlew verifyRoborazziDebug
      - name: Upload screenshot diffs on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: roborazzi-diffs
          path: |
            **/build/outputs/roborazzi/**

  assemble:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Assemble demo + prod (debug + release)
        run: |
          ./gradlew \
            :app:assembleDemoDebug \
            :app:assembleProdDebug \
            :app:assembleProdRelease \
            :benchmark:assemble

  coverage:
    runs-on: ubuntu-latest
    needs: [unit-tests]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Kover XML report
        run: ./gradlew koverXmlReport
      - name: Upload to Codecov
        if: ${{ secrets.CODECOV_TOKEN != '' }}
        uses: codecov/codecov-action@v4
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: build/reports/kover/report.xml
```

- [ ] **Step 2: 로컬 syntax 검증**

```bash
yq '.jobs | keys' .github/workflows/android.yml
```

Expected: `[static-analysis, unit-tests, screenshot-tests, assemble, coverage]`.

- [ ] **Step 3: 커밋**

```bash
git add .github/workflows/android.yml
git commit -m "ci: split workflow into static-analysis, tests, screenshot, assemble, coverage"
```

### Task 10.2: docs/verification-matrix.md 갱신

**Files:**
- Modify: `docs/verification-matrix.md`

- [ ] **Step 1: 신규 표준 명령 반영**

머지 전 권장 명령 섹션을 다음으로 갱신:

```bash
./gradlew \
  spotlessCheck lint \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyRoborazziDebug \
  koverXmlReport \
  :app:assembleProdRelease
```

- [ ] **Step 2: 커밋**

```bash
git add docs/verification-matrix.md
git commit -m "docs(verification): align matrix with v1.1 CI commands"
```

### Phase 10 검증

- [ ] PR 페이지에서 5개 job이 보이고 모두 green (실제 PR 후 확인)

---

## Phase 11: 보안 trade-off + 버전 bump + release

### Task 11.1: docs/security-trade-offs.md 신설

**Files:**
- Create: `docs/security-trade-offs.md`
- Modify: `README.md`, `AGENTS.md` (해당 단락을 본 문서로 링크)

- [ ] **Step 1: 본문**

```markdown
# 보안 trade-off 단일 출처

> 작성일: 2026-05-XX
> 적용 버전: 1.1.0 이후

이 문서는 GasStation이 의도적으로 수용한 보안 trade-off와 각 결정의 한계, 승격 조건을 모은 단일 출처다.

## 1. Opinet API 키를 client BuildConfig에 두는 결정

**현 상태**: `prod` flavor의 `app/build.gradle.kts`가 사용자별 `~/.gradle/gradle.properties`의 `opinet.apikey`를 `BuildConfig.OPINET_API_KEY`로 주입한다.

**한계**:
- APK 내부의 완전한 비밀 경계가 아니다. 디컴파일 시 추출 가능.
- key abuse / quota 초과 위험이 사용자 단말까지 분산된다.

**현재 수용 이유**:
- Opinet API는 개인 키별 quota를 가지며, 본 앱의 트래픽 규모에서 abuse 영향이 제한적이다.
- 별도 backend infra를 갖추기 전 단일 경계(BuildConfig) 노출로 단순함을 유지한다.

**승격 조건**:
- 공개 배포로 active install이 일정 규모를 넘는 경우.
- Opinet에서 quota 비용을 부과하기 시작하는 경우.
- 다른 API 통합이 늘어 client BuildConfig가 비밀 다발이 되는 경우.

**승격 시 변경**:
- `core:network`의 `NetworkRuntimeConfig`를 backend proxy URL로 교체.
- `app`의 BuildConfig 주입 제거.
- backend가 quota 모니터링, key restriction (referrer / app signature) 책임.

## 2. cleartext HTTP 화이트리스트

**현 상태**: `network_security_config.xml`이 `www.opinet.co.kr`만 cleartext 허용.

**한계**:
- 중간자 공격으로 가격 응답 위조 가능. 사용자 가격 비교의 정확성에 영향.

**현재 수용 이유**:
- Opinet 공식 엔드포인트가 https를 안정적으로 제공하지 않음.
- 가격 데이터는 PII가 아니며, 위조 시 영향이 사용자 결정 오류로 한정.

**승격 조건**:
- Opinet이 https를 공식 지원하거나 backend proxy 도입 시.

## 3. Android backup 비활성화

**현 상태**: `android:allowBackup="false"`. `data extraction` 대상에서 제외.

**근거**:
- 로컬 캐시, 가격 히스토리, watchlist, 설정은 단말 로컬 가치만 가진다.
- backup 활성화 시 cross-device로 stale 가격 데이터가 옮겨가 잘못된 비교 기반이 될 수 있다.

**승격 조건**:
- watchlist를 사용자 계정과 연동하는 cloud sync 기능 도입 시.

## 4. Certificate pinning 미도입

**현 상태**: OkHttp `CertificatePinner` 미사용.

**근거**:
- Opinet 인증서 회전 주기를 통제할 수 없어, pinning 오작동 시 사용자가 가격 비교를 못 하는 fatal failure가 됨.
- 현재 위협 모델에서 pinning 비용이 이익보다 큼.

**승격 조건**:
- backend proxy 경유로 자체 인증서 통제 가능 시.

## 5. CrashReporter는 추상화만, 실제 SDK 미연결

**현 상태**: `LogcatCrashReporter`가 prod에서 Timber로 기록.

**승격 시 변경**:
- `app/src/prod/.../CrashReporterModule.kt`의 binding을 Firebase Crashlytics / Sentry / Bugsnag 구현으로 교체.
- AndroidManifest 권한과 google-services.json 도입.

---

본 문서 갱신은 README 또는 AGENTS.md의 보안 한계 언급을 변경할 때마다 함께 검토한다.
```

- [ ] **Step 2: README/AGENTS의 보안 단락을 본 문서로 링크**

기존 README의 "현재 `prod` 키는 ..." 단락을 다음으로 축약:

```markdown
> `prod` 키는 Android 클라이언트 `BuildConfig`로 주입되며, 그 한계와 승격 조건은 [`docs/security-trade-offs.md`](docs/security-trade-offs.md)에 정리되어 있습니다. 앱은 로컬 캐시/설정을 Android backup 대상으로 내보내지 않습니다.
```

- [ ] **Step 3: 커밋**

```bash
git add docs/security-trade-offs.md README.md AGENTS.md
git commit -m "docs: introduce security trade-offs single source"
```

### Task 11.2: 버전 bump

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 변경**

```kotlin
defaultConfig {
    applicationId = "com.gasstation"
    versionCode = 4
    versionName = "1.1.0"
}
```

- [ ] **Step 2: assemble 확인**

```bash
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :app:assembleProdRelease
```

- [ ] **Step 3: 커밋**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.1.0 (versionCode 4)"
```

### Task 11.3: CHANGELOG 1.1.0 섹션

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: 상단에 1.1.0 섹션 추가**

```markdown
## 1.1.0 - 2026-05-XX

### 사용자 영향

- 영어 시스템 locale에서 영어 카피로 표시됩니다.
- 시작 속도가 baseline profile로 약 X% 향상되었습니다 (cold start 기준).

### 개발자 영향

- Spotless + ktlint convention plugin 도입. `./gradlew spotlessApply ktlintFormat`로 자동 포맷.
- Android lint를 모든 Android 모듈에서 `abortOnError = true`로 강제. `HardcodedText`는 error.
- Roborazzi 화면 회귀 도입. `core:designsystem` 핵심 primitive와 `feature:station-list` 4개 상태가 골든 PNG로 보호됨.
- `CrashReporter` 도메인 인터페이스 + flavor binding 도입. demo는 NoOp, prod는 Logcat 구현.
- Kover 통합 커버리지 리포트.
- Compose stability metrics를 `docs/compose-metrics/`에 commit.
- Baseline profile을 `app/src/main/baseline-prof.txt`로 commit.
- CI workflow를 static-analysis / unit-tests / screenshot-tests / assemble / coverage 5개 job으로 분리.
- 사용자 노출 문자열을 모두 `strings.xml`로 외부화. `StringResource` 추상화 도입.
- 한국어/영어 두 locale 자원 세트.

### 문서

- `docs/security-trade-offs.md` 신설.
- `docs/improvement-analysis.md`, `docs/deep-analysis-report.md`를 `docs/history/`로 이동.
- 프로덕트 정의를 "한국 운전자용 안드로이드 앱"으로 단일화.
- README에 영문 elevator, 배지, 5분 코드 투어, 데모 GIF 추가.

### 검증

```bash
./gradlew spotlessCheck ktlintCheck lint \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  verifyRoborazziDebug koverXmlReport \
  :app:assembleProdRelease
```

상세 릴리즈 노트는 [docs/release-notes/2026-05-XX-v1.1.0.md](docs/release-notes/2026-05-XX-v1.1.0.md)를 봅니다.
```

- [ ] **Step 2: 커밋**

```bash
git add CHANGELOG.md
git commit -m "docs(changelog): document 1.1.0 release"
```

### Task 11.4: release notes

**Files:**
- Create: `docs/release-notes/2026-05-XX-v1.1.0.md`

- [ ] **Step 1: 본문**

```markdown
# 1.1.0 릴리즈 노트 (2026-05-XX)

## 목표

GasStation을 정적 분석/화면 회귀/i18n/장애 보고/CI/문서 baseline을 갖춘 production 1.1 기준선으로 격상.

## 주요 변경

| 영역 | 변경 |
| --- | --- |
| 정적 분석 | Spotless + ktlint convention plugin, Android lint 엄격화 |
| 화면 회귀 | Roborazzi 도입, 6개 designsystem primitive + 4개 station-list state 골든 |
| i18n | strings.xml 외부화, StringResource 추상화, en strings 동반 |
| 장애 보고 | CrashReporter 인터페이스 + NoOp/Logcat 구현 |
| 커버리지 | Kover 통합 리포트 |
| Compose 품질 | stability metrics commit, baseline profile commit, startup metric README 노출 |
| CI | 5 job 분리, screenshot diff artifact, release minify 강제 |
| 문서 | 보안 trade-off 단일 출처, 1.0.2 분석 문서를 history로 이동, 제품 프레이밍 단일화 |
| 라이선스/OSS | MIT LICENSE, CONTRIBUTING, PR/Issue 템플릿, dependabot |

## 마이그레이션 영향

| 항목 | 영향 |
| --- | --- |
| 기존 사용자 데이터 | 변경 없음. Room schema 동일. |
| API 키 흐름 | 변경 없음. `opinet.apikey`는 여전히 사용자별 gradle.properties. |
| `demo` 빌드 | 변경 없음. deterministic seed 유지. |

## 검증 결과

`Verification Matrix` workflow 머지 전 그린.
```

- [ ] **Step 2: 커밋**

```bash
git add docs/release-notes/2026-05-XX-v1.1.0.md
git commit -m "docs: add 1.1.0 release notes"
```

### Task 11.5: 최종 검증

- [ ] **Step 1: 통합 명령 통과 확인**

```bash
./gradlew \
  spotlessCheck lint \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyRoborazziDebug \
  koverXmlReport \
  :app:assembleProdRelease
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: DoD 체크**

스펙의 Section 11 완료 정의 항목을 하나씩 검사.

```bash
# 1. 통합 명령
./gradlew spotlessCheck ktlintCheck lint verifyRoborazziDebug koverXmlReport :app:assembleProdRelease

# 2. OSS 파일
ls LICENSE CONTRIBUTING.md .github/PULL_REQUEST_TEMPLATE.md .github/ISSUE_TEMPLATE/ .github/dependabot.yml

# 3. 프레이밍 잔재 없음
rg -n "포트폴리오|portfolio|reference 앱|reviewer|interviewer|면접" -g '!docs/superpowers/**' -g '!docs/history/**' -g '!docs/release-notes/**' || echo "OK: 0 matches"

# 4. ko literal 잔존 없음
rg -n "\"[가-힣]" --type kt -g '!*/test/**' -g '!*/androidTest/**' feature app || echo "OK: 0 matches"

# 5. baseline profile
ls -la app/src/main/baseline-prof.txt

# 6. compose metrics
ls docs/compose-metrics/

# 9. security doc
ls docs/security-trade-offs.md

# 10. 잔재 디렉토리 없음
test ! -d core/common && echo "core/common removed"
test ! -d core/ui && echo "core/ui removed"
```

모두 OK이어야 함.

- [ ] **Step 3: PR 생성**

```bash
git push -u origin chore/production-baseline-v1.1
gh pr create --title "chore: production baseline v1.1.0" --body "$(cat <<'EOF'
## Summary

- GasStation 1.0.2 → 1.1.0 production baseline 격상
- 11 phase, 50+ task 일괄 머지
- 상세: `docs/superpowers/specs/2026-05-11-production-baseline-design.md`
- 구현 history: `docs/superpowers/plans/2026-05-11-production-baseline.md`

## Test plan

- [x] `./gradlew spotlessCheck ktlintCheck lint verifyRoborazziDebug koverXmlReport :app:assembleProdRelease`
- [x] CI 5 job green
- [x] DoD 10개 항목 모두 통과

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 부록 A: phase별 추정 소요

| Phase | 추정 |
| --- | --- |
| 0 | 5분 |
| 1 | 30분 |
| 2 | 1.5시간 (spotless 적용 후 대량 diff 검토 포함) |
| 3 | 1.5시간 (문서 8개 검토) |
| 4 | 4시간 (문자열 외부화 + 영어 카피) |
| 5 | 1.5시간 |
| 6 | 1시간 (GIF 캡처 별도) |
| 7 | 4시간 (Roborazzi 도입 + 골든 11장) |
| 8 | 2시간 |
| 9 | 1시간 |
| 10 | 1시간 |
| 11 | 1시간 |
| **합계** | **약 20시간** |

## 부록 B: 롤백 계획

- Phase 단위로 branch tag 또는 commit hash를 기록. 문제 발생 시 `git reset --hard <phase-end-hash>`.
- Spotless reformat은 함수적 변경이 없으므로 안전. Roborazzi 골든은 record로 재생성 가능.
- CrashReporter 도입 후 의도치 않은 throwable 흐름 변화가 있는지는 `:data:station:testDebugUnitTest` 회귀에서 잡힘.

## 부록 C: 단일 명령 부트스트랩

새 기여자는 다음을 실행:

```bash
./gradlew spotlessApply ktlintFormat \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  verifyRoborazziDebug
```

3분 안에 빌드/포맷/단위/화면 회귀를 모두 확인할 수 있다.
