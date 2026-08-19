package com.gasstation.buildlogic.quality

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification-only task with no outputs")
abstract class VerifyModuleBoundariesTask : DefaultTask() {
    @get:Input
    abstract val forbiddenEdges: ListProperty<String>

    @get:Input
    abstract val moduleEdges: ListProperty<String>

    @get:Input
    abstract val modulePaths: ListProperty<String>

    @get:Input
    abstract val activeModulePaths: ListProperty<String>

    @get:Input
    abstract val productionComponents: ListProperty<String>

    @get:Input
    abstract val productionDeclarationEvidence: ListProperty<String>

    @get:Input
    abstract val testedTargetEvidence: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionPolicyFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        reportFile.get().asFile.delete()
        try {
            verifyCheckedPolicy()
        } catch (failure: GradleException) {
            writeFailureReportIfMissing(failure.message.orEmpty())
            throw failure
        } catch (failure: ProductionDependencyPolicyException) {
            writeFailureReportIfMissing(failure.message.orEmpty())
            throw GradleException(failure.message.orEmpty(), failure)
        }
    }

    private fun verifyCheckedPolicy() {
        val rules = forbiddenEdges.get().map(::decodeRule)
        val legacyViolations =
            moduleEdges.get()
                .map(::decodeEdge)
                .flatMap { edge ->
                    rules.mapNotNull { rule ->
                        if (
                            edge.consumerPath.startsWith(rule.consumerPrefix) &&
                            edge.targetPath.startsWith(rule.targetPrefix)
                        ) {
                            "${edge.consumerPath} -> ${edge.targetPath}  (${rule.reason})"
                        } else {
                            null
                        }
                    }
                }
                .toSortedSet()

        val policy = ProductionDependencyPolicy.parse(productionPolicyFile.get().asFile.readBytes())
        policy.requireExactActiveModules(activeModulePaths.get())
        val actualScopes = aggregateProductionScopes(productionDeclarationEvidence.get())
        val directViolations = policy.compareDirectDeclarations(actualScopes)
        val testedTargetViolations = compareTestedTarget(policy.testedTarget, testedTargetEvidence.get())
        val diagnostics = (directViolations + testedTargetViolations).sorted()
        val report =
            buildString {
                append("{\"schemaVersion\":1,\"policyPath\":\"config/quality/production-dependency-policy.txt\",")
                append("\"policySha256\":${jsonString(policy.sha256)},")
                append("\"enforcement\":${jsonString(policy.enforcement.value)},")
                append("\"activeModules\":${jsonArray(activeModulePaths.get().sorted())},")
                append("\"gradleProjectNodes\":${jsonArray(modulePaths.get().sorted())},")
                append("\"productionComponents\":${jsonArray(productionComponents.get().sorted())},")
                append("\"directDeclarations\":${jsonArray(actualScopes.map { it.encoded })},")
                append("\"testedTarget\":${jsonArray(testedTargetEvidence.get().sorted())},")
                append("\"diagnostics\":${jsonArray(diagnostics)},")
                append("\"status\":${jsonString(if (diagnostics.isEmpty()) "allowlisted" else "violating")}}\n")
            }
        writeUtf8Lf(reportFile.get().asFile, report)

        if (legacyViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("모듈 경계 위반 ${legacyViolations.size}건 (docs/module-contracts.md 참조):")
                    legacyViolations.forEach { appendLine("  - $it") }
                },
            )
        }
        if (policy.enforcement == ProductionDependencyEnforcement.BLOCKING && diagnostics.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("production dependency policy violations ${diagnostics.size}:")
                    diagnostics.forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle(
            "모듈 경계 OK: 금지된 production 의존성 엣지 없음 " +
                "(${modulePaths.get().size}개 모듈 검사).",
        )
    }

    private fun writeFailureReportIfMissing(diagnostic: String) {
        if (reportFile.get().asFile.isFile) return
        writeUtf8Lf(
            reportFile.get().asFile,
            buildString {
                append("{\"schemaVersion\":1,\"policyPath\":\"config/quality/production-dependency-policy.txt\",")
                append("\"policySha256\":\"unavailable\",\"enforcement\":\"unavailable\",")
                append("\"activeModules\":${jsonArray(activeModulePaths.get().sorted())},")
                append("\"gradleProjectNodes\":${jsonArray(modulePaths.get().sorted())},")
                append("\"productionComponents\":${jsonArray(productionComponents.get().sorted())},")
                append("\"directDeclarations\":[],\"testedTarget\":[],")
                append("\"diagnostics\":${jsonArray(listOf(diagnostic))},\"status\":\"violating\"}\n")
            },
        )
    }

    private fun aggregateProductionScopes(records: List<String>): List<ProductionDependencyScope> {
        data class Key(val consumer: String, val kind: ProductionDependencyKind, val target: String, val configuration: String)
        data class Membership(val compile: MutableSet<String> = sortedSetOf(), val runtime: MutableSet<String> = sortedSetOf())
        val grouped = linkedMapOf<Key, Membership>()
        records.sorted().forEach { encoded ->
            val fields = encoded.split('|')
            if (fields.size != 6) throw GradleException("invalid production dependency evidence: $encoded")
            val kind = ProductionDependencyKind.entries.singleOrNull { it.value == fields[3] }
                ?: throw GradleException("invalid production dependency kind: $encoded")
            val key = Key(fields[0], kind, fields[4], fields[5])
            val membership = grouped.getOrPut(key, ::Membership)
            when (fields[2]) {
                "compile" -> membership.compile += fields[1]
                "runtime" -> membership.runtime += fields[1]
                else -> throw GradleException("invalid production classpath kind: $encoded")
            }
        }
        return grouped.map { (key, membership) ->
            ProductionDependencyScope(
                consumer = key.consumer,
                kind = key.kind,
                target = key.target,
                declarationConfiguration = key.configuration,
                compileComponents = membership.compile.toList(),
                runtimeComponents = membership.runtime.toList(),
            )
        }.sorted()
    }

    private fun compareTestedTarget(expected: TestedTargetRelation?, evidence: List<String>): List<String> {
        val observed = evidence.filter(String::isNotBlank).sorted()
        return when {
            expected == null && observed.isEmpty() -> emptyList()
            expected == null -> listOf("unexpected tested-target relation: $observed")
            observed == listOf(expected.encoded) -> emptyList()
            else -> listOf("tested-target relation mismatch: expected=${expected.encoded} actual=$observed")
        }
    }

    private fun decodeRule(encoded: String): ForbiddenModuleEdge {
        val parts = encoded.split('|', limit = 3)
        if (parts.size != 3 || parts.any(String::isBlank)) {
            throw GradleException("Invalid module boundary rule: $encoded")
        }
        return ForbiddenModuleEdge(
            consumerPrefix = parts[0],
            targetPrefix = parts[1],
            reason = parts[2],
        )
    }

    private fun decodeEdge(encoded: String): ModuleEdge {
        val parts = encoded.split('|', limit = 2)
        if (parts.size != 2 || parts.any(String::isBlank)) {
            throw GradleException("Invalid module dependency edge: $encoded")
        }
        return ModuleEdge(consumerPath = parts[0], targetPath = parts[1])
    }

    private data class ForbiddenModuleEdge(
        val consumerPrefix: String,
        val targetPrefix: String,
        val reason: String,
    )

    private data class ModuleEdge(
        val consumerPath: String,
        val targetPath: String,
    )
}
