package com.gasstation.buildlogic.quality

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
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

    @TaskAction
    fun verify() {
        val rules = forbiddenEdges.get().map(::decodeRule)
        val violations =
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

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("모듈 경계 위반 ${violations.size}건 (docs/module-contracts.md 참조):")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle(
            "모듈 경계 OK: 금지된 production 의존성 엣지 없음 " +
                "(${modulePaths.get().size}개 모듈 검사).",
        )
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
