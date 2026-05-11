package com.gasstation.analytics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class LogcatCrashReporterTest {
    private val capturedLogs = mutableListOf<CapturedLog>()

    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            capturedLogs += CapturedLog(priority = priority, tag = tag, message = message, throwable = t)
        }
    }

    @Before
    fun setUp() {
        Timber.plant(testTree)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `recordNonFatal logs at ERROR level with metadata as key=value prefix`() {
        val reporter = LogcatCrashReporter()
        val error = IllegalStateException("something broke")

        reporter.recordNonFatal(error, mapOf("module" to "data:station", "operation" to "refresh"))

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs.single()
        assertEquals(android.util.Log.ERROR, log.priority)
        assertEquals("GasStationCrash", log.tag)
        assertTrue("Expected message to contain 'module=data:station'", log.message.contains("module=data:station"))
        assertTrue("Expected message to contain 'operation=refresh'", log.message.contains("operation=refresh"))
        assertEquals(error, log.throwable)
    }

    @Test
    fun `log logs breadcrumb at INFO level`() {
        val reporter = LogcatCrashReporter()

        reporter.log("refresh started")

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs.single()
        assertEquals(android.util.Log.INFO, log.priority)
        assertEquals("GasStationCrash", log.tag)
        assertEquals("refresh started", log.message)
    }

    private data class CapturedLog(val priority: Int, val tag: String?, val message: String, val throwable: Throwable?)
}
