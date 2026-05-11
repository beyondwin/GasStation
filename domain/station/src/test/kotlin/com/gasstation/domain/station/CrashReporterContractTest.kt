package com.gasstation.domain.station

import org.junit.Assert.assertEquals
import org.junit.Test

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
