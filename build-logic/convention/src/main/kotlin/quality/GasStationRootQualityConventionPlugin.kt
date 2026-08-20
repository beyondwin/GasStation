package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.contractApiModules
import com.gasstation.buildlogic.quality.coverage.configureCoverage
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.getByType

class GasStationRootQualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            throw GradleException("gasstation.root.quality must be applied to the root project only")
        }

        configureCoverage(target)
        val productionDependencies = registerProductionDependencies(target)
        val selectedContractModules = productionDependencies.activeModules.filter { active ->
            contractApiModules.any { it.projectPath == active }
        }

        val inspectedModulePaths = target.subprojects.map(Project::getPath).sorted()
        target.tasks.register(
            "verifyModuleBoundaries",
            VerifyModuleBoundariesTask::class.java,
            Action<VerifyModuleBoundariesTask> {
                group = "verification"
                description = MODULE_BOUNDARY_DESCRIPTION
                modulePaths.set(inspectedModulePaths)
                activeModulePaths.set(productionDependencies.activeModules)
                productionComponents.set(productionDependencies.components.map { it.sorted() })
                productionDeclarationEvidence.set(productionDependencies.declarations.map { it.sorted() })
                testedTargetEvidence.set(productionDependencies.testedTargetEvidence.map { it.sorted() })
                productionPolicyFile.set(
                    target.layout.projectDirectory.file("config/quality/production-dependency-policy.txt"),
                )
                reportFile.set(
                    target.layout.buildDirectory.file("reports/quality/module-boundaries.json"),
                )
                modulePaths.lock()
                activeModulePaths.lock()
                productionComponents.lock()
                productionDeclarationEvidence.lock()
                testedTargetEvidence.lock()
                productionPolicyFile.lock()
                reportFile.lock()
            },
        )

        target.tasks.register(
            "productionDependencyInventory",
            ProductionDependencyInventoryTask::class.java,
            Action<ProductionDependencyInventoryTask> {
                group = "verification"
                description = "Writes resolved production compile/runtime dependency graph evidence."
                graphShards.from(productionDependencies.graphShards)
                reportFile.set(
                    target.layout.buildDirectory.file(
                        "reports/quality/production-dependency-graph.json",
                    ),
                )
                reportFile.lock()
            },
        )

        val publicApiBoundaries =
            target.tasks.register(
                    "verifyPublicApiBoundaries",
                    VerifyPublicApiBoundariesTask::class.java,
                    Action<VerifyPublicApiBoundariesTask> {
                        group = "verification"
                        description =
                            "Verifies all five checked-in ABI directories and scans their compiled public JVM surface for platform and implementation SDK types."
                        moduleMappings.set(
                            contractApiModules.map { module ->
                                "${module.projectPath}|${module.dumpPath}|${module.packageRoot}"
                            },
                        )
                        selectedActiveModules.set(selectedContractModules)
                        classRootMappings.convention(emptyList())
                        repositoryRoot.set(target.layout.projectDirectory)
                        forbiddenFamilies.set(FORBIDDEN_PUBLIC_API_FAMILIES)
                        scannerSchema.set("kotlin-abi-2.4.10+asm-9.9.1")
                        signaturePolicyFile.set(
                            target.layout.projectDirectory.file(
                                "config/quality/public-api-signatures.txt",
                            ),
                        )
                        contractApiModules.forEach { module ->
                            dumpFiles.from(
                                target.fileTree(target.layout.projectDirectory.dir(module.dumpPath.substringBeforeLast('/'))) {
                                    include("**/*.api")
                                },
                            )
                        }
                        reportFile.set(
                            target.layout.buildDirectory.file("reports/quality/public-api-boundaries.json"),
                        )
                        moduleMappings.lock()
                        selectedActiveModules.lock()
                        classRootMappings.finalizeValueOnRead()
                        repositoryRoot.lock()
                        forbiddenFamilies.lock()
                        scannerSchema.lock()
                        signaturePolicyFile.lock()
                        dumpFiles.lock()
                        reportFile.lock()
                    },
                )
        contractApiModules.forEach { contractModule ->
            val module = target.findProject(contractModule.projectPath) ?: return@forEach
                module.pluginManager.withPlugin("gasstation.jvm.library") {
                    val updateTaskPath = "${module.path}:updateKotlinAbi"
                    val explicitlyRequested = updateTaskPath in target.gradle.startParameter.taskNames
                    module.tasks.named("updateKotlinAbi").configure {
                        doFirst {
                            if (!explicitlyRequested) {
                                throw GradleException(
                                    "updateKotlinAbi automation is forbidden; request $updateTaskPath explicitly for reviewed baseline generation",
                                )
                            }
                        }
                    }
                    val main =
                        module.extensions
                            .getByType<JavaPluginExtension>()
                            .sourceSets
                            .named(SourceSet.MAIN_SOURCE_SET_NAME)
                    publicApiBoundaries.configure {
                        dependsOn(module.tasks.named("checkKotlinAbi"))
                        classDirectories.from(main.map { it.output.classesDirs })
                        classRootMappings.addAll(
                            main.map { sourceSet ->
                                sourceSet.output.classesDirs.files.map { directory ->
                                    "${module.path}|${target.relativePath(directory)}"
                                }.sorted()
                            },
                        )
                    }
                }
            }

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

private const val MODULE_BOUNDARY_DESCRIPTION =
    "docs/module-contracts.md 의 exact production dependency scope를 검증한다."
private const val COMPOSE_TEST_DESCRIPTION =
    "Fails when deprecated Compose v1 test-environment APIs are imported."
private const val CI_RUNTIME_DESCRIPTION =
    "Fails when the CI Java runtime cannot execute the configured Robolectric SDK."

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

private val FORBIDDEN_PUBLIC_API_FAMILIES =
    listOf(
        "android.",
        "androidx.",
        "com.google.android.gms.",
        "retrofit2.",
        "okhttp3.",
        "com.google.gson.",
    )
