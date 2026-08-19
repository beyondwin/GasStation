package com.gasstation.buildlogic.quality

import java.nio.charset.StandardCharsets.UTF_8

internal fun jsonString(value: String): String =
    buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

internal fun jsonArray(values: Iterable<String>): String =
    values.joinToString(prefix = "[", postfix = "]", separator = ",") { jsonString(it) }

internal fun writeUtf8Lf(file: java.io.File, text: String) {
    require(text.endsWith('\n') && '\r' !in text) { "report must be canonical LF text" }
    file.parentFile.mkdirs()
    file.writeBytes(text.toByteArray(UTF_8))
}
