# 모듈 경계 가드 + 변이 게이트 (검증 깊이 II) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 직전 `2026-06-06-verification-depth-hardening` 플랜(pitest report-only)을 이어, (1) 문서로만 존재하던 의도된 모듈 경계를 코드 가드로 고정하고, (2) 변이 테스트를 `domain` 계층 전체로 확장하며 `domain:station`을 회귀 floor 게이트로 승격한다. **사용자 대면 동작과 모듈 그래프는 불변** — 기존 설계를 "바꾸지 않고 지키는" 검증만 추가한다.

**Architecture:** 세 개의 독립 트랙을 각각 별도 commit으로 추가한다.
- **Track C(경계 가드)**: root `build.gradle.kts`에 `verifyModuleBoundaries` Gradle 태스크를 추가해 `docs/module-contracts.md`/`docs/architecture.md`의 의도된 경계(의도된 `core:location → domain:location` 예외 포함)를 검증하고, 빠르므로 CI `static-analysis` job에 넣는다.
- **Track A(변이 확장)**: 순수 JVM 모듈 `domain:settings`, `domain:location`에 pitest를 report-only로 붙여 결함 탐지력 베이스라인을 캡처하고, 살아남은 mutant를 보강한다.
- **Track B(변이 게이트)**: 베이스라인이 확보된 `domain:station`을 report-only에서 보수적 회귀 floor 게이트(`mutationThreshold`)로 승격한다. 느리므로 CI에 넣지 않고 온디맨드 유지.

**Tech Stack:** Gradle 9.3.1 Kotlin DSL (config-cache 활성), 버전 카탈로그(`gradle/libs.versions.toml`), `info.solidsoft.pitest` 1.19.0(직전 플랜에서 카탈로그 등록 완료, alias `libs.plugins.pitest`), GitHub Actions.

**기준 사실 (구현 전 확인됨):**
- `domain:station`, `domain:settings`, `domain:location`은 모두 `gasstation.jvm.library` 컨벤션의 순수 JVM 모듈. 패키지는 각각 `com.gasstation.domain.station|settings|location`. pitest는 Android 모듈에서 불안정하므로 JVM 한정.
- `domain:station` 변이 베이스라인(직전 플랜, `docs/test-strategy.md:94`): 보강 후 `Killed 28/60 (47%)`, **test strength 97%**, SURVIVED 1(= `StationPriceDelta.from`의 `<` 경계 동등 변이, 추가 테스트로 못 잡음). `no-coverage` 변이 31건 때문에 overall %가 낮음.
- `domain:station/build.gradle.kts`에는 이미 `alias(libs.plugins.pitest)` + `pitest {}`(report-only, `// report-only: mutationThreshold 게이트를 두지 않는다.` 주석)가 있다.
- root `build.gradle.kts` 구조: 최상단 `import` → `buildscript {}` → `plugins {}` → `dependencies {}`(kover) → `kover {}` → `isNonStable()` + `DependencyUpdatesTask` 설정 순. 파일 첫 줄에 `import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask` 존재.
- `gradle.properties`에 `org.gradle.configuration-cache=true`. → 경계 가드는 **설정 시점에 production `api`/`implementation` 프로젝트 의존성만 `consumer|target` String 목록으로 캡처**하고, 실행 시점에는 `@Input`으로 주입된 값만 읽는 typed task로 만든다(실행 시점 `Project` 접근 및 script-level 컬렉션 클로저 캡처 금지).
- CI(`.github/workflows/android.yml`) `static-analysis` job은 `./gradlew spotlessCheck lint --continue` 실행(파일 27번째 줄).
- 현재 production 모듈 그래프(`api`/`implementation`)는 아래 가드 규칙을 **모두 통과**한다(구현 전 GREEN 상태가 기대값). 테스트 전용 `testImplementation(project(...))`는 테스트 보조 의존이므로 이번 가드 범위 밖이다.

