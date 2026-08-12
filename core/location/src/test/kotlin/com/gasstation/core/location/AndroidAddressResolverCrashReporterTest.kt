package com.gasstation.core.location

import com.gasstation.core.observability.CrashReporter
import com.gasstation.domain.location.LocationAddressLookupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAddressResolverCrashReporterTest {
    @Test
    fun `non-IO exception is recorded via crashReporter and returned as Error`() = runBlocking {
        val crashReporter = FakeCrashReporter()
        val boom = IllegalStateException("unexpected geocoder state")

        val result = resolveAddressWithReporting(crashReporter) { throw boom }

        assertTrue(result is LocationAddressLookupResult.Error)
        assertEquals(1, crashReporter.records.size)
        val record = crashReporter.records.first()
        assertSame(boom, record.throwable)
        assertEquals("core:location", record.metadata["module"])
        assertEquals("resolveAddress", record.metadata["operation"])
    }

    @Test
    fun `IO exception is NOT recorded via crashReporter`() = runBlocking {
        val crashReporter = FakeCrashReporter()
        val ioError = java.io.IOException("network error")

        val result = resolveAddressWithReporting(crashReporter) { throw ioError }

        assertTrue(result is LocationAddressLookupResult.Error)
        assertEquals(0, crashReporter.records.size)
    }

    @Test
    fun `unexpected geocoder failure remains Error when crashReporter throws`() = runBlocking {
        val original = IllegalStateException("unexpected geocoder state")

        val result = resolveAddressWithReporting(
            crashReporter = ThrowingCrashReporter(IllegalStateException("reporter failed")),
        ) { throw original }

        assertTrue(result is LocationAddressLookupResult.Error)
        assertSame(original, (result as LocationAddressLookupResult.Error).throwable)
    }

    private class FakeCrashReporter : CrashReporter {
        data class Record(val throwable: Throwable, val metadata: Map<String, String>)
        val records = mutableListOf<Record>()
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
            records += Record(throwable, metadata)
        }
        override fun log(message: String) = Unit
    }

    private class ThrowingCrashReporter(private val throwable: Throwable) : CrashReporter {
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>): Nothing = throw this.throwable

        override fun log(message: String) = Unit
    }
}
