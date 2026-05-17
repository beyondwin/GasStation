package com.gasstation.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDrawReporterTest {
    @Test
    fun `reporter calls fully drawn callback once`() {
        var reportCount = 0
        val reporter = StartupDrawReporter {
            reportCount += 1
        }

        reporter.reportFirstContentDrawn()
        reporter.reportFirstContentDrawn()
        reporter.reportFirstContentDrawn()

        assertEquals(1, reportCount)
    }
}