**코드 대조 검증 (2026-06-06, 실제 파일 확인):**
- root `build.gradle.kts`: 1번째 줄 `import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask`, 마지막 블록은 77~79번째 줄 `tasks.withType<DependencyUpdatesTask>().configureEach { ... }`, 파일은 80번째 빈 줄로 끝. → Track C import는 1번째 줄 아래, typed task class는 `isNonStable()` 앞, 규칙/캡처/등록은 79번째 줄 다음에 붙인다.
- `gasstation.jvm.library` 컨벤션(`build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`)이 `testImplementation(libs.kotlin-test)`를 주입한다. `domain:station`이 **추가 junit 선언 없이** 이 설정만으로 pitest를 이미 돌리고 있으므로(베이스라인 28/60 존재), `domain:settings`/`domain:location`도 동일하게 동작한다. → A.1/A.3의 `testImplementation(libs.junit)` 폴백은 **거의 불필요**(만일을 위한 안전망).
- 카탈로그(`gradle/libs.versions.toml`): `pitest = { id = "info.solidsoft.pitest", version = "1.19.0" }`(111번째 줄), `kotlin-test`(48번째 줄) 등록 확인. → 카탈로그 변경 없음.
- `domain:settings/build.gradle.kts`는 `api(project(":core:model"))` + coroutines/inject + turbine/coroutines-test. `domain:location/build.gradle.kts`는 `implementation(project(":core:model"))` + coroutines/inject + coroutines-test. 둘 다 `plugins { id("gasstation.jvm.library") }`만 선언(pitest 미적용). → A.1/A.3가 alias만 추가하면 됨.
- 테스트 클래스 실존 확인: `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UpdateSettingsUseCasesTest.kt`, `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt`; `domain/location/src/test/kotlin/com/gasstation/domain/location/LocationUseCasesTest.kt`, `domain/location/src/test/kotlin/com/gasstation/domain/location/AddressLabelNormalizerTest.kt`.
- `docs/verification-matrix.md`, `CHANGELOG.md` 실존 → Track D는 신설이 아니라 수정.
- `docs/test-strategy.md`의 report-only 문장은 96번째 줄(Track B.3 편집 대상)과 정확히 일치. 베이스라인(94번째 줄) `Killed 28/60 (47%)`/test strength 97%/SURVIVED 1/no-coverage 31도 일치.
- use case 본문 직접 확인: `UpdateFuelTypeUseCase`/`UpdateSearchRadiusUseCase`는 `updateUserPreferences { current.copy(...) }` 위임(세터 없음). `UpdateSettingsUseCasesTest.kt`가 도달값을, `UserPreferencesTest.kt`가 5개 기본값을 이미 단언 → A.2 SURVIVED는 0 기대(정상 경로는 건너뜀). `AddressLabelNormalizer.kt`는 26번째 줄 set·38번째 줄 `indexOfLast`·42/46번째 줄 districtIndex 분기 모두 문서 기술과 일치.
- 2026-06-06 재검증: 현재 기준 커밋은 `c1e1f26`이고 `./gradlew :domain:settings:test :domain:location:test --console=plain`은 BUILD SUCCESSFUL. 단, 기존 C.1 초안의 `doLast { capturedModuleEdges... }` 방식은 config-cache에서 script object/Project 그래프 캡처 리스크가 있어 typed task + 단순 String `@Input` 방식으로 수정한다.
- 편집 앵커 실재 확인: `module-contracts.md` 11번째 줄 `- `core:*`는 ... 값 객체만 둡니다.`(C.4 삽입 기준점), `android.yml` 27번째 줄 `run: ./gradlew spotlessCheck lint --continue`(C.3 교체 대상), `verification-matrix.md` 107~118번째 줄 "검증 깊이 측정" 섹션(D.1 추가 위치), `CHANGELOG.md` 5번째 줄 `## Unreleased`(D.2 추가 위치) 모두 존재. 컨벤션 플러그인 36번째 줄 `add("testImplementation", libs.findLibrary("kotlin-test").get())` 확인 → A.1/A.3 junit 폴백 거의 불필요.

**강의 → 작업 매핑 (이 플랜의 학습 동기):**

