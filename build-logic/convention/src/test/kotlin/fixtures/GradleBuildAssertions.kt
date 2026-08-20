package com.gasstation.buildlogic.testing

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

fun BuildResult.assertTaskOutcome(path: String, expected: TaskOutcome) {
    val task = task(path)
        ?: throw AssertionError(
            "Task $path missing; available=${availableTaskOutcomes()}; outputTail=${output.takeLast(30_000)}",
        )
    if (task.outcome != expected) {
        throw AssertionError(
            "Task $path outcome mismatch: expected=$expected actual=${task.outcome}",
        )
    }
}

fun BuildResult.assertOutputContainsExactlyOnce(sentinel: String) {
    require(sentinel.isNotBlank()) { "Output sentinel must not be blank" }
    val occurrences = output.countLiteralOccurrences(sentinel)
    if (occurrences != 1) {
        throw AssertionError(
            "Expected sentinel exactly once but found $occurrences: $sentinel; outputTail=${output.boundedTail()}",
        )
    }
}

fun BuildResult.assertOutputKeyValueExactlyOnce(
    key: String,
    expectedValue: String,
): String {
    require(STRUCTURED_OUTPUT_KEY.matches(key)) {
        "Structured output key must contain only uppercase ASCII letters, digits, and underscores: $key"
    }
    require(expectedValue.isNotEmpty() && '\n' !in expectedValue && '\r' !in expectedValue) {
        "Structured output value must be a non-empty single-line value"
    }

    val keyPrefix = "$key="
    val keyLines =
        output.lineSequence()
            .filter { line -> line.trim().startsWith(keyPrefix) }
            .toList()
    if (keyLines.size != 1) {
        throw AssertionError(
            "Expected exactly one structured output line for $key but found ${keyLines.size}; " +
                "outputTail=${output.boundedTail()}",
        )
    }

    val expectedLine = keyPrefix + expectedValue
    val actualLine = keyLines.single()
    if (actualLine != expectedLine) {
        throw AssertionError(
            "Structured output mismatch for $key: expected=$expectedLine actual=$actualLine; " +
                "outputTail=${output.boundedTail()}",
        )
    }
    return actualLine.removePrefix(keyPrefix)
}

fun BuildResult.assertOutputDoesNotContain(sentinel: String) {
    require(sentinel.isNotBlank()) { "Output sentinel must not be blank" }
    val occurrences = output.countLiteralOccurrences(sentinel)
    if (occurrences != 0) {
        throw AssertionError(
            "Expected sentinel to be absent but found $occurrences occurrence(s): " +
                "$sentinel; outputTail=${output.boundedTail()}",
        )
    }
}

fun BuildResult.assertConfigurationCacheStored() {
    assertExactOutputLine(CONFIGURATION_CACHE_STORED)
}

fun BuildResult.assertConfigurationCacheReused() {
    assertExactOutputLine(CONFIGURATION_CACHE_REUSED)
    val forbiddenLines =
        output.lineSequence().filter { line ->
            line == CONFIGURATION_CACHE_STORED || line.startsWith("Calculating task graph")
        }.toList()
    if (forbiddenLines.isNotEmpty()) {
        throw AssertionError(
            "Expected configuration-cache reuse without graph calculation/storage, " +
                "but found $forbiddenLines; outputTail=${output.boundedTail()}",
        )
    }
}

private fun BuildResult.assertExactOutputLine(expectedLine: String) {
    val occurrences = output.lineSequence().count { line -> line == expectedLine }
    if (occurrences != 1) {
        throw AssertionError(
            "Expected exact output line once but found $occurrences: $expectedLine; " +
                "outputTail=${output.boundedTail()}",
        )
    }
}

private fun BuildResult.availableTaskOutcomes(): String =
    tasks.sortedBy { it.path }.joinToString(
        prefix = "[",
        postfix = "]",
        limit = MAX_DIAGNOSTIC_TASKS,
        truncated = "...",
    ) { "${it.path}=${it.outcome}" }

private fun String.countLiteralOccurrences(sentinel: String): Int {
    var count = 0
    var index = indexOf(sentinel)
    while (index >= 0) {
        count += 1
        index = indexOf(sentinel, startIndex = index + sentinel.length)
    }
    return count
}

private fun String.boundedTail(): String {
    val outputLines = lines()
    val diagnosticLines =
        if (outputLines.size <= MAX_DIAGNOSTIC_LINES * 2) {
            outputLines
        } else {
            outputLines.take(MAX_DIAGNOSTIC_LINES) +
                listOf("... output truncated ...") +
                outputLines.takeLast(MAX_DIAGNOSTIC_LINES)
        }
    return diagnosticLines.joinToString("\\n").takeLast(MAX_DIAGNOSTIC_CHARACTERS)
}

private const val MAX_DIAGNOSTIC_TASKS = 40
private const val MAX_DIAGNOSTIC_LINES = 12
private const val MAX_DIAGNOSTIC_CHARACTERS = 2_000
private const val CONFIGURATION_CACHE_STORED = "Configuration cache entry stored."
private const val CONFIGURATION_CACHE_REUSED = "Reusing configuration cache."
private val STRUCTURED_OUTPUT_KEY = Regex("[A-Z][A-Z0-9_]*")
