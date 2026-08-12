package com.gasstation.core.observability

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CancellationException

class CrashReporterContractTest {
    @Test
    fun fake_reporter_records_nonfatal() {
        val reporter = FakeCrashReporter()
        val error = IllegalStateException("boom")
        reporter.recordNonFatal(error, mapOf("module" to "station"))
        assertEquals(1, reporter.records.size)
        assertEquals(error, reporter.records.first().throwable)
        assertEquals("station", reporter.records.first().metadata["module"])
    }

    @Test
    fun fake_reporter_logs_breadcrumb() {
        val reporter = FakeCrashReporter()
        reporter.log("refresh started")
        assertEquals(listOf("refresh started"), reporter.logs)
    }

    @Test
    fun `recordNonFatalSafely swallows ordinary reporter exception`() {
        val reporter = throwingReporter(IllegalStateException("reporter failed"))

        reporter.recordNonFatalSafely(IllegalArgumentException("original"))
    }

    @Test(expected = CancellationException::class)
    fun `recordNonFatalSafely preserves reporter cancellation`() {
        throwingReporter(CancellationException("cancelled"))
            .recordNonFatalSafely(IllegalArgumentException("original"))
    }

    @Test(expected = AssertionError::class)
    fun `recordNonFatalSafely does not swallow fatal error`() {
        throwingReporter(AssertionError("fatal"))
            .recordNonFatalSafely(IllegalArgumentException("original"))
    }

    private fun throwingReporter(reporterFailure: Throwable): CrashReporter = object : CrashReporter {
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>): Nothing = throw reporterFailure

        override fun log(message: String) = Unit
    }

    private class FakeCrashReporter : CrashReporter {
        data class Record(val throwable: Throwable, val metadata: Map<String, String>)
        val records = mutableListOf<Record>()
        val logs = mutableListOf<String>()
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
            records += Record(throwable, metadata)
        }
        override fun log(message: String) {
            logs += message
        }
    }
}