| 강의 | 작업 | 적용 포인트 |
| --- | --- | --- |
| [멀티모듈 아키텍처 Kotlin&Spring (337692)](https://www.inflearn.com/courses/lecture?courseId=337692) | Track C | 모듈 경계/네이밍 일관성을 코드 가드로 고정 |
| [오브젝트 - 설계 원칙편 (336658)](https://www.inflearn.com/courses/lecture?courseId=336658) | Track C | DIP·의존성 방향을 가드 규칙으로 명문화 |
| [이펙티브 자바 1부 (328628)](https://www.inflearn.com/courses/lecture?courseId=328628) | Track C 주석 | `api` vs `implementation` 노출은 **의도된 결정**이므로 가드가 깨지 않게 보존(F3) |
| [Practical Testing (329295)](https://www.inflearn.com/courses/lecture?courseId=329295) | Track A·B | 변이로 테스트 결함 탐지력 측정 → floor 게이트로 회귀 차단 |

> **설계 전제(중요):** F1(`core:location`이 데이터 역할), F3(`api` 노출)은 `docs/architecture.md:88,97`·`docs/module-contracts.md:22,29`에 **의도된 결정으로 명시**돼 있다. 이 플랜은 그 결정을 **바꾸지 않는다.** Track C 가드는 의도된 `core:location → domain:location` 예외를 규칙에서 제외해 보존한다.

---

## Track C: 모듈 경계 가드 (CI 차단)

가장 작고 빠르며 위험이 없다. 먼저 구현해 독립 commit한다.

**Files:**
- Modify: `build.gradle.kts` (root — import + 규칙/캡처 + `verifyModuleBoundaries` 태스크)
- Modify: `.github/workflows/android.yml` (`static-analysis` job에 태스크 추가)
- Modify: `docs/module-contracts.md` (가드 명령 한 줄 추가)

### Task C.1: root build에 config-cache 안전한 경계 가드 태스크 추가

- [ ] **Step 1: root `build.gradle.kts` 최상단에 import 추가**

기존 첫 줄(`import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask`) **바로 아래**에 추가:

```kotlin
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
```

- [ ] **Step 2: `isNonStable()` 함수 바로 위에 typed task class 추가**

`doLast` 클로저가 script-level 컬렉션을 직접 캡처하지 않게 한다. 실행 시점에는 `@Input`으로 직렬화된 String 값만 읽는다.

```kotlin
abstract class VerifyModuleBoundariesTask : DefaultTask() {
    @get:Input
    var forbiddenEdges: List<String> = emptyList()

    @get:Input
    var moduleEdges: List<String> = emptyList()

    @get:Input
    var moduleCount: Int = 0

    @TaskAction
    fun verify() {
        val rules = forbiddenEdges.map { encoded ->
            val parts = encoded.split("|", limit = 3)
            require(parts.size == 3) { "Invalid module boundary rule: $encoded" }
            ForbiddenModuleEdge(
                consumerPrefix = parts[0],
                targetPrefix = parts[1],
                reason = parts[2],
            )
        }
        val violations = mutableListOf<String>()
        moduleEdges.forEach { encodedEdge ->
            val edgeParts = encodedEdge.split("|", limit = 2)
            require(edgeParts.size == 2) { "Invalid module dependency edge: $encodedEdge" }
            val consumerPath = edgeParts[0]
            val dependencyPath = edgeParts[1]
            rules.forEach { rule ->
                if (
                    consumerPath.startsWith(rule.consumerPrefix) &&
                    dependencyPath.startsWith(rule.targetPrefix)
                ) {
                    violations += "$consumerPath -> $dependencyPath  (${rule.reason})"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("모듈 경계 위반 ${violations.size}건 (docs/module-contracts.md 참조):")
                    violations.sorted().forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle("모듈 경계 OK: 금지된 production 의존성 엣지 없음 (${moduleCount}개 모듈 검사).")
    }

    private data class ForbiddenModuleEdge(
        val consumerPrefix: String,
        val targetPrefix: String,
        val reason: String,
    )
}
```

- [ ] **Step 3: 파일 맨 끝(`tasks.withType<DependencyUpdatesTask>` 블록 다음)에 규칙·캡처·태스크 등록 추가**

```kotlin
// === 모듈 경계 가드 ===
// docs/module-contracts.md / docs/architecture.md 의 "의도된" 모듈 경계를 코드로 고정한다.
// 의도된 예외: core:location -> domain:location (위치를 플랫폼 인프라로 둔 결정, architecture.md:97).
//   core:location 은 아래 소비자 prefix 목록에 없으므로 제약되지 않는다.
//   F1(core:location 데이터 역할)/F3(api 노출)은 "고칠 결함"이 아니라 가드가 지켜야 할 의도된 규칙이다.
// 형식: "소비 모듈 path prefix|금지된 대상 모듈 path prefix|위반 사유"
val forbiddenModuleEdges = listOf(
    ":feature:|:core:location|feature는 위치 인프라를 직접 호출하지 않고 domain:location을 경유한다",
    ":feature:|:core:network|feature는 네트워크를 직접 다루지 않는다",
    ":feature:|:core:database|feature는 Room을 직접 다루지 않는다",
    ":feature:|:core:datastore|feature는 DataStore를 직접 다루지 않는다",
    ":feature:|:data:|feature는 저장소 구현이 아니라 domain 계약에만 의존한다",
    ":data:|:core:location|data는 위치 인프라에 의존하지 않는다 (위치는 feature→domain→core:location)",
    ":data:|:feature:|data는 화면 계층을 알지 못한다",
    ":domain:|:data:|domain은 구현 세부를 모른다",
    ":domain:|:feature:|domain은 화면 계층을 모른다",
    ":domain:|:core:location|domain은 Android 위치 인프라를 모른다",
    ":domain:|:core:network|domain은 네트워크 구현을 모른다",
    ":domain:|:core:database|domain은 Room을 모른다",
    ":domain:|:core:datastore|domain은 DataStore를 모른다",
    ":domain:|:core:designsystem|domain은 UI를 모른다",
    ":core:model|:domain:|core:model은 도메인 계층을 모른다",
    ":core:model|:data:|core:model은 데이터 계층을 모른다",
    ":core:network|:domain:|core:network은 도메인 계층을 모른다",
    ":core:observability|:domain:|core:observability는 도메인 계층을 모른다",
)

// config-cache 안전: 자식 프로젝트를 먼저 평가해 production 선언 의존성(api/implementation)을
// "consumer|target" String 으로만 캡처하고, 실행 시점에는 task @Input 값만 읽는다.
evaluationDependsOnChildren()
val capturedModuleEdges: List<String> = subprojects.flatMap { sp ->
    sp.configurations
        .filter { it.name == "implementation" || it.name == "api" }
        .flatMap { cfg -> cfg.dependencies.withType(ProjectDependency::class.java) }
        .map { "${sp.path}|${it.path}" }
        .distinct()
}
val capturedModuleCount = subprojects.size

tasks.register<VerifyModuleBoundariesTask>("verifyModuleBoundaries") {
    group = "verification"
    description = "docs/module-contracts.md 의 의도된 모듈 경계를 검증한다 (의도된 core:location→domain:location 예외 제외)."
    forbiddenEdges = forbiddenModuleEdges
    moduleEdges = capturedModuleEdges
    moduleCount = capturedModuleCount
}
```

- [ ] **Step 4: 현재 그래프에서 GREEN 통과 확인**

Run: `./gradlew verifyModuleBoundaries --console=plain`
Expected: BUILD SUCCESSFUL, 마지막 줄에 `모듈 경계 OK: 금지된 production 의존성 엣지 없음 (18개 모듈 검사).`

- [ ] **Step 5: config-cache 호환 확인 (저장 + 재사용)**

Run: `./gradlew verifyModuleBoundaries --console=plain && ./gradlew verifyModuleBoundaries --console=plain`
Expected: 두 번째 실행에서 `Reusing configuration cache.` 출력, BUILD SUCCESSFUL. (config-cache 문제 보고가 없어야 한다.)

### Task C.2: 가드가 위반을 실제로 잡는지 음성 검증

- [ ] **Step 1: 임시로 금지된 엣지 주입**

`data/station/build.gradle.kts`의 `dependencies {}` 블록 첫 줄에 임시로 추가:

```kotlin
    implementation(project(":core:location"))
```

- [ ] **Step 2: 가드가 FAIL 하는지 확인**

Run: `./gradlew verifyModuleBoundaries --console=plain`
Expected: BUILD FAILED. 메시지에 `모듈 경계 위반 1건` 과 `:data:station -> :core:location  (data는 위치 인프라에 의존하지 않는다 ...)` 포함.

- [ ] **Step 3: 임시 엣지 제거(원복)**

`data/station/build.gradle.kts`에서 Step 1에 추가한 `implementation(project(":core:location"))` 줄을 삭제한다.

- [ ] **Step 4: 다시 GREEN 확인**

Run: `./gradlew verifyModuleBoundaries --console=plain`
Expected: BUILD SUCCESSFUL, `모듈 경계 OK ...`.

### Task C.3: CI static-analysis job에 가드 연결

- [ ] **Step 1: `.github/workflows/android.yml`의 static-analysis step 이름과 실행 줄 수정**

다음 step 이름을

```yaml
      - name: Spotless + Lint
```

다음으로 교체:

```yaml
      - name: Spotless + Lint + Module Boundaries
```

다음 줄을

```yaml
        run: ./gradlew spotlessCheck lint --continue
```

다음으로 교체:

```yaml
        run: ./gradlew spotlessCheck lint verifyModuleBoundaries --continue
```

- [ ] **Step 2: 워크플로 YAML 문법 검증(로컬)**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/android.yml'))" && echo OK`
Expected: `OK`.

### Task C.4: 가드 문서화 + commit

- [ ] **Step 1: `docs/module-contracts.md` 공통 규칙 섹션에 한 줄 추가**

`## 공통 규칙` 블록의 마지막 불릿(`- `core:*`는 여러 모듈이 공유하는 인프라와 값 객체만 둡니다.`) 바로 아래에 추가:

```markdown
- 위 경계는 `./gradlew verifyModuleBoundaries`(CI `static-analysis` 포함)로 강제합니다. 의도된 예외는 `core:location → domain:location` 하나이며, 가드 규칙에서 제외돼 있습니다.
```

- [ ] **Step 2: 포맷 회귀 확인**

Run: `./gradlew spotlessCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: commit**

```bash
git add build.gradle.kts .github/workflows/android.yml docs/module-contracts.md
git commit -m "$(cat <<'EOF'
chore: enforce intended module boundaries via verifyModuleBoundaries

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

**Track C 검증:**
```bash
./gradlew verifyModuleBoundaries
```

---

## Track A: domain 계층 변이 테스트 확장 (report-only)

`domain:settings`, `domain:location`에 pitest를 붙여 결함 탐지력 베이스라인을 캡처한다. report-only/온디맨드, CI 미포함. pitest 플러그인 카탈로그 항목(`libs.plugins.pitest`)은 직전 플랜에서 등록 완료라 **카탈로그 변경 없음**.

**Files:**
- Modify: `domain/settings/build.gradle.kts` (plugin alias + `pitest {}`)
- Modify: `domain/location/build.gradle.kts` (plugin alias + `pitest {}`)
- (조건부) Modify: 각 모듈 `src/test/...` (살아남은 mutant 보강)
- Modify: `docs/test-strategy.md` (두 모듈 점수 기록)

### Task A.1: domain:settings pitest 적용 + 베이스라인 캡처

- [ ] **Step 1: `domain/settings/build.gradle.kts`의 `plugins {}`에 alias 추가**

기존:

```kotlin
plugins {
    id("gasstation.jvm.library")
}
```

수정:

```kotlin
plugins {
    id("gasstation.jvm.library")
    alias(libs.plugins.pitest)
}
```

- [ ] **Step 2: 같은 파일 끝(`dependencies {}` 다음)에 `pitest {}` 추가**

```kotlin
pitest {
    targetClasses.set(setOf("com.gasstation.domain.settings.*"))
    targetTests.set(setOf("com.gasstation.domain.settings.*"))
    threads.set(2)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // report-only: 베이스라인 캡처 단계. 게이트는 점수 안정화 후 domain:station처럼 별도 결정.
}
```

- [ ] **Step 3: 변이 테스트 실행 + 점수 캡처**

Run: `./gradlew :domain:settings:pitest --console=plain`
Expected: BUILD SUCCESSFUL. 콘솔 끝에 `>> Generated N mutations Killed K (X%)` 출력. 이 X%와 `>> Line Coverage` 값을 기록한다.

  - **만약 `0 tests` / `no mutations`로 끝나면**(JUnit 백엔드 미해석): `domain/settings/build.gradle.kts`의 `dependencies {}`에 `testImplementation(libs.junit)` 한 줄을 추가하고 Step 3을 재실행한다. (CI `unit-tests`에서 `:domain:settings:test`가 통과하므로 보통 불필요.)

- [ ] **Step 4: 리포트 생성 확인**

Run: `ls domain/settings/build/reports/pitest/`
Expected: `index.html`(및 `mutations.xml`) 존재.

### Task A.2: domain:settings 살아남은 mutant 보강 (조건부)

- [ ] **Step 1: SURVIVED 개수 확인**

Run: `grep -c 'status="SURVIVED"' domain/settings/build/reports/pitest/mutations.xml 2>/dev/null || echo 0`

  - **출력이 `0`이면 이 Task 전체를 건너뛴다**(보강 불필요).

- [ ] **Step 2: SURVIVED 변이 1~2건을 잡는 테스트 추가**

**코드 구조상 예상(실제 본문 확인됨):** `domain:settings`의 변이 표면은 매우 좁다. `model/UserPreferences.kt`는 5개 필드 + `default()` 팩토리만 있는 data class이고, 6개 use case는 모두 `settingsRepository.updateUserPreferences { current -> current.copy(필드 = 인자) }` 형태의 얇은 위임이다(`setX(...)` 같은 세터 호출은 없다). 분기·반복·산술이 없어 pitest가 만들 변이 자체가 거의 없다. 게다가 보강 대상이 될 두 지점이 **이미 단언돼 있다**:
- **인자 전달**: `UpdateSettingsUseCasesTest.kt`는 `UpdateFuelTypeUseCase(repository)(FuelType.DIESEL)` 후 `assertEquals(FuelType.DIESEL, repository.current.fuelType)`로, radius/brandFilter/mapProvider도 같은 방식으로, sortOrder는 transform 경로로 **이미 도달값을 단언**한다. → `copy(...)` 할당을 바꾸는 변이는 이미 죽는다.
- **`UserPreferences.default()`**: `UserPreferencesTest.kt`의 `defaults stay aligned ...`가 5개 기본값을 모두 `assertEquals`로 단언한다. → 기본값 치환 변이도 이미 죽는다.

따라서 **Step 1의 SURVIVED는 0일 가능성이 매우 높고, 이 Task는 통째로 건너뛰는 것이 정상 경로다.** 만에 하나 리포트에 SURVIVED가 남으면, `mutations.xml`의 `status="SURVIVED"`(또는 `index.html`)가 가리키는 정확한 클래스/라인을 먼저 확인하고, **그 라인에 한정해** 기존 스타일(`kotlin.test` `assertEquals`, Turbine, coroutines-test)로 신규 입력 케이스만 추가한다. 기존 동작 계약은 바꾸지 않는다.

- [ ] **Step 3: 테스트 통과 확인**

Run: `./gradlew :domain:settings:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 점수 재측정(동일 또는 상승)**

Run: `./gradlew :domain:settings:pitest --console=plain`
Expected: BUILD SUCCESSFUL, 변이 점수가 Task A.1 대비 동일하거나 상승.

### Task A.3: domain:location pitest 적용 + 베이스라인 캡처

- [ ] **Step 1: `domain/location/build.gradle.kts`의 `plugins {}`에 alias 추가**

기존:

```kotlin
plugins {
    id("gasstation.jvm.library")
}
```

수정:

```kotlin
plugins {
    id("gasstation.jvm.library")
    alias(libs.plugins.pitest)
}
```

- [ ] **Step 2: 같은 파일 끝(`dependencies {}` 다음)에 `pitest {}` 추가**

```kotlin
pitest {
    targetClasses.set(setOf("com.gasstation.domain.location.*"))
    targetTests.set(setOf("com.gasstation.domain.location.*"))
    threads.set(2)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // report-only: 베이스라인 캡처 단계. 게이트는 점수 안정화 후 별도 결정.
}
```

- [ ] **Step 3: 변이 테스트 실행 + 점수 캡처**

Run: `./gradlew :domain:location:pitest --console=plain`
Expected: BUILD SUCCESSFUL. `>> Generated N mutations Killed K (X%)`의 X%와 Line Coverage 기록.

  - **`0 tests`/`no mutations`로 끝나면**: `domain/location/build.gradle.kts`의 `dependencies {}`에 `testImplementation(libs.junit)` 추가 후 재실행.

- [ ] **Step 4: 리포트 생성 확인**

Run: `ls domain/location/build/reports/pitest/`
Expected: `index.html`(및 `mutations.xml`) 존재.

### Task A.4: domain:location 살아남은 mutant 보강 (조건부)

- [ ] **Step 1: SURVIVED 개수 확인**

Run: `grep -c 'status="SURVIVED"' domain/location/build/reports/pitest/mutations.xml 2>/dev/null || echo 0`

  - **출력이 `0`이면 이 Task 전체를 건너뛴다.**

- [ ] **Step 2: SURVIVED 변이 1~2건을 잡는 테스트 추가**

**코드 구조상 예상:** `domain:location`의 변이 표면 대부분은 `AddressLabelNormalizer.kt`다(분기·경계가 많아 SURVIVED 후보가 집중됨). use case들은 repository 위임이라 얇다. 기존 `AddressLabelNormalizerTest.kt`는 4개 케이스로 (a) 도로명→행정동, (b) 국가코드/건물동 무시, (c) 분리된 시 토큰 결합(특별시), (d) 행정동 없을 때 원본 반환을 덮는다. 아직 **안 덮인** 분기 → 변이가 살아남기 쉬운 곳:

- **`joinSplitAdministrativeTokens`의 set 멤버(26번째 줄 `setOf("특별시", "광역시", "특별자치시", "특별자치도")`)**: 기존 테스트는 `특별시`만 친다. `광역시`/`특별자치시`/`특별자치도` 멤버를 set에서 빼는 변이는 살아남는다. → 케이스 추가:
  ```kotlin
  @Test
  fun `joins metropolitan and special self-governing region tokens`() {
      assertEquals("부산광역시 해운대구 우동", normalizeCurrentAddressLabel("부산 광역시 해운대구 우동"))
      assertEquals("세종특별자치시 한솔동", normalizeCurrentAddressLabel("세종 특별자치시 한솔동"))
  }
  ```
- **`toAdministrativeDongLabel`의 `districtIndex >= 0` else 분기(42·46번째 줄)**: 구/군이 **없는** 입력은 기존 테스트에 없다. 세종처럼 구가 없는 경우 else로 가 시/도 바로 앞을 찾는다. 위 `세종특별자치시 한솔동` 케이스가 이 경로(districtIndex < 0 → 시/도 탐색)도 함께 친다.
- **`indexOfLast`(38번째 줄, `indexOfFirst` 아님)**: 행정동 토큰이 **둘 이상**일 때 마지막을 골라야 한다. 기존 테스트의 "동 상가"는 단일 글자 "동"이라 `isAdministrativeDongPart`(`dropLast(1).any { '가'..'힣' }`)를 통과 못 해 실제 행정동이 아니다. → 진짜 행정동 두 개를 둔 케이스로 `indexOfLast`를 고정:
  ```kotlin
  @Test
  fun `picks the last administrative dong when several are present`() {
      assertEquals("서울특별시 강남구 역삼동", normalizeCurrentAddressLabel("서울특별시 강남구 삼성동 인근 서울특별시 강남구 역삼동"))
  }
  ```

Step 1의 SURVIVED 리포트가 가리키는 실제 라인에 맞춰 위 후보 중 1~2개를 `AddressLabelNormalizerTest.kt`에 추가한다. 모두 **기존 동작 계약을 바꾸지 않는** 신규 입력 케이스다. 기존 스타일(`kotlin.test`의 `assertEquals`) 유지.

- [ ] **Step 3: 테스트 통과 확인**

Run: `./gradlew :domain:location:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 점수 재측정(동일 또는 상승)**

Run: `./gradlew :domain:location:pitest --console=plain`
Expected: BUILD SUCCESSFUL, 점수가 Task A.3 대비 동일하거나 상승.

### Task A.5: 두 모듈 점수 문서화 + commit

- [ ] **Step 1: `docs/test-strategy.md`의 Mutation testing 섹션 보강**

`## Mutation testing (변이 테스트)` 섹션에 `domain:station` 항목 아래로 다음을 추가:
- **`domain:settings` 베이스라인(2026-06-06):** Task A.1에서 캡처한 `Killed K/N (X%)`, test strength, SURVIVED 개수. (Task A.2를 했다면) 보강한 테스트 요약. report-only.
- **`domain:location` 베이스라인(2026-06-06):** Task A.3에서 캡처한 `Killed K/N (X%)`, test strength, SURVIVED 개수. (Task A.4를 했다면) 보강 요약. report-only.
- **실행 명령:** `./gradlew :domain:settings:pitest`, `./gradlew :domain:location:pitest` (온디맨드/로컬).

- [ ] **Step 2: 포맷 회귀 확인**

Run: `./gradlew spotlessCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: commit**

```bash
git add domain/settings/build.gradle.kts domain/location/build.gradle.kts docs/test-strategy.md
# Task A.2/A.4를 수행했다면 보강한 테스트 파일도 추가:
# git add domain/settings/src/test domain/location/src/test
git commit -m "$(cat <<'EOF'
test: extend mutation testing to domain:settings and domain:location (report-only)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

**Track A 검증:**
```bash
./gradlew :domain:settings:pitest :domain:location:pitest
./gradlew :domain:settings:test :domain:location:test
```

---

## Track B: domain:station 변이 회귀 게이트 승격 (온디맨드)

베이스라인(47%, test strength 97%)이 확보된 `domain:station`을 report-only에서 보수적 회귀 floor 게이트로 올린다. 느리므로 CI 미포함 — 온디맨드 실행 시 회귀를 pass/fail로 판정.

**Files:**
- Modify: `domain/station/build.gradle.kts` (`pitest {}`에 `mutationThreshold` 추가)
- Modify: `docs/test-strategy.md` (report-only → floor 게이트 갱신)

### Task B.1: mutationThreshold floor 추가

- [ ] **Step 1: `domain/station/build.gradle.kts`의 `pitest {}` 주석 줄 교체**

다음 줄을

```kotlin
    // report-only: mutationThreshold 게이트를 두지 않는다.
```

다음으로 교체:

```kotlin
    // 회귀 floor 게이트: overall 47%(test strength 97%) 베이스라인 대비 보수적 40% floor.
    // no-coverage 변이 31건 + 동등 변이 1건 때문에 100% 불가 → overall 기준 floor로 "회귀"만 차단한다.
    mutationThreshold.set(40)
```

- [ ] **Step 2: 게이트가 현재 점수에서 PASS 하는지 확인**

Run: `./gradlew :domain:station:pitest --console=plain`
Expected: BUILD SUCCESSFUL. (overall 47% ≥ floor 40%이므로 통과.)

### Task B.2: 게이트가 회귀를 실제로 잡는지 음성 검증

- [ ] **Step 1: 임시로 floor를 베이스라인 위로 올림**

`domain/station/build.gradle.kts`에서 `mutationThreshold.set(40)`을 임시로 다음으로 변경:

```kotlin
    mutationThreshold.set(60)
```

- [ ] **Step 2: 게이트가 FAIL 하는지 확인**

Run: `./gradlew :domain:station:pitest --console=plain`
Expected: BUILD FAILED. 메시지에 `Mutation score of 47 is below threshold of 60` (또는 동등한 "below threshold" 문구) 포함.

- [ ] **Step 3: floor를 40으로 원복**

`mutationThreshold.set(60)`을 다시 `mutationThreshold.set(40)`으로 되돌린다.

- [ ] **Step 4: 다시 PASS 확인**

Run: `./gradlew :domain:station:pitest --console=plain`
Expected: BUILD SUCCESSFUL.

### Task B.3: 문서 갱신 + commit

- [ ] **Step 1: `docs/test-strategy.md`의 `domain:station` report-only 문구 갱신**

`docs/test-strategy.md:96`의 다음 문장

```markdown
- **report-only 결정:** mutation 점수 임계값으로 빌드를 깨지 않습니다. 변이 테스트는 느리므로 CI에 포함하지 않고 로컬/온디맨드로 둡니다. 게이트화는 점수가 안정화된 뒤 별도로 결정합니다.
```

을 다음으로 교체:

```markdown
- **회귀 floor 게이트 결정:** 베이스라인(overall 47%, test strength 97%)이 안정화돼 `mutationThreshold`를 보수적 40% floor로 설정했습니다. no-coverage 변이 31건과 동등 변이 1건 때문에 100%가 불가하므로, floor는 "100%를 강제"하는 게 아니라 "테스트가 약해지는 회귀"만 차단합니다. 변이 테스트는 느리므로 CI에는 넣지 않고 온디맨드(`./gradlew :domain:station:pitest`)로 실행합니다. `domain:settings`/`domain:location`은 베이스라인 안정화 전까지 report-only를 유지합니다.
```

- [ ] **Step 2: 포맷 회귀 확인**

Run: `./gradlew spotlessCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: commit**

```bash
git add domain/station/build.gradle.kts docs/test-strategy.md
git commit -m "$(cat <<'EOF'
test: promote domain:station mutation testing to a regression floor gate

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

**Track B 검증:**
```bash
./gradlew :domain:station:pitest
```

---

## 문서 갱신 (마무리 commit)

**Files:**
- Modify: `docs/verification-matrix.md`
- Modify: `CHANGELOG.md`

### Task D.1: verification-matrix.md 갱신

- [ ] **Step 1: 명령 추가**

`docs/verification-matrix.md`에 다음을 추가(기존 mutation/dependency 명령 근처):
- `./gradlew verifyModuleBoundaries` — 의도된 모듈 경계 검증(빠름, CI `static-analysis`에 포함). 의도된 예외는 `core:location → domain:location` 하나.
- `./gradlew :domain:settings:pitest`, `./gradlew :domain:location:pitest` — domain 계층 변이 테스트(온디맨드, report-only).
- `./gradlew :domain:station:pitest` — **회귀 floor 게이트(40%)**, 온디맨드.

### Task D.2: CHANGELOG.md Unreleased 갱신

- [ ] **Step 1: 항목 추가**

`CHANGELOG.md`의 `Unreleased` 섹션에 추가:
- 의도된 모듈 경계를 강제하는 `verifyModuleBoundaries` 가드 추가(CI static-analysis 포함).
- 변이 테스트를 `domain:settings`/`domain:location`로 확장(report-only).
- `domain:station` 변이 테스트를 회귀 floor 게이트(40%)로 승격.

### Task D.3: 문서 commit

- [ ] **Step 1: commit**

```bash
git add docs/verification-matrix.md CHANGELOG.md
git commit -m "$(cat <<'EOF'
docs: record module boundary guard and mutation gate

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## 트랙 간 독립성

Track C(경계 가드) / Track A(변이 확장) / Track B(변이 게이트)는 서로 독립적으로 commit한다. 한 묶음으로 batch하지 않는다. 직전 verification-depth-hardening 플랜과 동일한 "엄브렐러 스펙, 독립 commit" 패턴.

## 최종 검증 (전체)

```bash
# Track C
./gradlew verifyModuleBoundaries
# Track A
./gradlew :domain:settings:pitest :domain:location:pitest :domain:settings:test :domain:location:test
# Track B
./gradlew :domain:station:pitest
# 회귀 안전망 (기존 fast 경로 + config-cache 호환)
./gradlew spotlessCheck lint verifyModuleBoundaries --continue
./gradlew \
  :domain:location:test :domain:settings:test :domain:station:test :core:model:test \
  :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

## 가드 규칙이 현재 그래프를 통과하는 근거 (구현 전 GREEN 기대값)

| 소비 모듈 | 실제 main 의존(`implementation`/`api`) | 금지 규칙 위반 |
| --- | --- | --- |
| `feature:station-list` | `domain:location/station/settings`, `core:designsystem`, `core:model` | 없음 |
| `feature:watchlist` | `domain:station`, `core:designsystem`, `core:model` | 없음 |
| `feature:settings` | `domain:settings`, `core:designsystem`, `core:model` | 없음 |
| `data:station` | `domain:station`, `core:model/observability/network/database` | 없음(`core:location` 미의존) |
| `data:settings` | `domain:settings`, `core:datastore` | 없음 |
| `domain:*` | `core:model`만 | 없음 |
| `core:model/observability` | 없음 | 없음 |
| `core:network` | `core:model` | 없음 |
| `core:location` | `domain:location`, `core:model/observability` | **규칙 제외**(의도된 예외) |
| `core:designsystem` | `core:model` | 없음(소비자 목록 외) |
| `app` | 전체 | 없음(조립 루트, 소비자 목록 외) |
| `tools:demo-seed` | `core:model/network`, `domain:station` | 없음(소비자 목록 외) |
