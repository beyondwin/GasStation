import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

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
    jacoco
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.googleDevtoolsKsp) apply false
    alias(libs.plugins.googleDaggerHiltAndroid) apply false
    alias(libs.plugins.spotless) apply false
}

jacoco {
    toolVersion = "0.8.15"
}

subprojects {
    if (path != ":benchmark") {
        pluginManager.apply("jacoco")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.15"
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

abstract class VerifyNoDeprecatedComposeTestApisTask : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbiddenImports = listOf(
            "import androidx.compose.ui.test.junit4.AndroidComposeTestRule",
            "import androidx.compose.ui.test.junit4.createAndroidComposeRule",
            "import androidx.compose.ui.test.junit4.createComposeRule",
            "import androidx.compose.ui.test.junit4.createEmptyComposeRule",
            "import androidx.compose.ui.test.AndroidComposeUiTestEnvironment",
            "import androidx.compose.ui.test.runAndroidComposeUiTest",
            "import androidx.compose.ui.test.runComposeUiTest",
            "import androidx.compose.ui.test.runEmptyComposeUiTest",
        )
        val violations = sources.files
            .sortedBy { it.invariantSeparatorsPath }
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImports.any(line::startsWith)) {
                        "${source.invariantSeparatorsPath}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Deprecated Compose test APIs found; migrate imports to the official v2 packages:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle("Compose test API guard OK: deprecated v1 test-environment imports not found.")
    }
}

abstract class VerifyCiRobolectricRuntimeTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workflowFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val robolectricConfigFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val workflow = workflowFile.get().asFile.readText()
        val ciJavaVersion = Regex(
            pattern = "(?m)^\\s*CI_JAVA_VERSION:\\s*[\\\"]?(\\d+)[\\\"]?\\s*$",
        ).find(workflow)?.groupValues?.get(1)?.toInt()
            ?: throw GradleException(
                "Android CI must declare a top-level CI_JAVA_VERSION for the shared Gradle runtime.",
            )
        val javaVersionDeclarations = Regex(
            pattern = "(?m)^\\s*java-version:\\s*(.+?)\\s*$",
        ).findAll(workflow).map { it.groupValues[1] }.toList()
        val expectedJavaVersionReference = "\${{ env.CI_JAVA_VERSION }}"
        if (
            javaVersionDeclarations.isEmpty() ||
            javaVersionDeclarations.any { it != expectedJavaVersionReference }
        ) {
            throw GradleException(
                "Every Android CI setup-java step must use $expectedJavaVersionReference; found " +
                    javaVersionDeclarations.joinToString(),
            )
        }
        val robolectricSdk = java.util.Properties().run {
            robolectricConfigFile.get().asFile.inputStream().use(::load)
            getProperty("sdk")?.toIntOrNull()
        } ?: throw GradleException("config/robolectric/robolectric.properties must declare a numeric sdk.")
        val minimumJavaVersion = if (robolectricSdk >= 36) 21 else 17

        if (ciJavaVersion < minimumJavaVersion) {
            throw GradleException(
                "Robolectric SDK $robolectricSdk requires Java $minimumJavaVersion or newer, " +
                    "but Android CI declares Java $ciJavaVersion.",
            )
        }
        logger.lifecycle(
            "CI/Robolectric runtime OK: Java $ciJavaVersion supports test SDK $robolectricSdk.",
        )
    }
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

tasks.register<VerifyNoDeprecatedComposeTestApisTask>("verifyNoDeprecatedComposeTestApis") {
    group = "verification"
    description = "Fails when deprecated Compose v1 test-environment APIs are imported."
    sources.from(
        fileTree(rootDir) {
            include("**/src/test/**/*.kt", "**/src/androidTest/**/*.kt")
            exclude(".worktrees/**", "**/build/**")
        },
    )
}

tasks.register<VerifyCiRobolectricRuntimeTask>("verifyCiRobolectricRuntime") {
    group = "verification"
    description = "Fails when the CI Java runtime cannot execute the configured Robolectric SDK."
    workflowFile.set(layout.projectDirectory.file(".github/workflows/android.yml"))
    robolectricConfigFile.set(
        layout.projectDirectory.file("config/robolectric/robolectric.properties"),
    )
}

