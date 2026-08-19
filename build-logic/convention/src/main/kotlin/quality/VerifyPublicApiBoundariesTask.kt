package com.gasstation.buildlogic.quality

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

@DisableCachingByDefault(because = "Public ABI verification writes explicit diagnostic evidence")
abstract class VerifyPublicApiBoundariesTask : DefaultTask() {
    @get:Input
    abstract val moduleMappings: ListProperty<String>

    @get:Input
    abstract val forbiddenFamilies: ListProperty<String>

    @get:Input
    abstract val scannerSchema: org.gradle.api.provider.Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dumpFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        reportFile.get().asFile.delete()
        try {
            verifyCheckedSurface()
        } catch (failure: GradleException) {
            if (!reportFile.get().asFile.isFile) {
                writeUtf8Lf(
                    reportFile.get().asFile,
                    buildString {
                        append("{\"schemaVersion\":1,\"scannerSchema\":${jsonString(scannerSchema.get())},")
                        append("\"modules\":${jsonArray(moduleMappings.get().sorted())},")
                        append("\"dumpIdentities\":[],\"classIdentities\":[],")
                        append("\"selectedClassCount\":0,\"selectedMemberCount\":0,")
                        append("\"descriptorLocationCount\":0,\"signatureLocationCount\":0,")
                        append("\"violations\":${jsonArray(listOf(failure.message.orEmpty()))}}\n")
                    },
                )
            }
            throw failure
        }
    }

    private fun verifyCheckedSurface() {
        val mappings = moduleMappings.get().map(::decodeMapping).sortedBy(Mapping::module)
        val expectedPaths = mappings.map(Mapping::dumpPath).toSortedSet()
        val dumpsByPath = linkedMapOf<String, File>()
        dumpFiles.files.filter(File::isFile).forEach { file ->
            val matches = expectedPaths.filter { expected -> file.invariantSeparatorsPath.endsWith("/$expected") }
            if (matches.size != 1) throw GradleException("unexpected or ambiguous ABI dump: ${file.name}")
            if (dumpsByPath.put(matches.single(), file) != null) throw GradleException("duplicate ABI dump: ${matches.single()}")
        }
        if (dumpsByPath.keys != expectedPaths) {
            throw GradleException("ABI dump discovery mismatch: expected=$expectedPaths actual=${dumpsByPath.keys.sorted()}")
        }

        val classRoots = classDirectories.files.filter(File::isDirectory).sortedBy(File::getName)
        if (classRoots.isEmpty()) throw GradleException("compiled public API class directories are missing")
        val violations = sortedSetOf<String>()
        val dumpIdentities = mutableListOf<String>()
        val classIdentities = sortedSetOf<String>()
        var selectedClasses = 0
        var selectedMembers = 0
        var descriptorLocations = 0
        var signatureLocations = 0

        mappings.forEach { mapping ->
            val dumpFile = dumpsByPath.getValue(mapping.dumpPath)
            val dumpBytes = dumpFile.readBytes()
            if (dumpBytes.isEmpty() || dumpBytes.any { it == '\r'.code.toByte() }) {
                throw GradleException("ABI dump must be nonempty UTF-8/LF: ${mapping.dumpPath}")
            }
            val dumpText = decodeUtf8(dumpBytes, mapping.dumpPath)
            val dump = try {
                KotlinAbiDumpParser.parse(dumpText, mapping.packageRoot)
            } catch (failure: KotlinAbiFormatException) {
                throw GradleException("invalid ABI dump ${mapping.dumpPath}: ${failure.message}", failure)
            }
            dumpIdentities += "${mapping.dumpPath}|bytes=${dumpBytes.size}|sha256=${sha256(dumpBytes)}"
            dump.classes.forEach { abiClass ->
                val relativeClass = "${abiClass.internalName}.class"
                val candidates = classRoots.map { it.resolve(relativeClass) }.filter(File::isFile)
                if (candidates.size != 1) {
                    violations +=
                        "${mapping.module}|${mapping.dumpPath}:${abiClass.line}|${abiClass.internalName}|" +
                            "class mapping expected=1 actual=${candidates.size}"
                    return@forEach
                }
                selectedClasses += 1
                val classFile = candidates.single()
                val classBytes = classFile.readBytes()
                classIdentities += "$relativeClass|bytes=${classBytes.size}|sha256=${sha256(classBytes)}"
                val scan = scanClass(abiClass, classBytes)
                selectedMembers += scan.selectedMembers
                descriptorLocations += scan.descriptorTypes.size
                signatureLocations += scan.signatureTypes.size
                scan.missingMembers.forEach { missing ->
                    violations += "${mapping.module}|${mapping.dumpPath}:$missing|$relativeClass|missing selected ABI member"
                }
                (scan.descriptorTypes + scan.signatureTypes).sortedBy(ScannedType::location).forEach { token ->
                    if (forbiddenFamilies.get().any(token.className::startsWith)) {
                        violations +=
                            "${mapping.module}|${mapping.dumpPath}:${token.dumpLine}|${token.entry}|" +
                                "$relativeClass|${token.location}|${token.className}"
                    }
                }
            }
        }

        val report =
            buildString {
                append("{\"schemaVersion\":1,\"scannerSchema\":${jsonString(scannerSchema.get())},")
                append("\"modules\":${jsonArray(mappings.map(Mapping::encoded))},")
                append("\"dumpIdentities\":${jsonArray(dumpIdentities.sorted())},")
                append("\"classIdentities\":${jsonArray(classIdentities)},")
                append("\"selectedClassCount\":$selectedClasses,\"selectedMemberCount\":$selectedMembers,")
                append("\"descriptorLocationCount\":$descriptorLocations,\"signatureLocationCount\":$signatureLocations,")
                append("\"violations\":${jsonArray(violations)}}\n")
            }
        writeUtf8Lf(reportFile.get().asFile, report)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("public API boundary violations ${violations.size}:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
        logger.lifecycle(
            "Public API boundaries OK: ${mappings.size} dumps, $selectedClasses classes, " +
                "$selectedMembers members, $descriptorLocations descriptor and $signatureLocations signature locations.",
        )
    }

    private fun scanClass(abiClass: KotlinAbiClass, bytes: ByteArray): ClassScan {
        val expected = abiClass.members.associateBy { "${it.kind}|${it.name}|${it.descriptor}" }
        val found = mutableSetOf<String>()
        val descriptorTypes = mutableListOf<ScannedType>()
        val signatureTypes = mutableListOf<ScannedType>()
        val reader = try {
            ClassReader(bytes)
        } catch (failure: IllegalArgumentException) {
            throw GradleException("malformed class file for ${abiClass.internalName}", failure)
        }
        if (reader.className != abiClass.internalName) throw GradleException("class identity mismatch for ${abiClass.internalName}")
        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>,
                ) {
                    listOfNotNull(superName).plus(interfaces).forEach { internal ->
                        descriptorTypes += ScannedType("class-descriptor", internal.replace('/', '.'), abiClass.line, name)
                    }
                    signature?.let { collectSignature(it, "class-signature", abiClass.line, name, signatureTypes) }
                }

                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
                    annotationVisitor("class-annotation", descriptor, abiClass.line, abiClass.internalName, descriptorTypes)

                override fun visitTypeAnnotation(
                    typeRef: Int,
                    typePath: org.objectweb.asm.TypePath?,
                    descriptor: String,
                    visible: Boolean,
                ): AnnotationVisitor =
                    annotationVisitor("class-type-annotation", descriptor, abiClass.line, abiClass.internalName, descriptorTypes)

                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): FieldVisitor? {
                    val key = "field|$name|$descriptor"
                    val selected = expected[key] ?: return null
                    found += key
                    addDescriptorTypes(descriptor, "field-descriptor", selected, descriptorTypes)
                    signature?.let {
                        collectSignature(it, "field-signature", selected.line, name, signatureTypes, typeSignature = true)
                    }
                    return object : FieldVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
                            annotationVisitor("field-annotation", descriptor, selected.line, name, descriptorTypes)

                        override fun visitTypeAnnotation(
                            typeRef: Int,
                            typePath: org.objectweb.asm.TypePath?,
                            descriptor: String,
                            visible: Boolean,
                        ): AnnotationVisitor =
                            annotationVisitor("field-type-annotation", descriptor, selected.line, name, descriptorTypes)
                    }
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    val key = "fun|$name|$descriptor"
                    val selected = expected[key] ?: return null
                    found += key
                    addDescriptorTypes(descriptor, "method-descriptor", selected, descriptorTypes)
                    signature?.let { collectSignature(it, "method-signature", selected.line, name, signatureTypes) }
                    exceptions.orEmpty().forEach { exception ->
                        descriptorTypes += ScannedType("method-exception", exception.replace('/', '.'), selected.line, name)
                    }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
                            annotationVisitor("method-annotation", descriptor, selected.line, name, descriptorTypes)

                        override fun visitParameterAnnotation(parameter: Int, descriptor: String, visible: Boolean): AnnotationVisitor =
                            annotationVisitor("parameter-annotation[$parameter]", descriptor, selected.line, name, descriptorTypes)

                        override fun visitTypeAnnotation(
                            typeRef: Int,
                            typePath: org.objectweb.asm.TypePath?,
                            descriptor: String,
                            visible: Boolean,
                        ): AnnotationVisitor =
                            annotationVisitor("method-type-annotation", descriptor, selected.line, name, descriptorTypes)
                    }
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return ClassScan(
            selectedMembers = found.size,
            missingMembers = expected.filterKeys { it !in found }.values.map { "${it.line}|${it.kind} ${it.name} ${it.descriptor}" },
            descriptorTypes = descriptorTypes,
            signatureTypes = signatureTypes,
        )
    }

    private fun addDescriptorTypes(
        descriptor: String,
        location: String,
        member: KotlinAbiMember,
        result: MutableList<ScannedType>,
    ) {
        JvmAbiTypeScanner.typesFromDescriptor(descriptor).forEach { type ->
            result += ScannedType(location, type, member.line, "${member.kind} ${member.name} ${member.descriptor}")
        }
    }

    private fun collectSignature(
        signature: String,
        location: String,
        line: Int,
        entry: String,
        result: MutableList<ScannedType>,
        typeSignature: Boolean = false,
    ) {
        try {
            JvmAbiTypeScanner.typesFromSignature(signature, typeSignature).forEach { type ->
                result += ScannedType(location, type, line, entry)
            }
        } catch (failure: Exception) {
            throw GradleException("malformed $location for $entry", failure)
        }
    }

    private fun annotationVisitor(
        location: String,
        descriptor: String,
        line: Int,
        entry: String,
        result: MutableList<ScannedType>,
    ): AnnotationVisitor {
        JvmAbiTypeScanner.typesFromDescriptor(descriptor).forEach { type ->
            result += ScannedType(location, type, line, entry)
        }
        return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) {
                if (value is Type) {
                    JvmAbiTypeScanner.typesFromDescriptor(value.descriptor).forEach { type ->
                        result += ScannedType("$location-value", type, line, entry)
                    }
                }
            }

            override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor =
                annotationVisitor("$location-nested", descriptor, line, entry, result)

            override fun visitEnum(name: String?, descriptor: String, value: String) {
                JvmAbiTypeScanner.typesFromDescriptor(descriptor).forEach { type ->
                    result += ScannedType("$location-enum", type, line, entry)
                }
            }

            override fun visitArray(name: String?): AnnotationVisitor = this
        }
    }

    private fun decodeMapping(encoded: String): Mapping {
        val fields = encoded.split('|')
        if (fields.size != 3) throw GradleException("invalid public API mapping: $encoded")
        return Mapping(fields[0], fields[1], fields[2])
    }

    private fun decodeUtf8(bytes: ByteArray, path: String): String = try {
        UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (failure: Exception) {
        throw GradleException("ABI dump is not valid UTF-8: $path", failure)
    }

    private data class Mapping(val module: String, val dumpPath: String, val packageRoot: String) {
        val encoded: String get() = "$module|$dumpPath|$packageRoot"
    }

    private data class ScannedType(val location: String, val className: String, val dumpLine: Int, val entry: String)

    private data class ClassScan(
        val selectedMembers: Int,
        val missingMembers: List<String>,
        val descriptorTypes: List<ScannedType>,
        val signatureTypes: List<ScannedType>,
    )
}
