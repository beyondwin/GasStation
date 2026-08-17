package com.gasstation.buildlogic.quality.coverage

import java.nio.charset.StandardCharsets
import java.text.Normalizer

internal fun canonicalCoverageJson(value: Any?): ByteArray =
    buildString { appendCoverageJson(value) }.toByteArray(StandardCharsets.UTF_8)

private fun StringBuilder.appendCoverageJson(value: Any?) {
    when (value) {
        null -> append("null")
        is Boolean, is Int, is Long -> append(value)
        is String -> appendCoverageString(value)
        is Map<*, *> -> {
            append('{')
            value.entries
                .map { entry ->
                    require(entry.key is String) { "Coverage JSON object keys must be strings" }
                    Normalizer.normalize(entry.key as String, Normalizer.Form.NFC) to entry.value
                }
                .sortedBy { it.first }
                .forEachIndexed { index, (key, item) ->
                    if (index > 0) append(',')
                    appendCoverageString(key)
                    append(':')
                    appendCoverageJson(item)
                }
            append('}')
        }
        is Iterable<*> -> {
            append('[')
            value.forEachIndexed { index, item ->
                if (index > 0) append(',')
                appendCoverageJson(item)
            }
            append(']')
        }
        else -> error("Unsupported coverage JSON type: ${value::class.java.name}")
    }
}

private fun StringBuilder.appendCoverageString(raw: String) {
    val value = Normalizer.normalize(raw, Normalizer.Form.NFC)
    append('"')
    value.forEach { character ->
        when {
            character == '"' -> append("\\\"")
            character == '\\' -> append("\\\\")
            character.code in 0..0x1f -> append("\\u%04x".format(character.code))
            else -> append(character)
        }
    }
    append('"')
}
