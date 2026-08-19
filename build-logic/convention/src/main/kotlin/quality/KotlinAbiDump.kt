package com.gasstation.buildlogic.quality

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

internal data class KotlinAbiDump(val classes: List<KotlinAbiClass>)

internal data class KotlinAbiClass(
    val internalName: String,
    val superTypes: List<String>,
    val line: Int,
    val members: List<KotlinAbiMember>,
)

internal data class KotlinAbiMember(
    val kind: String,
    val name: String,
    val descriptor: String,
    val line: Int,
)

internal object KotlinAbiDumpParser {
    private val modifierOrder =
        listOf("public", "protected", "private", "static", "final", "abstract", "synthetic", "interface", "annotation")
            .withIndex().associate { it.value to it.index }
    private val className = Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:[/.][A-Za-z_$][A-Za-z0-9_$]*)*")
    private val memberName = Regex("[A-Za-z_$<>][A-Za-z0-9_$<>-]*")

    fun parse(text: String, packageRoot: String): KotlinAbiDump {
        if ('\r' in text || !text.endsWith('\n')) throw KotlinAbiFormatException("ABI dump must use UTF-8/LF with trailing LF")
        val lines = text.split('\n')
        val classes = mutableListOf<KotlinAbiClass>()
        var current: MutableClass? = null
        lines.dropLast(1).forEachIndexed { index, raw ->
            val lineNumber = index + 1
            when {
                raw.isBlank() -> {
                    if (current != null) throw KotlinAbiFormatException("line $lineNumber: blank line inside class block")
                }
                raw == "}" -> {
                    val completed = current ?: throw KotlinAbiFormatException("line $lineNumber: unmatched class terminator")
                    classes += completed.finish()
                    current = null
                }
                raw.startsWith("\t") -> {
                    val owner = current ?: throw KotlinAbiFormatException("line $lineNumber: member outside class")
                    owner.members += parseMember(raw.removePrefix("\t"), lineNumber)
                }
                else -> {
                    if (current != null) throw KotlinAbiFormatException("line $lineNumber: unbalanced class block")
                    current = parseClass(raw, lineNumber, packageRoot)
                }
            }
        }
        if (current != null) throw KotlinAbiFormatException("unbalanced class block at end of dump")
        if (classes.isEmpty()) throw KotlinAbiFormatException("ABI dump contains no classes")
        if (classes.map { it.internalName }.size != classes.map { it.internalName }.toSet().size) {
            throw KotlinAbiFormatException("duplicate class record")
        }
        return KotlinAbiDump(classes)
    }

    private fun parseClass(raw: String, line: Int, packageRoot: String): MutableClass {
        if (!raw.endsWith(" {")) throw KotlinAbiFormatException("line $line: malformed class record")
        val body = raw.removeSuffix(" {")
        val marker = " class "
        val markerIndex = body.indexOf(marker)
        if (markerIndex <= 0) throw KotlinAbiFormatException("line $line: unknown ABI record")
        validateModifiers(body.substring(0, markerIndex), line)
        val declaration = body.substring(markerIndex + marker.length)
        val parts = declaration.split(" : ", limit = 2)
        val internalName = normalizeClassName(parts[0], line)
        val dotted = internalName.replace('/', '.')
        if (dotted != packageRoot && !dotted.startsWith("$packageRoot.")) {
            throw KotlinAbiFormatException("line $line: class $dotted is outside package root $packageRoot")
        }
        val supers = if (parts.size == 1) emptyList() else parts[1].split(", ").map { normalizeClassName(it, line) }
        return MutableClass(internalName, supers, line)
    }

    private fun parseMember(raw: String, line: Int): KotlinAbiMember {
        val tokens = raw.split(' ')
        val kindIndex = tokens.indexOfFirst { it == "field" || it == "fun" }
        if (kindIndex <= 0 || tokens.size != kindIndex + 3) {
            throw KotlinAbiFormatException("line $line: unknown or malformed member record")
        }
        validateModifiers(tokens.take(kindIndex).joinToString(" "), line)
        val kind = tokens[kindIndex]
        val name = tokens[kindIndex + 1]
        if (!memberName.matches(name)) throw KotlinAbiFormatException("line $line: malformed JVM member name")
        val descriptor = tokens[kindIndex + 2]
        if ('.' in descriptor || "//" in descriptor) {
            throw KotlinAbiFormatException("line $line: object descriptors must use canonical JVM slash syntax")
        }
        if (kind == "fun" && (descriptor.count { it == '(' } != 1 || descriptor.count { it == ')' } != 1 || descriptor.indexOf(')') <= 0 || descriptor.endsWith(')'))) {
            throw KotlinAbiFormatException("line $line: malformed JVM method descriptor")
        }
        try {
            if (kind == "field") Type.getType(descriptor) else Type.getMethodType(descriptor)
        } catch (failure: IllegalArgumentException) {
            throw KotlinAbiFormatException("line $line: malformed JVM descriptor $descriptor", failure)
        }
        if (kind == "field" && descriptor.startsWith('(')) {
            throw KotlinAbiFormatException("line $line: field uses a method descriptor")
        }
        if (kind == "fun" && !descriptor.startsWith('(')) {
            throw KotlinAbiFormatException("line $line: function uses a field descriptor")
        }
        return KotlinAbiMember(kind, name, descriptor, line)
    }

    private fun validateModifiers(raw: String, line: Int) {
        val modifiers = raw.split(' ')
        if (modifiers.isEmpty() || modifiers.any { it !in modifierOrder }) {
            throw KotlinAbiFormatException("line $line: unknown modifier in '$raw'")
        }
        val indexes = modifiers.map { modifierOrder.getValue(it) }
        if (indexes != indexes.sorted() || modifiers.size != modifiers.toSet().size) {
            throw KotlinAbiFormatException("line $line: modifiers must be unique and writer-ordered")
        }
    }

    private fun normalizeClassName(value: String, line: Int): String {
        if (!className.matches(value) || value.contains("//") || value.contains("..") || (value.contains('/') && value.contains('.'))) {
            throw KotlinAbiFormatException("line $line: malformed class name $value")
        }
        return value.replace('.', '/')
    }

    private data class MutableClass(
        val internalName: String,
        val superTypes: List<String>,
        val line: Int,
        val members: MutableList<KotlinAbiMember> = mutableListOf(),
    ) {
        fun finish(): KotlinAbiClass {
            val keys = members.map { "${it.kind}|${it.name}|${it.descriptor}" }
            if (keys.size != keys.toSet().size) throw KotlinAbiFormatException("duplicate member record in $internalName")
            return KotlinAbiClass(internalName, superTypes, line, members.toList())
        }
    }
}

