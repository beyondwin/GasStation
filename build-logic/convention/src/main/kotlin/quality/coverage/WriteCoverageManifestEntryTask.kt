package com.gasstation.buildlogic.quality.coverage

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
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
        val productionRecords = sourceRecords(root, sourceFiles.files)
        val testRecords = sourceRecords(root, testSourceFiles.files, includePackage = false)
        val prepared = preparedClassDirectory.get().asFile
        val classRecords = classRecords(prepared)
        val classIds = classRecords.associate { it["jacocoClassId"] as String to true }
        val executionFiles = executionData.files.filter(File::isFile).sortedBy { relative(root, it) }
        if (executionFiles.isEmpty()) throw GradleException("Missing JaCoCo execution data for ${reportId.get()}")
        val store = ExecutionDataStore()
        val sessions = SessionInfoStore()
        executionFiles.forEach { file ->
            FileInputStream(file).use { input ->
                val reader = ExecutionDataReader(input)
                reader.setExecutionDataVisitor(store)
                reader.setSessionInfoVisitor(sessions)
                while (reader.read()) Unit
            }
        }
        val executionRecords = store.contents.sortedBy { it.id }.map { record ->
            linkedMapOf<String, Any>(
                "classId" to record.id.toHexId(),
                "name" to record.name,
                "probes" to record.probes.joinToString("") { if (it) "1" else "0" },
            )
        }
        val projectExecution = executionRecords.filter { classIds.containsKey(it["classId"]) }
        val xml = xmlReport.get().asFile
        val xmlSemantic = xmlSemanticRecords(xml)
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
            "executionFileSha256" to sha256(canonicalCoverageJson(executionFiles.map {
                linkedMapOf("path" to relative(root, it), "sha256" to sha256(it.readBytes()))
            })),
            "executionRecords" to projectExecution,
            "ignoredNonProjectExecutionRecordCount" to executionRecords.size - projectExecution.size,
            "executionSemanticSha256" to sha256(canonicalCoverageJson(projectExecution)),
            "xmlFileSha256" to sha256(xml.readBytes()),
            "reportSemanticSha256" to sha256(canonicalCoverageJson(xmlSemantic)),
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(canonicalCoverageJson(payload) + byteArrayOf('\n'.code.toByte()))
        }
    }

    private fun sourceRecords(root: File, files: Set<File>, includePackage: Boolean = true): List<Map<String, Any>> =
        files.filter { it.isFile && it.extension in setOf("kt", "java") }.sortedBy { relative(root, it) }.map { file ->
            linkedMapOf<String, Any>().apply {
                put("path", relative(root, file))
                if (includePackage) put("package", lexicalPackage(file.readText()))
                put("filename", file.name)
                put("sha256", sha256(file.readBytes()))
            }
        }

    private fun classRecords(directory: File): List<Map<String, Any>> {
        val coverage = CoverageBuilder()
        Analyzer(ExecutionDataStore(), coverage).analyzeAll(directory)
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

    private fun xmlSemanticRecords(file: File): List<Map<String, Any>> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(file)
        val records = mutableListOf<Map<String, Any>>()
        val packages = document.getElementsByTagName("package")
        for (packageIndex in 0 until packages.length) {
            val packageNode = packages.item(packageIndex)
            val packageName = packageNode.attributes.getNamedItem("name").nodeValue
            val children = packageNode.childNodes
            for (sourceIndex in 0 until children.length) {
                val source = children.item(sourceIndex)
                if (source.nodeName != "sourcefile") continue
                val sourceName = source.attributes.getNamedItem("name").nodeValue
                val lines = source.childNodes
                for (lineIndex in 0 until lines.length) {
                    val line = lines.item(lineIndex)
                    if (line.nodeName != "line") continue
                    records += linkedMapOf(
                        "package" to packageName,
                        "source" to sourceName,
                        "line" to line.attributes.getNamedItem("nr").nodeValue.toInt(),
                        "mi" to line.attributes.getNamedItem("mi").nodeValue.toInt(),
                        "ci" to line.attributes.getNamedItem("ci").nodeValue.toInt(),
                        "mb" to line.attributes.getNamedItem("mb").nodeValue.toInt(),
                        "cb" to line.attributes.getNamedItem("cb").nodeValue.toInt(),
                    )
                }
            }
        }
        return records.sortedWith(compareBy({ it["package"].toString() }, { it["source"].toString() }, { it["line"] as Int }))
    }

    private fun lexicalPackage(text: String): String {
        val stripped = text.replace(Regex("(?s)/\\*.*?\\*/|//[^\\r\\n]*|\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\""), " ")
        val match = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)").findAll(stripped).toList()
        if (match.size != 1) throw GradleException("Expected exactly one package declaration in authored source")
        return match.single().groupValues[1]
    }
}

private fun relative(root: File, file: File): String {
    val canonical = file.canonicalFile
    if (!canonical.toPath().startsWith(root.toPath())) throw GradleException("Coverage path escapes repository: $file")
    return canonical.relativeTo(root).invariantSeparatorsPath
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun Long.toHexId(): String = java.lang.Long.toUnsignedString(this, 16).padStart(16, '0')
