package com.gasstation.buildlogic.quality

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
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
    abstract val selectedActiveModules: ListProperty<String>

    @get:Input
    abstract val classRootMappings: ListProperty<String>

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:Input
    abstract val forbiddenFamilies: ListProperty<String>

    @get:Input
    abstract val scannerSchema: org.gradle.api.provider.Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val signaturePolicyFile: RegularFileProperty

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
                        append("\"signaturePolicySha256\":\"unavailable\",\"signatureExpectations\":[],")
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
        val expectedModules = mappings.map(Mapping::module)
        val selectedModules = selectedActiveModules.get().sorted()
        val rootsByModule = decodeClassRoots(classRootMappings.get())
        if (selectedModules != expectedModules || rootsByModule.keys.toList() != expectedModules) {
            throw GradleException(
                "public API topology mismatch: mappings=$expectedModules active=$selectedModules " +
                    "classRoots=${rootsByModule.keys.toList()}",
            )
        }
        val signaturePolicyBytes = signaturePolicyFile.get().asFile.readBytes()
        val signatureExpectations = decodeSignaturePolicy(signaturePolicyBytes, mappings)
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

        val classRoots = classDirectories.files.filter(File::isDirectory).map(File::getCanonicalFile).toSortedSet()
        val mappedRoots = rootsByModule.values.flatten().filter(File::isDirectory).map(File::getCanonicalFile).toSortedSet()
        if (classRoots.isEmpty() || classRoots != mappedRoots || rootsByModule.any { (_, roots) -> roots.none(File::isDirectory) }) {
            throw GradleException("compiled public API class root mismatch")
        }
        val violations = sortedSetOf<String>()
        val dumpIdentities = mutableListOf<String>()
        val classIdentities = sortedSetOf<String>()
        var selectedClasses = 0
        var selectedMembers = 0
        var descriptorLocations = 0
        var signatureLocations = 0

        mappings.forEach { mapping ->
            val moduleRoots = rootsByModule.getValue(mapping.module)
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
            val memberKeys = dump.classes.flatMap { abiClass ->
                abiClass.members.map { member ->
                    SignatureKey(mapping.module, abiClass.internalName, member.kind, member.name, member.descriptor)
                }
            }.toSet()
            val unknownSignatureExpectations = signatureExpectations.keys.filter { key ->
                key.module == mapping.module && key !in memberKeys
            }
            if (unknownSignatureExpectations.isNotEmpty()) {
                throw GradleException(
                    "signature expectation does not select an ABI member: ${unknownSignatureExpectations.sortedBy(SignatureKey::encoded)}",
                )
            }
            dumpIdentities += "${mapping.dumpPath}|bytes=${dumpBytes.size}|sha256=${sha256(dumpBytes)}"
            dump.classes.forEach { abiClass ->
                val relativeClass = "${abiClass.internalName}.class"
                val candidates = moduleRoots.map { it to it.resolve(relativeClass) }.filter { it.second.isFile }
                if (candidates.size != 1) {
                    violations +=
                        "${mapping.module}|${mapping.dumpPath}:${abiClass.line}|${abiClass.internalName}|" +
                            "class mapping expected=1 actual=${candidates.size}"
                    return@forEach
                }
                selectedClasses += 1
                val (selectedRoot, classFile) = candidates.single()
                val classBytes = classFile.readBytes()
                classIdentities +=
                    "${mapping.module}|$relativeClass|root=${relativeToRoot(selectedRoot)}|" +
                        "bytes=${classBytes.size}|sha256=${sha256(classBytes)}"
                val requiredSignatures = signatureExpectations.filterKeys { key ->
                    key.module == mapping.module && key.className == abiClass.internalName
                }.mapKeys { (key, _) -> key.memberKey }
                val scan = scanClass(abiClass, classBytes, requiredSignatures)
                selectedMembers += scan.selectedMembers
                descriptorLocations += scan.descriptorTypes.size
                signatureLocations += scan.signatureTypes.size
                scan.signatureViolations.forEach { violation ->
                    violations += "${mapping.module}|${mapping.dumpPath}:$violation|$relativeClass"
                }
                scan.missingMembers.forEach { missing ->
                    val member = abiClass.members.single { "${it.line}|${it.kind} ${it.name} ${it.descriptor}" == missing }
                    val inherited = resolveInheritedMember(abiClass, member, classBytes, moduleRoots)
                    when {
                        inherited.owners.size > 1 ->
                            violations +=
                                "${mapping.module}|${mapping.dumpPath}:$missing|$relativeClass|" +
                                    "inherited selected ABI member owner expected=1 actual=${inherited.owners}"
                        inherited.scan == null ->
                            violations +=
                                "${mapping.module}|${mapping.dumpPath}:$missing|$relativeClass|missing selected ABI member"
                        else -> {
                            selectedMembers += inherited.scan.selectedMembers
                            descriptorLocations += inherited.scan.descriptorTypes.size
                            signatureLocations += inherited.scan.signatureTypes.size
                            inherited.scan.descriptorTypes.forEach { token ->
                                if (forbiddenFamilies.get().any(token.className::startsWith)) {
                                    violations += forbiddenViolation(mapping, token, relativeClass)
                                }
                            }
                            inherited.scan.signatureViolations.forEach { violation ->
                                violations += "${mapping.module}|${mapping.dumpPath}:$violation|$relativeClass"
                            }
                            inherited.scan.signatureTypes.forEach { token ->
                                if (forbiddenFamilies.get().any(token.className::startsWith)) {
                                    violations += forbiddenViolation(mapping, token, relativeClass)
                                }
                            }
                        }
                    }
                }
                (scan.descriptorTypes + scan.signatureTypes).sortedBy(ScannedType::location).forEach { token ->
                    if (forbiddenFamilies.get().any(token.className::startsWith)) {
                        violations += forbiddenViolation(mapping, token, relativeClass)
                    }
                }
            }
        }

        val report =
            buildString {
                append("{\"schemaVersion\":1,\"scannerSchema\":${jsonString(scannerSchema.get())},")
                append("\"modules\":${jsonArray(mappings.map(Mapping::encoded))},")
                append("\"signaturePolicySha256\":${jsonString(sha256(signaturePolicyBytes))},")
                append("\"signatureExpectations\":${jsonArray(signatureExpectations.entries.map { (key, value) -> "${key.encoded}|$value" }.sorted())},")
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

    private fun scanClass(
        abiClass: KotlinAbiClass,
        bytes: ByteArray,
        requiredSignatures: Map<String, String> = emptyMap(),
    ): ClassScan {
        val expected = abiClass.members.associateBy { "${it.kind}|${it.name}|${it.descriptor}" }
        val found = mutableSetOf<String>()
        val descriptorTypes = mutableListOf<ScannedType>()
        val signatureTypes = mutableListOf<ScannedType>()
        val signatureViolations = mutableListOf<String>()
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
                    verifyRequiredSignature(
                        requiredSignatures[key],
                        signature,
                        "field",
                        selected,
                        signatureViolations,
                    )
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
                    verifyRequiredSignature(
                        requiredSignatures[key],
                        signature,
                        "method",
                        selected,
                        signatureViolations,
                    )
                    exceptions.orEmpty().forEach { exception ->
                        descriptorTypes += ScannedType("method-exception", exception.replace('/', '.'), selected.line, name)
                    }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotationDefault(): AnnotationVisitor =
                            annotationValueVisitor("annotation-default", selected.line, name, descriptorTypes)

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
            signatureViolations = signatureViolations,
        )
    }

    private fun verifyRequiredSignature(
        expected: String?,
        actual: String?,
        memberKind: String,
        member: KotlinAbiMember,
        violations: MutableList<String>,
    ) {
        if (expected == null) return
        when {
            actual == null ->
                violations +=
                    "${member.line}|${member.kind} ${member.name} ${member.descriptor}|" +
                        "missing required $memberKind Signature expected=$expected"
            actual != expected ->
                violations +=
                    "${member.line}|${member.kind} ${member.name} ${member.descriptor}|" +
                        "required $memberKind Signature mismatch expected=$expected actual=$actual"
        }
    }

    private fun resolveInheritedMember(
        abiClass: KotlinAbiClass,
        member: KotlinAbiMember,
        ownerBytes: ByteArray,
        moduleRoots: List<File>,
    ): InheritedResolution {
        val pending = ArrayDeque(classParents(ownerBytes))
        val visited = mutableSetOf<String>()
        val owners = mutableListOf<Pair<String, ClassScan>>()
        while (pending.isNotEmpty()) {
            val internalName = pending.removeFirst()
            if (!visited.add(internalName)) continue
            val candidates = moduleRoots.map { it.resolve("$internalName.class") }.filter(File::isFile)
            if (candidates.size > 1) {
                return InheritedResolution(candidates.map { internalName }, null)
            }
            val candidate = candidates.singleOrNull() ?: continue
            val bytes = candidate.readBytes()
            val inheritedClass = abiClass.copy(internalName = internalName, members = listOf(member))
            val scan = scanClass(inheritedClass, bytes)
            if (scan.selectedMembers == 1) owners += internalName to scan
            pending.addAll(classParents(bytes))
        }
        return InheritedResolution(owners.map { it.first }.sorted(), owners.singleOrNull()?.second)
    }

    private fun classParents(bytes: ByteArray): List<String> {
        val reader = ClassReader(bytes)
        return listOfNotNull(reader.superName).plus(reader.interfaces).sorted()
    }

    private fun forbiddenViolation(mapping: Mapping, token: ScannedType, relativeClass: String): String =
        "${mapping.module}|${mapping.dumpPath}:${token.dumpLine}|${token.entry}|" +
            "$relativeClass|${token.location}|${token.className}"

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
        return annotationValueVisitor(location, line, entry, result)
    }

    private fun annotationValueVisitor(
        location: String,
        line: Int,
        entry: String,
        result: MutableList<ScannedType>,
    ): AnnotationVisitor =
        object : AnnotationVisitor(Opcodes.ASM9) {
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

    private fun decodeMapping(encoded: String): Mapping {
        val fields = encoded.split('|')
        if (fields.size != 3) throw GradleException("invalid public API mapping: $encoded")
        return Mapping(fields[0], fields[1], fields[2])
    }

    private fun decodeSignaturePolicy(
        bytes: ByteArray,
        mappings: List<Mapping>,
    ): Map<SignatureKey, String> {
        val text = decodeUtf8(bytes, "config/quality/public-api-signatures.txt")
        if ('\r' in text || !text.endsWith('\n')) {
            throw GradleException("signature policy must use UTF-8/LF with one trailing LF")
        }
        val lines = text.dropLast(1).split('\n')
        if (lines.firstOrNull() != "schema-version=1" || lines.drop(1).any(String::isBlank)) {
            throw GradleException("invalid public API signature policy header or blank row")
        }
        val mappingByModule = mappings.associateBy(Mapping::module)
        val entries = lines.drop(1).map { encoded ->
            val fields = encoded.split('|')
            if (fields.size != 6) throw GradleException("invalid public API signature expectation: $encoded")
            val key = SignatureKey(fields[0], fields[1], fields[2], fields[3], fields[4])
            val mapping = mappingByModule[key.module]
                ?: throw GradleException("signature expectation has unmapped module: $encoded")
            val dottedClass = key.className.replace('/', '.')
            if (dottedClass != mapping.packageRoot && !dottedClass.startsWith("${mapping.packageRoot}.")) {
                throw GradleException("signature expectation class is outside mapped package: $encoded")
            }
            when (key.kind) {
                "field" -> {
                    JvmDescriptorGrammar.requireFieldDescriptor(key.descriptor)
                    JvmSignatureGrammar.requireSignature(fields[5], typeSignature = true)
                }
                "fun" -> {
                    JvmDescriptorGrammar.requireMethodDescriptor(key.descriptor)
                    JvmSignatureGrammar.requireSignature(fields[5], typeSignature = false)
                }
                else -> throw GradleException("invalid signature expectation member kind: $encoded")
            }
            key to fields[5]
        }
        if (lines.drop(1) != lines.drop(1).sorted() || entries.size != entries.toSet().size) {
            throw GradleException("public API signature expectations must be sorted and unique")
        }
        return entries.toMap()
    }

    private fun decodeClassRoots(encodedRoots: List<String>): Map<String, List<File>> {
        val decoded = encodedRoots.sorted().map { encoded ->
            val fields = encoded.split('|')
            if (fields.size != 2 || fields[0].isBlank() || fields[1].isBlank()) {
                throw GradleException("invalid public API class root mapping: $encoded")
            }
            fields[0] to repositoryRoot.get().asFile.resolve(fields[1]).canonicalFile
        }
        if (decoded.size != decoded.toSet().size) {
            throw GradleException("duplicate public API class root mapping")
        }
        return decoded.groupBy({ it.first }, { it.second }).toSortedMap()
    }

    private fun relativeToRoot(file: File): String =
        file.relativeTo(repositoryRoot.get().asFile).invariantSeparatorsPath

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

    private data class SignatureKey(
        val module: String,
        val className: String,
        val kind: String,
        val name: String,
        val descriptor: String,
    ) {
        val memberKey: String get() = "$kind|$name|$descriptor"
        val encoded: String get() = "$module|$className|$kind|$name|$descriptor"
    }

    private data class ClassScan(
        val selectedMembers: Int,
        val missingMembers: List<String>,
        val descriptorTypes: List<ScannedType>,
        val signatureTypes: List<ScannedType>,
        val signatureViolations: List<String>,
    )

    private data class InheritedResolution(val owners: List<String>, val scan: ClassScan?)
}