val coverageUnitTestTasks = listOf(
    ":domain:location:test",
    ":core:model:test",
    ":domain:station:test",
    ":domain:settings:test",
    ":core:database:testDebugUnitTest",
    ":core:datastore:testDebugUnitTest",
    ":core:designsystem:testDebugUnitTest",
    ":core:location:testDebugUnitTest",
    ":core:network:test",
    ":core:observability:test",
    ":data:settings:testDebugUnitTest",
    ":data:station:testDebugUnitTest",
    ":feature:settings:testDebugUnitTest",
    ":feature:station-list:testDebugUnitTest",
    ":feature:watchlist:testDebugUnitTest",
    ":app:testDemoDebugUnitTest",
    ":app:testProdDebugUnitTest",
    ":tools:demo-seed:test",
)
val coverageProjects = subprojects.filter { it.path != ":benchmark" }
val coverageExcludes = listOf(
    "**/*Hilt_*.*",
    "**/*_HiltModules*.*",
    "**/*_Factory*.*",
    "**/*_Provide*.*",
    "**/*ComposableSingletons*.*",
    "**/*Preview*Kt*.*",
)
val jvmCoverageProjects = setOf(
    ":core:model",
    ":core:network",
    ":core:observability",
    ":domain:location",
    ":domain:settings",
    ":domain:station",
    ":tools:demo-seed",
)

tasks.register<JacocoReport>("coverageXmlReport") {
    group = "verification"
    description = "Runs the complete unit-test matrix and writes the aggregated JaCoCo XML report."
    dependsOn(
        coverageUnitTestTasks,
    )
    executionData.from(
        coverageProjects.map { project ->
            project.fileTree(project.layout.buildDirectory) {
                include(
                    "jacoco/*.exec",
                    "outputs/unit_test_code_coverage/**/*.exec",
                )
            }
        },
    )
    sourceDirectories.from(
        coverageProjects.flatMap { project ->
            listOf(
                project.layout.projectDirectory.dir("src/main/kotlin"),
                project.layout.projectDirectory.dir("src/main/java"),
                project.layout.projectDirectory.dir("src/demo/kotlin"),
                project.layout.projectDirectory.dir("src/prod/kotlin"),
            )
        },
    )
    classDirectories.from(
        coverageProjects.flatMap { project ->
            if (project.path in jvmCoverageProjects) {
                listOf(
                    project.fileTree(project.layout.buildDirectory.dir("classes/kotlin/main")) {
                        exclude(coverageExcludes)
                    },
                    project.fileTree(project.layout.buildDirectory.dir("classes/java/main")) {
                        exclude(coverageExcludes)
                    },
                )
            } else {
                val variant = if (project.path == ":app") "demoDebug" else "debug"
                buildList {
                    add(
                        project.fileTree(
                            project.layout.buildDirectory.dir(
                                "intermediates/built_in_kotlinc/$variant/compile${variant.replaceFirstChar(Char::uppercase)}Kotlin/classes",
                            ),
                        ) {
                            exclude(coverageExcludes)
                        },
                    )
                    if (project.path == ":app") {
                        add(
                            project.fileTree(
                                project.layout.buildDirectory.dir(
                                    "intermediates/built_in_kotlinc/prodDebug/compileProdDebugKotlin/classes",
                                ),
                            ) {
                                include(
                                    "com/gasstation/analytics/LogcatCrashReporter*.class",
                                    "com/gasstation/di/ProdCrashReporterModule*.class",
                                    "com/gasstation/di/ProdStartupModule*.class",
                                    "com/gasstation/startup/ProdSecretsStartupHook*.class",
                                )
                                exclude(coverageExcludes)
                            },
                        )
                    }
                }
            }
        },
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/report.xml"))
        html.required.set(false)
        csv.required.set(false)
    }
}

tasks.register<VerifyModuleBoundariesTask>("verifyModuleBoundaries") {
    group = "verification"
    description = "docs/module-contracts.md 의 의도된 모듈 경계를 검증한다 (의도된 core:location→domain:location 예외 제외)."
    forbiddenEdges = forbiddenModuleEdges
    moduleEdges = capturedModuleEdges
    moduleCount = capturedModuleCount
}
