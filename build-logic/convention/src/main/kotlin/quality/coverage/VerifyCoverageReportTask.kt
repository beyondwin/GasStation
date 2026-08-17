package com.gasstation.buildlogic.quality.coverage

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Git worktree and base-ref state are intentionally live external inputs")
abstract class VerifyCoverageReportTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val manifest: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val policy: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val baseline: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val verifier: RegularFileProperty
    @get:Input abstract val sourceCommit: Property<String>
    @get:Input abstract val event: Property<String>
    @get:Input @get:Optional abstract val baseRef: Property<String>
    @get:OutputFile abstract val summary: RegularFileProperty

    @get:Inject abstract val execOperations: ExecOperations

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        val result = execOperations.exec {
            executable("python3")
            val arguments = mutableListOf(
                verifier.get().asFile.absolutePath,
                "verify",
                "--manifest", manifest.get().asFile.absolutePath,
                "--policy", policy.get().asFile.absolutePath,
                "--baseline", baseline.get().asFile.absolutePath,
                "--output", summary.get().asFile.absolutePath,
                "--source-commit", sourceCommit.get(),
                "--event", event.get(),
            )
            baseRef.orNull?.takeIf(String::isNotBlank)?.let { value ->
                arguments += listOf("--base-ref", value)
            }
            args(arguments)
            isIgnoreExitValue = true
        }
        result.assertNormalExitValue()
    }
}
