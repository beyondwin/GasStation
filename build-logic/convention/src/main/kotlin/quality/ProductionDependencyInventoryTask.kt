package com.gasstation.buildlogic.quality

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Resolved dependency evidence is explicitly refreshed")
abstract class ProductionDependencyInventoryTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val graphShards: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun inventory() {
        val records =
            graphShards.files.sortedBy { it.invariantSeparatorsPath }.flatMap { shard ->
                val bytes = shard.readBytes()
                if (bytes.isEmpty() || bytes.last() != '\n'.code.toByte() || bytes.any { it == '\r'.code.toByte() }) {
                    throw GradleException("invalid production dependency graph shard: ${shard.name}")
                }
                shard.readLines(Charsets.UTF_8).filter(String::isNotBlank)
            }.sorted()
        val unresolved = records.filter { it.contains("|unresolved|") }
        val report =
            buildString {
                append("{\"schemaVersion\":1,\"records\":")
                append(jsonArray(records))
                append(",\"unresolved\":")
                append(jsonArray(unresolved))
                append("}\n")
            }
        writeUtf8Lf(reportFile.get().asFile, report)
        if (unresolved.isNotEmpty()) {
            throw GradleException("unresolved production dependency graph entries: ${unresolved.size}")
        }
        logger.lifecycle("Production dependency inventory OK: ${records.size} resolved graph records.")
    }
}

@DisableCachingByDefault(because = "Resolved graph shards are explicit diagnostic evidence")
abstract class ProductionDependencyGraphShardTask : DefaultTask() {
    @get:Input
    abstract val graphRecords: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeShard() {
        writeUtf8Lf(
            outputFile.get().asFile,
            graphRecords.get().sorted().joinToString(separator = "\n", postfix = "\n"),
        )
    }
}
