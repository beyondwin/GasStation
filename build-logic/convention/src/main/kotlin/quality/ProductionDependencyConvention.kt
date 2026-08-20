package com.gasstation.buildlogic.quality

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.api.variant.Variant
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
    val declarations: org.gradle.api.provider.ListProperty<String>,
    val graphShards: org.gradle.api.file.ConfigurableFileCollection,
    val testedTargetEvidence: org.gradle.api.provider.ListProperty<String>,
)

internal fun registerProductionDependencies(root: Project): ProductionDependencyRegistration {
    val activeModules = readActiveModulePaths(root)
    val components = root.objects.setProperty(String::class.java).convention(emptySet())
    val declarations = root.objects.listProperty(String::class.java).convention(emptyList())
    val graphShards = root.objects.fileCollection()
    val testedTargetEvidence = root.objects.listProperty(String::class.java).convention(emptyList())

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
        module.pluginManager.withPlugin("java") {
            val java = module.extensions.getByType<JavaPluginExtension>()
            val main = java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
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
            val testedTargetComponents = root.objects.setProperty(String::class.java).convention(emptySet())
            val selfInstrumenting = root.objects.property(Boolean::class.java)
            val testedTargetClasspaths = mutableListOf<Triple<String, Configuration, Configuration>>()
            android.finalizeDsl { dsl ->
                selfInstrumenting.set(
                    dsl.experimentalProperties["android.experimental.self-instrumenting"] as? Boolean
                        ?: false,
                )
            }
            android.onVariants(android.selector().all()) { variant ->
                testedTargetComponents.add(variant.name)
                testedTargetClasspaths += Triple(
                    variant.name,
                    variant.compileConfiguration,
                    variant.runtimeConfiguration,
                )
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
            testedTargetEvidence.add(
                root.provider {
                    val componentNames = testedTargetComponents.get().sorted()
                    val targets = module.configurations.getByName("testedApks").dependencies
                        .filterIsInstance<ProjectDependency>()
                        .map(ProjectDependency::getPath)
                        .sorted()
                    val compileOnlyByComponent = testedTargetClasspaths.associate { (component, compile, runtime) ->
                        component to compileOnlyProjectDependencies(compile, runtime)
                    }.toSortedMap()
                    val compileOnlyComponents = compileOnlyByComponent.filterValues { dependencies ->
                        targets.any { target -> dependencies.any { it.path == target } }
                    }.keys.toList()
                    val membership = when {
                        compileOnlyComponents.isEmpty() -> "absent"
                        compileOnlyComponents == componentNames -> "present"
                        else -> "mixed:${compileOnlyComponents.joinToString(",")}"
                    }
                    val exactCompileOnlyRelation =
                        if (selfInstrumenting.get()) {
                            compileOnlyByComponent.values.all(List<ProjectDependencyObservation>::isEmpty)
                        } else {
                            targets.size == 1 && compileOnlyByComponent.values.all { dependencies ->
                                dependencies.size == 1 && dependencies.single().path == targets.single()
                            }
                        }
                    val compileOnlyIdentities = compileOnlyByComponent.flatMap { (component, dependencies) ->
                        dependencies.map { dependency -> "$component:${dependency.encoded}" }
                    }
                    if (targets.size == 1 && !membership.startsWith("mixed:") && exactCompileOnlyRelation) {
                        TestedTargetRelation(
                            consumer = module.path,
                            components = componentNames,
                            target = targets.single(),
                            selfInstrumenting = selfInstrumenting.get(),
                            compileOnlyMembership = membership,
                            compileOnlyIdentities = compileOnlyIdentities,
                        ).encoded
                    } else {
                        "tested-target-observation|${module.path}|${componentNames.joinToString(",")}|" +
                            "targets=${targets.ifEmpty { listOf("-") }.joinToString(",")}|" +
                            "self-instrumenting=${selfInstrumenting.get()}|" +
                            "compile-only-components=${compileOnlyComponents.ifEmpty { listOf("-") }.joinToString(",")}|" +
                            "target-configuration=${compileOnlyIdentities.ifEmpty { listOf("-") }.joinToString(",")}|" +
                            "multiplicity=${compileOnlyByComponent.entries.joinToString(",") { (component, dependencies) -> "$component:${dependencies.size}" }}"
                    }
                },
            )
        }
    }

    return ProductionDependencyRegistration(
        activeModules = activeModules,
        components = components,
        declarations = declarations,
        graphShards = graphShards,
        testedTargetEvidence = testedTargetEvidence,
    )
}

