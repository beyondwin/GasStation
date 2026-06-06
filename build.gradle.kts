import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

buildscript {
    dependencies {
        classpath(libs.kotlin.gradlePlugin) {
            version {
                strictly(libs.versions.kotlin.get())
            }
        }
        classpath(libs.kotlin.compose.gradlePlugin) {
            version {
                strictly(libs.versions.kotlin.get())
            }
        }
        classpath(libs.ksp.gradlePlugin) {
            version {
                strictly(libs.versions.ksp.get())
            }
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.googleDevtoolsKsp) apply false
    alias(libs.plugins.googleDaggerHiltAndroid) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.benManesVersions)
}

dependencies {
    kover(project(":app"))
    kover(project(":core:model"))
    kover(project(":core:observability"))
    kover(project(":core:designsystem"))
    kover(project(":core:location"))
    kover(project(":core:network"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
    kover(project(":domain:location"))
    kover(project(":domain:settings"))
    kover(project(":domain:station"))
    kover(project(":data:settings"))
    kover(project(":data:station"))
    kover(project(":feature:settings"))
    kover(project(":feature:station-list"))
    kover(project(":feature:watchlist"))
    kover(project(":tools:demo-seed"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
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

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !regex.matches(version)
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isNonStable(candidate.version) }
}

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

// config-cache 안전: 모든 subproject(중첩 모듈 포함)를 먼저 평가해 production 선언 의존성(api/implementation)을
// "consumer|target" String 으로만 캡처하고, 실행 시점에는 task @Input 값만 읽는다.
// evaluationDependsOnChildren()는 직계 자식만 평가하므로 :data:station 같은 손자 모듈이 누락된다.
// 따라서 subprojects(전이적 전체)를 각각 명시적으로 먼저 평가한다.
subprojects.forEach { evaluationDependsOn(it.path) }
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