internal object JvmAbiTypeScanner {
    private val forbiddenPrefixes =
        listOf("android.", "androidx.", "com.google.android.gms.", "retrofit2.", "okhttp3.", "com.google.gson.")

    fun typesFromDescriptor(descriptor: String): Set<String> {
        val types = sortedSetOf<String>()
        val root = try {
            if (descriptor.startsWith('(')) Type.getMethodType(descriptor) else Type.getType(descriptor)
        } catch (failure: IllegalArgumentException) {
            throw KotlinAbiFormatException("malformed JVM descriptor $descriptor", failure)
        }
        if (root.sort == Type.METHOD) {
            root.argumentTypes.forEach { collect(it, types) }
            collect(root.returnType, types)
        } else {
            collect(root, types)
        }
        return types
    }

    fun isForbiddenType(className: String): Boolean = forbiddenPrefixes.any(className::startsWith)

    fun typesFromSignature(signature: String, typeSignature: Boolean): List<String> {
        val types = mutableListOf<String>()
        try {
            val visitor =
                object : SignatureVisitor(Opcodes.ASM9) {
                    private var currentClass: String? = null

                    override fun visitClassType(name: String) {
                        currentClass = name
                        types += name.replace('/', '.')
                    }

                    override fun visitInnerClassType(name: String) {
                        val owner = requireNotNull(currentClass) { "inner signature type without owner" }
                        val inner = "$owner\$$name"
                        currentClass = inner
                        types += inner.replace('/', '.')
                    }
                }
            val reader = SignatureReader(signature)
            if (typeSignature) reader.acceptType(visitor) else reader.accept(visitor)
        } catch (failure: Exception) {
            throw KotlinAbiFormatException("malformed JVM signature $signature", failure)
        }
        return types
    }

    private fun collect(type: Type, result: MutableSet<String>) {
        when (type.sort) {
            Type.ARRAY -> collect(type.elementType, result)
            Type.OBJECT -> result += type.className
        }
    }
}

internal class KotlinAbiFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