private data class ProjectDependencyObservation(
    val path: String,
    val declarationConfiguration: String,
    val targetConfiguration: String,
) {
    val encoded: String get() = "$declarationConfiguration->$path@targetConfiguration=$targetConfiguration"
}

private fun compileOnlyProjectDependencies(
    compileConfiguration: Configuration,
    runtimeConfiguration: Configuration,
): List<ProjectDependencyObservation> {
    val runtimeHierarchy = runtimeConfiguration.hierarchy.toSet()
    return compileConfiguration.hierarchy
        .filter { it !in runtimeHierarchy }
        .sortedBy(Configuration::getName)
        .flatMap { bucket ->
            bucket.dependencies.filterIsInstance<ProjectDependency>().map { dependency ->
                ProjectDependencyObservation(
                    path = dependency.path,
                    declarationConfiguration = bucket.name,
                    targetConfiguration = dependency.targetConfiguration ?: "default",
                )
            }
        }.sortedBy(ProjectDependencyObservation::encoded)
}

private fun <VariantT : Variant> registerAndroidProductionComponents(
    module: Project,
    android: AndroidComponentsExtension<*, *, VariantT>,
    components: org.gradle.api.provider.SetProperty<String>,
    declarations: org.gradle.api.provider.ListProperty<String>,
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
    declarations: org.gradle.api.provider.ListProperty<String>,
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
    declarations: org.gradle.api.provider.ListProperty<String>,
) {
    declarations.addAll(
        module.provider {
            configuration.hierarchy.sortedBy(Configuration::getName).flatMap { bucket ->
                bucket.dependencies.map { dependency ->
                    val identity = dependencyIdentity(dependency)
                    "${module.path}|$componentName|$classpathKind|${identity.kind}|${identity.target}|" +
                        "${bucket.name}|${identity.detail}"
                }
            }
        },
    )
}

private data class DirectDependencyIdentity(val kind: String, val target: String, val detail: String)

private fun dependencyIdentity(dependency: Dependency): DirectDependencyIdentity =
    when (dependency) {
        is ProjectDependency ->
            DirectDependencyIdentity(
                "project",
                dependency.path,
                "targetConfiguration=${dependency.targetConfiguration ?: "default"}",
            )
        else -> {
            val group = dependency.group
            val name = dependency.name
            if (group.isNullOrBlank() || name.isBlank()) {
                DirectDependencyIdentity("unsupported", dependency.javaClass.name, "-")
            } else {
                DirectDependencyIdentity("external", "$group:$name", "-")
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
            val expandedComponents = mutableSetOf<String>()
            val rootIdentity = selectedIdentity(root.id)
            records +=
                "${module.path}|$componentName|$classpathKind|root|" +
                    "root=$rootIdentity|path=$rootIdentity"
            fun visit(
                component: org.gradle.api.artifacts.result.ResolvedComponentResult,
            ) {
                val parentIdentity = selectedIdentity(component.id)
                if (!expandedComponents.add(parentIdentity)) return
                component.dependencies.forEach { dependency ->
                    when (dependency) {
                        is ResolvedDependencyResult -> {
                            val selected = selectedIdentity(dependency.selected.id)
                            val edgePath =
                                listOf(rootIdentity, parentIdentity, selected)
                                    .fold(mutableListOf<String>()) { path, identity ->
                                        path.apply { if (lastOrNull() != identity) add(identity) }
                                    }
                            records +=
                                "${module.path}|$componentName|$classpathKind|" +
                                    "${if (parentIdentity == rootIdentity) "direct" else "transitive"}|" +
                                    "root=$rootIdentity|parent=$parentIdentity|" +
                                    "requested=${stableToken(dependency.requested.displayName)}|selected=$selected|" +
                                    "path=${edgePath.joinToString(">")}"
                            visit(dependency.selected)
                        }
                        is UnresolvedDependencyResult -> {
                            val requested = stableToken(dependency.attempted.displayName)
                            val edgePath =
                                listOf(rootIdentity, parentIdentity, "unresolved:$requested")
                                    .fold(mutableListOf<String>()) { path, identity ->
                                        path.apply { if (lastOrNull() != identity) add(identity) }
                                    }
                            records +=
                                "${module.path}|$componentName|$classpathKind|unresolved|" +
                                    "root=$rootIdentity|parent=$parentIdentity|requested=$requested|" +
                                    "reason=${dependency.failure.javaClass.simpleName}|" +
                                    "path=${edgePath.joinToString(">")}"
                        }
                    }
                }
            }
            visit(root)
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
