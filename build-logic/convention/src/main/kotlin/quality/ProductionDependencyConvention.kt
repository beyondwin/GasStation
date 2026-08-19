package com.gasstation.buildlogic.quality

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.getByType

internal data class ProductionDependencyRegistration(
    val activeModules: List<String>,
    val components: org.gradle.api.provider.SetProperty<String>,
    val declarations: org.gradle.api.provider.SetProperty<String>,
    val graphShards: org.gradle.api.file.ConfigurableFileCollection,
    val testedTargetEvidence: org.gradle.api.provider.SetProperty<String>,
)

internal fun registerProductionDependencies(root: Project): ProductionDependencyRegistration {
    val activeModules = readActiveModulePaths(root)
    val components = root.objects.setProperty(String::class.java).convention(emptySet())
    val declarations = root.objects.setProperty(String::class.java).convention(emptySet())
    val graphShards = root.objects.fileCollection()
    val testedTargetEvidence = root.objects.setProperty(String::class.java).convention(emptySet())
    val testedTargetComponents = root.objects.setProperty(String::class.java).convention(emptySet())
    val testedTargetPath = root.objects.property(String::class.java)
    val selfInstrumenting = root.objects.property(Boolean::class.java)

    root.subprojects.filter { it.path in activeModules }.forEach { module ->
        val moduleGraphRecords = module.objects.setProperty(String::class.java).convention(emptySet())
        val shard = module.tasks.register(
            "productionDependencyInventoryShard",
            ProductionDependencyGraphShardTask::class.java,
        ) {
            graphRecords.set(moduleGraphRecords.map { it.sorted() })
            outputFile.set(
                module.layout.buildDirectory.file("reports/quality/production-dependency-graph-shard.txt"),
            )
            graphRecords.finalizeValueOnRead()
            graphRecords.disallowChanges()
            outputFile.finalizeValueOnRead()
            outputFile.disallowChanges()
        }
        graphShards.from(shard.flatMap { it.outputFile })
        module.pluginManager.withPlugin("gasstation.jvm.library") {
            val java = module.extensions.getByType<JavaPluginExtension>()
            val main = java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            recordJvmKotlinStdlib(module, declarations)
            registerProductionComponent(
                module,
                "main",
                module.configurations.getByName(main.compileClasspathConfigurationName),
                module.configurations.getByName(main.runtimeClasspathConfigurationName),
                components,
                declarations,
                moduleGraphRecords,
            )
        }
        module.pluginManager.withPlugin("com.android.application") {
            registerAndroidProductionComponents(
                module,
                module.extensions.getByType<ApplicationAndroidComponentsExtension>(),
                components,
                declarations,
                moduleGraphRecords,
            )
        }
        module.pluginManager.withPlugin("com.android.library") {
            registerAndroidProductionComponents(
                module,
                module.extensions.getByType<LibraryAndroidComponentsExtension>(),
                components,
                declarations,
                moduleGraphRecords,
            )
        }
        module.pluginManager.withPlugin("com.android.test") {
            val android = module.extensions.getByType<TestAndroidComponentsExtension>()
            android.finalizeDsl { dsl ->
                testedTargetPath.set(dsl.targetProjectPath)
                selfInstrumenting.set(
                    dsl.experimentalProperties["android.experimental.self-instrumenting"] as? Boolean
                        ?: false,
                )
            }
            android.onVariants(android.selector().all()) { variant ->
                testedTargetComponents.add(variant.name)
                registerProductionComponent(
                    module,
                    variant.name,
                    variant.compileConfiguration,
                    variant.runtimeConfiguration,
                    components,
                    declarations,
                    moduleGraphRecords,
                )
            }
        }
    }
    testedTargetEvidence.add(
        root.provider {
            val componentNames = testedTargetComponents.get().sorted()
            if (componentNames.isEmpty()) return@provider ""
            TestedTargetRelation(
                consumer = ":benchmark",
                components = componentNames,
                target = testedTargetPath.get(),
                selfInstrumenting = selfInstrumenting.get(),
                compileOnlyMembership = if (selfInstrumenting.get()) "absent" else "present",
            ).encoded
        },
    )

    return ProductionDependencyRegistration(
        activeModules = activeModules,
        components = components,
        declarations = declarations,
        graphShards = graphShards,
        testedTargetEvidence = testedTargetEvidence,
    )
}

private fun recordJvmKotlinStdlib(
    module: Project,
    declarations: org.gradle.api.provider.SetProperty<String>,
) {
    listOf("compile", "runtime").forEach { classpathKind ->
        declarations.add(
            "${module.path}|main|$classpathKind|external|" +
                "org.jetbrains.kotlin:kotlin-stdlib|api",
        )
    }
}

