package com.gasstation.buildlogic.quality

import java.nio.charset.StandardCharsets.UTF_8
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification-only task with no outputs")
abstract class VerifyNoDeprecatedComposeTestApisTask : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val forbiddenImports: ListProperty<String>

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile.canonicalFile
        val sourcesWithRelativePaths =
            sources.files.map { source ->
                val canonicalSource = source.canonicalFile
                if (!canonicalSource.toPath().startsWith(root.toPath())) {
                    throw GradleException(
                        "Compose test source must be located under the configured repository root.",
                    )
                }
                val relativePath =
                    root.toPath().relativize(canonicalSource.toPath()).joinToString("/")
                relativePath to canonicalSource
            }.sortedBy { (relativePath, _) -> relativePath }
        val forbidden = forbiddenImports.get()
        val violations =
            sourcesWithRelativePaths.flatMap { (relativePath, source) ->
                source.readLines(UTF_8).mapIndexedNotNull { index, line ->
                    if (forbidden.any(line::startsWith)) {
                        "$relativePath:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }.sorted()

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "Deprecated Compose test APIs found; migrate imports to the official v2 packages:",
                    )
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle(
            "Compose test API guard OK: deprecated v1 test-environment imports not found.",
        )
    }
}
