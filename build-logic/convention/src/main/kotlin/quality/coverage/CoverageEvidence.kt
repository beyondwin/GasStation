package com.gasstation.buildlogic.quality.coverage

import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.w3c.dom.Element

internal fun coverageXmlSemanticSha256(
    bytes: ByteArray,
    reportId: String,
): String = sha256Coverage(coverageXmlSemanticRecords(bytes, reportId))

internal fun coverageXmlSemanticRecords(
    bytes: ByteArray,
    reportId: String,
): ByteArray {
    val factory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    val document = factory.newDocumentBuilder().parse(bytes.inputStream())
    val report = document.documentElement
    if (report.tagName != "report") throw GradleException("JaCoCo XML root must be report for $reportId")
    val reportName = report.requiredAttribute("name", reportId)
    val records = mutableListOf<Map<String, Any?>>()
    val identities = mutableSetOf<String>()

    fun append(record: Map<String, Any?>) {
        val identity = canonicalCoverageJson(record).decodeToString()
        if (!identities.add(identity)) throw GradleException("Duplicate XML semantic identity for $reportId")
        records += record
    }

    append(linkedMapOf("kind" to "report-identity", "reportId" to reportId, "name" to reportName))
    appendCounters(report, "report", linkedMapOf("reportId" to reportId), reportId, ::append)
    report.directChildren("package").forEach { packageElement ->
        val packageName = packageElement.requiredAttribute("name", reportId)
        val packageFields = linkedMapOf<String, Any?>("reportId" to reportId, "package" to packageName)
        append(linkedMapOf("kind" to "package-identity") + packageFields)
        appendCounters(packageElement, "package", packageFields, reportId, ::append)
        packageElement.directChildren("class").forEach { classElement ->
            val className = classElement.requiredAttribute("name", reportId)
            if (className.substringBeforeLast('/', "") != packageName) {
                throw GradleException("Class/package mismatch for $reportId: $className")
            }
            val classFields =
                linkedMapOf<String, Any?>(
                    "reportId" to reportId,
                    "package" to packageName,
                    "class" to className,
                    "source" to classElement.optionalAttribute("sourcefilename"),
                )
            append(linkedMapOf("kind" to "class-identity") + classFields)
            appendCounters(classElement, "class", classFields, reportId, ::append)
            classElement.directChildren("method").forEach { methodElement ->
                val methodFields =
                    linkedMapOf<String, Any?>(
                        *classFields.entries.map { it.key to it.value }.toTypedArray(),
                        "method" to methodElement.requiredAttribute("name", reportId),
                        "descriptor" to methodElement.requiredAttribute("desc", reportId),
                        "declaredLine" to methodElement.optionalNonNegativeInt("line", reportId),
                    )
                append(linkedMapOf("kind" to "method-identity") + methodFields)
                appendCounters(methodElement, "method", methodFields, reportId, ::append)
            }
        }
        packageElement.directChildren("sourcefile").forEach { sourceElement ->
            val sourceFields =
                linkedMapOf<String, Any?>(
                    "reportId" to reportId,
                    "package" to packageName,
                    "source" to sourceElement.requiredAttribute("name", reportId),
                )
            append(linkedMapOf("kind" to "source-identity") + sourceFields)
            appendCounters(sourceElement, "source", sourceFields, reportId, ::append)
            val seenLineNumbers = mutableSetOf<Int>()
            sourceElement.directChildren("line").forEach { line ->
                val lineNumber = line.requiredNonNegativeInt("nr", reportId).also {
                    if (it == 0 || !seenLineNumbers.add(it)) {
                        throw GradleException("Duplicate or invalid JaCoCo line number for $reportId")
                    }
                }
                append(
                    linkedMapOf<String, Any?>(
                        "kind" to "source-line",
                        *sourceFields.entries.map { it.key to it.value }.toTypedArray(),
                        "line" to lineNumber,
                        "mi" to line.requiredNonNegativeInt("mi", reportId),
                        "ci" to line.requiredNonNegativeInt("ci", reportId),
                        "mb" to line.requiredNonNegativeInt("mb", reportId),
                        "cb" to line.requiredNonNegativeInt("cb", reportId),
                    ),
                )
            }
        }
    }
    val sorted = records.sortedWith { left, right ->
        compareUnsigned(canonicalCoverageJson(left), canonicalCoverageJson(right))
    }
    return canonicalCoverageJson(sorted)
}