private fun <VariantT : Variant> registerAndroidProductionComponents(
    module: Project,
    android: AndroidComponentsExtension<*, *, VariantT>,
    components: org.gradle.api.provider.SetProperty<String>,
    declarations: org.gradle.api.provider.SetProperty<String>,
    graphRecords: org.gradle.api.provider.SetProperty<String>,
) {
    android.onVariants(android.selector().all()) { variant ->
        registerProductionComponent(
            module,
            variant.name,
            variant.compileConfiguration,
            variant.runtimeConfiguration,
            components,
            declarations,
            graphRecords,
        )
    }
}

private fun registerProductionComponent(
    module: Project,
    componentName: String,
    compileConfiguration: Configuration,
    runtimeConfiguration: Configuration,
    components: org.gradle.api.provider.SetProperty<String>,
    declarations: org.gradle.api.provider.SetProperty<String>,
    graphRecords: org.gradle.api.provider.SetProperty<String>,
) {
    components.add("${module.path}|$componentName")
    captureDeclarations(module, componentName, "compile", compileConfiguration, declarations)
    captureDeclarations(module, componentName, "runtime", runtimeConfiguration, declarations)
    captureResolvedGraph(module, componentName, "compile", compileConfiguration, graphRecords)
    captureResolvedGraph(module, componentName, "runtime", runtimeConfiguration, graphRecords)
}

private fun captureDeclarations(
    module: Project,
    componentName: String,
    classpathKind: String,
    configuration: Configuration,
    declarations: org.gradle.api.provider.SetProperty<String>,
) {
    configuration.hierarchy.sortedBy(Configuration::getName).forEach { bucket ->
        bucket.dependencies.configureEach(
            object : Action<Dependency> {
                override fun execute(dependency: Dependency) {
                    val (kind, target) = dependencyIdentity(dependency)
                    declarations.add(
                        "${module.path}|$componentName|$classpathKind|$kind|$target|${bucket.name}",
                    )
                }
            },
        )
    }
}

private fun dependencyIdentity(dependency: Dependency): Pair<String, String> =
    when (dependency) {
        is ProjectDependency -> "project" to dependency.path
        else -> {
            val group = dependency.group
            val name = dependency.name
            if (group.isNullOrBlank() || name.isBlank()) {
                "unsupported" to dependency.javaClass.name
            } else {
                "external" to "$group:$name"
            }
        }
    }

private fun captureResolvedGraph(
    module: Project,
    componentName: String,
    classpathKind: String,
    configuration: Configuration,
    graphRecords: org.gradle.api.provider.SetProperty<String>,
) {
    graphRecords.addAll(
        configuration.incoming.resolutionResult.rootComponent.map { root ->
            val records = sortedSetOf<String>()
            val visited = mutableSetOf<String>()
            fun visit(component: org.gradle.api.artifacts.result.ResolvedComponentResult, depth: Int) {
                val selectedIdentity = selectedIdentity(component.id)
                if (!visited.add(selectedIdentity)) return
                component.dependencies.forEach { dependency ->
                    when (dependency) {
                        is ResolvedDependencyResult -> {
                            val selected = selectedIdentity(dependency.selected.id)
                            records +=
                                "${module.path}|$componentName|$classpathKind|" +
                                    "${if (depth == 0) "direct" else "transitive"}|" +
                                    "requested=${stableToken(dependency.requested.displayName)}|selected=$selected"
                            visit(dependency.selected, depth + 1)
                        }
                        is UnresolvedDependencyResult -> {
                            records +=
                                "${module.path}|$componentName|$classpathKind|unresolved|" +
                                    "requested=${stableToken(dependency.attempted.displayName)}|" +
                                    "reason=${dependency.failure.javaClass.simpleName}"
                        }
                    }
                }
            }
            visit(root, 0)
            records.toList()
        },
    )
}

private fun selectedIdentity(identifier: org.gradle.api.artifacts.component.ComponentIdentifier): String =
    when (identifier) {
        is ProjectComponentIdentifier -> "project:${identifier.projectPath}"
        is ModuleComponentIdentifier -> "external:${identifier.group}:${identifier.module}:${identifier.version}"
        else -> "opaque:${stableToken(identifier.displayName)}"
    }

private fun stableToken(value: String): String =
    value.replace('\\', '/').replace(Regex("(?:[A-Za-z]:)?/[^| ]+"), "<path>")

internal fun readActiveModulePaths(root: Project): List<String> {
    val raw = root.gradle.extensions.extraProperties.properties["gasstation.activeModulePaths"]
        ?: throw GradleException("settings.gradle.kts must publish gasstation.activeModulePaths")
    if (raw !is List<*> || raw.any { it !is String }) {
        throw GradleException("gasstation.activeModulePaths must be an immutable List<String>")
    }
    val modules = raw.filterIsInstance<String>()
    if (modules.size != modules.toSet().size || modules.any { !it.matches(Regex(":[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*")) }) {
        throw GradleException("gasstation.activeModulePaths contains duplicate or non-canonical paths")
    }
    val missing = modules.toSet() - root.subprojects.map(Project::getPath).toSet()
    if (missing.isNotEmpty()) throw GradleException("explicit active modules are absent from Gradle: ${missing.sorted()}")
    return modules.sorted()
}
