package com.gasstation.analytics

import org.junit.Test

class NoOpCrashReporterTest {
    @Test
    fun `recordNonFatal does not throw`() {
        val reporter = NoOpCrashReporter()
        reporter.recordNonFatal(IllegalStateException("boom"), mapOf("module" to "test"))
    }

    @Test
    fun `log does not throw`() {
        val reporter = NoOpCrashReporter()
        reporter.log("breadcrumb")
    }
}
