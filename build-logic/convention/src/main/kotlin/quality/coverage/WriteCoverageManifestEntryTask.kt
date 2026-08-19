package com.gasstation.buildlogic.quality.coverage

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.Normalizer
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.data.ExecutionDataReader
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore

@CacheableTask
abstract class WriteCoverageManifestEntryTask : DefaultTask() {
    @get:Input abstract val sourceCommit: Property<String>
    @get:Input abstract val reportId: Property<String>
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val platform: Property<String>
    @get:Input abstract val variant: Property<String>
    @get:Input abstract val testTaskPath: Property<String>
    @get:Input abstract val sourceRoots: ListProperty<String>
    @get:Input abstract val testSourceRoots: ListProperty<String>
    @get:Internal abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSourceFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedClassDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val executionData: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xmlReport: RegularFileProperty

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeEntry() {
        val commit = sourceCommit.get()
        if (!commit.matches(Regex("[0-9a-f]{40}")) || commit == "0".repeat(40)) {
            throw GradleException("gasstation.coverageSourceCommit must be one non-zero 40-hex object ID")
        }
        val root = repositoryRoot.get().asFile.canonicalFile
        val productionRecords = sourceRecords(root, sourceFiles.files, sourceRoots.get(), includePackage = true)
        val testRecords = sourceRecords(root, testSourceFiles.files, testSourceRoots.get(), includePackage = false)
        val prepared = preparedClassDirectory.get().asFile
        val classRecords = classRecords(prepared)
        val classIds = classRecords.associate { it["jacocoClassId"] as String to true }
        val executionFiles = executionData.files.filter(File::isFile).sortedBy { relative(root, it) }
        if (executionFiles.size != 1) {
            throw GradleException(
                "${reportId.get()} requires exactly one existing JaCoCo execution data file; found ${executionFiles.size}",
            )
        }
        val (projectExecution, ignoredExecutionCount) =
            readCoverageExecutionRecords(executionFiles, classIds.keys)
        val xml = xmlReport.get().asFile
        val xmlSemanticSha256 = coverageXmlSemanticSha256(xml.readBytes(), reportId.get())
        val payload = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "sourceCommit" to commit,
            "reportId" to reportId.get(),
            "module" to modulePath.get(),
            "platform" to platform.get(),
            "variant" to variant.get(),
            "testTask" to testTaskPath.get(),
            "xmlReport" to relative(root, xml),
            "sourceRoots" to sourceRoots.get().sorted(),
            "sources" to productionRecords,
            "testSourceRoots" to testSourceRoots.get().sorted(),
            "testSources" to testRecords,
            "testInputIdentitySha256" to sha256(canonicalCoverageJson(testRecords)),
            "inputClassArtifacts" to listOf(relative(root, prepared)),
            "preparedClassDirectory" to relative(root, prepared),
            "classFileCount" to classRecords.size,
            "classes" to classRecords,
            "executionData" to executionFiles.map { relative(root, it) },
            "executionFileSha256" to sha256(executionFiles.single().readBytes()),
            "executionRecords" to projectExecution,
            "ignoredNonProjectExecutionRecordCount" to ignoredExecutionCount,
            "executionSemanticSha256" to sha256(canonicalCoverageJson(projectExecution)),
            "xmlFileSha256" to sha256(xml.readBytes()),
            "reportSemanticSha256" to xmlSemanticSha256,
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(canonicalCoverageJson(payload) + byteArrayOf('\n'.code.toByte()))
        }
    }

    private fun sourceRecords(
        root: File,
        files: Set<File>,
        declaredRoots: List<String>,
        includePackage: Boolean,
    ): List<Map<String, Any>> {
        val records = files.filter { it.isFile && it.extension in setOf("kt", "java") }.sortedBy { relative(root, it) }.map { file ->
            val path = relative(root, file)
            if (path != Normalizer.normalize(path, Normalizer.Form.NFC)) {
                throw GradleException("Coverage source path is not Unicode NFC: $path")
            }
            if (declaredRoots.none { path.startsWith("${it.trimEnd('/')}/") }) {
                throw GradleException("Coverage source is outside its declared static roots: $path")
            }
            if (
                path.contains("/build/") || path.contains("/generated/") ||
                (includePackage && Regex("/src/(?:test|androidTest|testFixtures)(?:/|$)").containsMatchIn(path))
            ) {
                throw GradleException("Generated or test source cannot enter authored production coverage: $path")
            }
            linkedMapOf<String, Any>().apply {
                put("path", path)
                if (includePackage) put("package", lexicalPackageDeclaration(file.readBytes(), file.extension))
                put("filename", file.name)
                put("sha256", sha256(file.readBytes()))
            }
        }
        val collision = records.groupBy { (it["path"] as String).lowercase() }.values.firstOrNull { it.size > 1 }
        if (collision != null) throw GradleException("Coverage source paths collide after case folding")
        return records
    }

    private fun classRecords(directory: File): List<Map<String, Any>> {
        val coverage = CoverageBuilder()
        Analyzer(ExecutionDataStore(), coverage).analyzeAll(directory)
        val duplicateIds = coverage.classes.groupBy { it.id }.filterValues { it.size > 1 }
        if (duplicateIds.isNotEmpty()) throw GradleException("Duplicate JaCoCo class ID in prepared classes")
        val ids = coverage.classes.associate { "${it.name}.class" to it.id.toHexId() }
        return directory.walkTopDown().filter { it.isFile && it.extension == "class" }
            .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
            .map { file ->
                val path = file.relativeTo(directory).invariantSeparatorsPath
                linkedMapOf(
                    "path" to path,
                    "sha256" to sha256(file.readBytes()),
                    "jacocoClassId" to (ids[path] ?: throw GradleException("JaCoCo did not analyze prepared class $path")),
                )
            }.toList()
    }

}

internal fun readCoverageExecutionRecords(
    executionFiles: List<File>,
    projectClassIds: Set<String>,
): Pair<List<Map<String, Any>>, Int> {
    val store = ExecutionDataStore()
    val sessions = SessionInfoStore()
    try {
        executionFiles.forEach { file ->
            FileInputStream(file).use { input ->
                val reader = ExecutionDataReader(input)
                reader.setExecutionDataVisitor(store)
                reader.setSessionInfoVisitor(sessions)
                while (reader.read()) Unit
            }
        }
    } catch (error: IllegalStateException) {
        throw GradleException("Incompatible duplicate JaCoCo execution record", error)
    }
    val records =
        store.contents.sortedWith { left, right ->
            val idComparison = java.lang.Long.compareUnsigned(left.id, right.id)
            if (idComparison != 0) idComparison else left.name.compareTo(right.name)
        }.map { record ->
            linkedMapOf<String, Any>(
                "classId" to record.id.toHexId(),
                "name" to record.name,
                "probes" to record.probes.joinToString("") { if (it) "1" else "0" },
            )
        }
    val project = records.filter { it["classId"] in projectClassIds }
    return project to (records.size - project.size)
}

private fun relative(root: File, file: File): String {
    val canonical = file.canonicalFile
    if (!canonical.toPath().startsWith(root.toPath())) throw GradleException("Coverage path escapes repository: $file")
    return canonical.relativeTo(root).invariantSeparatorsPath
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun Long.toHexId(): String = java.lang.Long.toUnsignedString(this, 16).padStart(16, '0')
