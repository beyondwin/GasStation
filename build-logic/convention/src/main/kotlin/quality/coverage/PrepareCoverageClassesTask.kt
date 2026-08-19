package com.gasstation.buildlogic.quality.coverage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
abstract class PrepareCoverageClassesTask : DefaultTask() {
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @get:Input
    abstract val excludedClassPatterns: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val inputArtifactIdentityFile: org.gradle.api.file.RegularFileProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun prepare() {
        val identity = inputArtifactIdentityFile.get().asFile
        identity.delete()
        val output = outputDirectory.get().asFile
        val staging = output.resolveSibling("${output.name}.staging")
        deleteExact(staging)
        staging.mkdirs()
        val seen = linkedMapOf<String, String>()

        inputDirectories.get().map(Directory::getAsFile).sortedBy(File::getAbsolutePath).forEach { directory ->
            if (!directory.exists()) return@forEach
            directory.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
                .forEach { source ->
                    requireCoverageClassWithinDirectory(directory, source)
                    copyClass(source.relativeTo(directory).invariantSeparatorsPath, source.readBytes(), staging, seen)
                }
        }
        inputJars.get().map(RegularFile::getAsFile).sortedBy(File::getAbsolutePath).forEach { archive ->
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.sortedBy { it.name }.forEach { entry ->
                    copyClass(entry.name, zip.getInputStream(entry).use { it.readBytes() }, staging, seen)
                }
            }
        }
        if (seen.isEmpty()) {
            deleteExact(staging)
            throw GradleException("Coverage class preparation produced zero class files for $path")
        }
        deleteExact(output)
        try {
            Files.move(staging.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(staging.toPath(), output.toPath())
        } catch (error: Exception) {
            deleteExact(output)
            throw GradleException("Could not publish prepared coverage classes for $path", error)
        }
        val records = inputArtifactRecords()
        identity.parentFile.mkdirs()
        identity.writeText(
            records.joinToString(separator = "\n", postfix = "\n") { record ->
                listOf(
                    record.kind,
                    record.entryCount.toString(),
                    record.sha256,
                    Base64.getEncoder().encodeToString(record.path.toByteArray(Charsets.UTF_8)),
                ).joinToString("\t")
            },
        )
    }

    private fun inputArtifactRecords(): List<InputArtifactRecord> {
        val root = repositoryRoot.get().asFile.canonicalFile
        val artifacts =
            (inputDirectories.get().map { it.asFile } + inputJars.get().map { it.asFile })
                .filter(File::exists)
                .map(File::getCanonicalFile)
                .distinct()
                .sortedBy { repositoryRelative(root, it) }
        if (artifacts.isEmpty()) throw GradleException("$path has no existing provider-owned class input artifact")
        return artifacts.map { artifact ->
            if (artifact.isFile) {
                InputArtifactRecord("file", repositoryRelative(root, artifact), 1, coverageSha256(artifact.readBytes()))
            } else {
                val entries = artifact.walkTopDown().filter(File::isFile).map { file ->
                    linkedMapOf(
                        "path" to file.canonicalFile.relativeTo(artifact).invariantSeparatorsPath,
                        "sha256" to coverageSha256(file.readBytes()),
                    )
                }.toList().sortedBy { it["path"] }
                InputArtifactRecord(
                    "directory",
                    repositoryRelative(root, artifact),
                    entries.size,
                    coverageSha256(canonicalCoverageJson(entries)),
                )
            }
        }
    }

    private fun copyClass(path: String, bytes: ByteArray, staging: File, seen: MutableMap<String, String>) {
        val normalized = normalizeCoverageClassPath(path) ?: return
        if (excludedClassPatterns.get().any { pattern -> coverageGlobMatches(pattern, normalized) }) return
        recordCoverageClassPath(normalized, seen)
        staging.resolve(normalized).apply {
            parentFile.mkdirs()
            writeBytes(bytes)
            setLastModified(0L)
        }
    }

    private fun deleteExact(file: File) {
        if (!file.exists()) return
        fileSystemOperations.delete { delete(file) }
        if (file.exists()) throw GradleException("Could not delete coverage path: $file")
    }
}

internal fun normalizeCoverageClassPath(path: String): String? {
    val normalized = Normalizer.normalize(path.replace('\\', '/'), Normalizer.Form.NFC)
    if (!normalized.endsWith(".class")) return null
    if (normalized.startsWith('/') || normalized.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
        throw GradleException("Malformed coverage class path: $path")
    }
    return normalized
}

internal fun recordCoverageClassPath(path: String, seen: MutableMap<String, String>) {
    val collision = seen.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }
    if (collision != null) {
        throw GradleException("Duplicate or case-colliding coverage class path: ${collision.key} and $path")
    }
    seen[path] = path
}

internal fun requireCoverageClassWithinDirectory(directory: File, source: File) {
    if (!source.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath())) {
        throw GradleException("Coverage class input escapes provider directory: $source")
    }
}

private data class InputArtifactRecord(
    val kind: String,
    val path: String,
    val entryCount: Int,
    val sha256: String,
)

private fun repositoryRelative(root: File, file: File): String {
    val canonical = file.canonicalFile
    if (!canonical.toPath().startsWith(root.toPath())) throw GradleException("Coverage path escapes repository: $file")
    return canonical.relativeTo(root).invariantSeparatorsPath
}

private fun coverageSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun coverageGlobMatches(pattern: String, path: String): Boolean {
    val regex = buildString {
        append('^')
        var index = 0
        while (index < pattern.length) {
            when {
                pattern.startsWith("**", index) -> {
                    append(".*")
                    index += 2
                }
                pattern[index] == '*' -> {
                    append("[^/]*")
                    index++
                }
                else -> {
                    append(Regex.escape(pattern[index].toString()))
                    index++
                }
            }
        }
        append('$')
    }
    return Regex(regex).matches(path)
}
