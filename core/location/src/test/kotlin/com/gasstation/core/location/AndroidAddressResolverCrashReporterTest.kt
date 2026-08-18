package com.gasstation.core.location

import android.app.Application
import android.location.Address
import com.gasstation.core.observability.CrashReporter
import com.gasstation.domain.location.LocationAddressLookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidAddressResolverCrashReporterTest {
    @Test
    fun `resolved address returns a display label without reporting`() = runBlocking {
        val crashReporter = FakeCrashReporter()
        val address = Address(Locale.KOREA).apply {
            setAddressLine(0, "서울특별시 강남구 역삼동")
        }

        val result = resolveAddressWithReporting(crashReporter) { address }

        assertTrue(result is LocationAddressLookupResult.Success)
        assertEquals(0, crashReporter.records.size)
    }

    @Test
    fun `address without a display label returns unavailable without reporting`() = runBlocking {
        val crashReporter = FakeCrashReporter()

        val result = resolveAddressWithReporting(crashReporter) { Address(Locale.KOREA) }

        assertEquals(LocationAddressLookupResult.Unavailable, result)
        assertEquals(0, crashReporter.records.size)
    }

    @Test
    fun `lookup cancellation is rethrown without crash reporting`() = runBlocking {
        val crashReporter = FakeCrashReporter()
        val cancellation = CancellationException("cancelled")

        val thrown = runCatching {
            resolveAddressWithReporting(crashReporter) { throw cancellation }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(0, crashReporter.records.size)
    }

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
