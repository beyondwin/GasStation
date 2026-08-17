package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.quality.coverage.configureCoverage
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.HasConfigurableValue

class GasStationRootQualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            throw GradleException("gasstation.root.quality must be applied to the root project only")
        }

        configureCoverage(target)

        val inspectedModulePaths = target.subprojects.map(Project::getPath).sorted()
        val capturedModuleEdges = target.objects.setProperty(String::class.java)
        capturedModuleEdges.convention(emptySet())
        target.subprojects(Action<Project> {
            val consumerPath = path
            configurations.configureEach(Action<Configuration> {
                if (name in CAPTURED_CONFIGURATION_NAMES) {
                    dependencies
                        .withType(ProjectDependency::class.java)
                        .configureEach(Action<ProjectDependency> {
                            capturedModuleEdges.add("$consumerPath|$path")
                        })
                }
            })
        })

        target.tasks.register(
            "verifyModuleBoundaries",
            VerifyModuleBoundariesTask::class.java,
            Action<VerifyModuleBoundariesTask> {
                group = "verification"
                description = MODULE_BOUNDARY_DESCRIPTION
                forbiddenEdges.set(FORBIDDEN_MODULE_EDGES.sorted())
                moduleEdges.set(capturedModuleEdges.map { edges -> edges.sorted() })
                modulePaths.set(inspectedModulePaths)
                forbiddenEdges.lock()
                moduleEdges.lock()
                modulePaths.lock()
            },
        )

        target.tasks.register(
            "verifyNoDeprecatedComposeTestApis",
            VerifyNoDeprecatedComposeTestApisTask::class.java,
            Action<VerifyNoDeprecatedComposeTestApisTask> {
                group = "verification"
                description = COMPOSE_TEST_DESCRIPTION
                sources.from(
                    target.fileTree(target.rootDir, Action<ConfigurableFileTree> {
                        include("**/src/test/**/*.kt", "**/src/androidTest/**/*.kt")
                        exclude(".worktrees/**", "**/build/**")
                    }),
                )
                forbiddenImports.set(FORBIDDEN_COMPOSE_IMPORTS.sorted())
                repositoryRoot.set(target.layout.projectDirectory)
                sources.lock()
                forbiddenImports.lock()
                repositoryRoot.lock()
            },
        )

        target.tasks.register(
            "verifyCiRobolectricRuntime",
            VerifyCiRobolectricRuntimeTask::class.java,
            Action<VerifyCiRobolectricRuntimeTask> {
                group = "verification"
                description = CI_RUNTIME_DESCRIPTION
                workflowFile.set(
                    target.layout.projectDirectory.file(".github/workflows/android.yml"),
                )
                robolectricConfigFile.set(
                    target.layout.projectDirectory.file(
                        "config/robolectric/robolectric.properties",
                    ),
                )
                workflowFile.lock()
                robolectricConfigFile.lock()
            },
        )
    }
}

private fun HasConfigurableValue.lock() {
    finalizeValueOnRead()
    disallowChanges()
}

private val CAPTURED_CONFIGURATION_NAMES = setOf("api", "implementation")

private const val MODULE_BOUNDARY_DESCRIPTION =
    "docs/module-contracts.md 의 의도된 모듈 경계를 검증한다 (의도된 core:location→domain:location 예외 제외)."
private const val COMPOSE_TEST_DESCRIPTION =
    "Fails when deprecated Compose v1 test-environment APIs are imported."
private const val CI_RUNTIME_DESCRIPTION =
    "Fails when the CI Java runtime cannot execute the configured Robolectric SDK."

private val FORBIDDEN_MODULE_EDGES =
    listOf(
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

private val FORBIDDEN_COMPOSE_IMPORTS =
    listOf(
        "import androidx.compose.ui.test.junit4.AndroidComposeTestRule",
        "import androidx.compose.ui.test.junit4.createAndroidComposeRule",
        "import androidx.compose.ui.test.junit4.createComposeRule",
        "import androidx.compose.ui.test.junit4.createEmptyComposeRule",
        "import androidx.compose.ui.test.AndroidComposeUiTestEnvironment",
        "import androidx.compose.ui.test.runAndroidComposeUiTest",
        "import androidx.compose.ui.test.runComposeUiTest",
        "import androidx.compose.ui.test.runEmptyComposeUiTest",
    )