private fun appendCounters(
    element: Element,
    kind: String,
    fields: Map<String, Any?>,
    reportId: String,
    append: (Map<String, Any?>) -> Unit,
) {
    val allowed = setOf("INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS")
    val seen = mutableSetOf<String>()
    element.directChildren("counter").forEach { counter ->
        val type = counter.requiredAttribute("type", reportId)
        if (type !in allowed || !seen.add(type)) {
            throw GradleException("Unknown or duplicate $kind counter $type for $reportId")
        }
        append(
            linkedMapOf<String, Any?>(
                "kind" to "$kind-counter",
                *fields.entries.map { it.key to it.value }.toTypedArray(),
                "type" to type,
                "missed" to counter.requiredNonNegativeInt("missed", reportId),
                "covered" to counter.requiredNonNegativeInt("covered", reportId),
            ),
        )
    }
}

private fun Element.directChildren(name: String): List<Element> =
    buildList {
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child is Element && child.tagName == name) add(child)
        }
    }

private fun Element.requiredAttribute(
    name: String,
    reportId: String,
): String = getAttribute(name).takeIf { hasAttribute(name) }
    ?: throw GradleException("Missing JaCoCo $name attribute for $reportId")

private fun Element.optionalAttribute(name: String): String? =
    getAttribute(name).takeIf { hasAttribute(name) }

private fun Element.requiredNonNegativeInt(
    name: String,
    reportId: String,
): Int {
    val raw = requiredAttribute(name, reportId)
    if (!raw.matches(Regex("0|[1-9][0-9]*"))) {
        throw GradleException("JaCoCo $name must be a canonical non-negative integer for $reportId")
    }
    return raw.toIntOrNull() ?: throw GradleException("JaCoCo $name is too large for $reportId")
}

private fun Element.optionalNonNegativeInt(
    name: String,
    reportId: String,
): Int? = if (hasAttribute(name)) requiredNonNegativeInt(name, reportId) else null

private fun compareUnsigned(
    left: ByteArray,
    right: ByteArray,
): Int {
    val limit = minOf(left.size, right.size)
    for (index in 0 until limit) {
        val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}

internal fun lexicalPackageDeclaration(
    bytes: ByteArray,
    suffix: String,
): String {
    val source = bytes.toString(Charsets.UTF_8)
    if (!source.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
        throw GradleException("Authored source is not valid UTF-8")
    }
    if (suffix == "java" && Regex("\\\\u+[0-9a-fA-F]{4}").containsMatchIn(source)) {
        throw GradleException("Java Unicode escape is forbidden before package lexing")
    }
    if (suffix !in setOf("kt", "java")) throw GradleException("Unsupported authored source suffix: $suffix")
    val cleaned = lexCoverageSource(source)
    val candidates =
        if (suffix == "java") {
            Regex("(?m)^\\s*package\\s+([^;\\n]+)(;?)").findAll(cleaned).map { match ->
                if (match.groupValues[2] != ";") throw GradleException("Java package declaration requires semicolon")
                match.groupValues[1].trim()
            }.toList()
        } else {
            Regex("(?m)^\\s*package\\s+([^\\s;]+)").findAll(cleaned).map { it.groupValues[1] }.toList()
        }
    if (candidates.size != 1) throw GradleException("Expected exactly one package declaration in authored source")
    val packageName = candidates.single()
    if (!packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*"))) {
        throw GradleException("Invalid package declaration: $packageName")
    }
    return packageName
}

private fun lexCoverageSource(source: String): String {
    val output = StringBuilder(source.length)
    var index = 0
    var blockDepth = 0
    fun blank(character: Char) = if (character == '\n') '\n' else ' '
    while (index < source.length) {
        when {
            blockDepth > 0 && source.startsWith("/*", index) -> {
                blockDepth++
                output.append("  ")
                index += 2
            }
            blockDepth > 0 && source.startsWith("*/", index) -> {
                blockDepth--
                output.append("  ")
                index += 2
            }
            blockDepth > 0 -> output.append(blank(source[index++]))
            source.startsWith("/*", index) -> {
                blockDepth = 1
                output.append("  ")
                index += 2
            }
            source.startsWith("//", index) -> {
                while (index < source.length && source[index] != '\n') output.append(' ').also { index++ }
            }
            source.startsWith("\"\"\"", index) -> {
                val end = source.indexOf("\"\"\"", index + 3)
                if (end < 0) throw GradleException("Unterminated triple-quoted string")
                while (index < end + 3) output.append(blank(source[index++]))
            }
            source[index] == '"' || source[index] == '\'' -> {
                val quote = source[index]
                output.append(' ')
                index++
                var escaped = false
                var terminated = false
                while (index < source.length) {
                    val character = source[index++]
                    output.append(blank(character))
                    if (escaped) escaped = false else if (character == '\\') escaped = true else if (character == quote) {
                        terminated = true
                        break
                    }
                }
                if (!terminated) throw GradleException("Unterminated quoted literal")
            }
            else -> output.append(source[index++])
        }
    }
    if (blockDepth != 0) throw GradleException("Unterminated block comment")
    return output.toString()
}

private fun sha256Coverage(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
