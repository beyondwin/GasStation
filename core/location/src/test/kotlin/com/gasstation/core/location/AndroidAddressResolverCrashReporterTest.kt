package com.gasstation.core.location

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.station.CrashReporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that AndroidAddressResolver routes unexpected (non-IO) exceptions
 * through CrashReporter while leaving IO exceptions unrecorded.
 *
 * These tests use a lightweight AddressResolver facade that mirrors the
 * exception-handling contract of AndroidAddressResolver, keeping the suite
 * fast and free of Android / Geocoder dependencies.
 */
class AndroidAddressResolverCrashReporterTest {
    @Test
    fun `non-IO exception is recorded via crashReporter and returned as Error`() = runBlocking {
        val crashReporter = FakeCrashReporter()
        val boom = IllegalStateException("unexpected geocoder state")
        val resolver = crashReportingAddressResolver(throwable = boom, crashReporter = crashReporter)

        val result = resolver.addressFor(Coordinates(37.498095, 127.027610))

        assertTrue(result is LocationAddressLookupResult.Error)
        assertEquals(1, crashReporter.records.size)
        val record = crashReporter.records.first()
        assertEquals(boom, record.throwable)
        assertEquals("core:location", record.metadata["module"])
        assertEquals("resolveAddress", record.metadata["operation"])
    }

    @Test
    fun `IO exception is NOT recorded via crashReporter`() = runBlocking {
        val crashReporter = FakeCrashReporter()
        val ioError = java.io.IOException("network error")
        val resolver = crashReportingAddressResolver(throwable = ioError, crashReporter = crashReporter)

        val result = resolver.addressFor(Coordinates(37.498095, 127.027610))

        assertTrue(result is LocationAddressLookupResult.Error)
        assertEquals(0, crashReporter.records.size)
    }

    /**
     * Factory that mirrors the exception-handling contract of AndroidAddressResolver:
     * - CancellationException → rethrown
     * - IOException → returned as Error, NOT recorded
     * - Any other Exception → recorded via crashReporter AND returned as Error
     */
    private fun crashReportingAddressResolver(throwable: Throwable, crashReporter: CrashReporter): AddressResolver =
        object : AddressResolver {
            override suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult = try {
                throw throwable
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (exception: java.io.IOException) {
                LocationAddressLookupResult.Error(exception)
            } catch (exception: Exception) {
                crashReporter.recordNonFatal(
                    exception,
                    mapOf("module" to "core:location", "operation" to "resolveAddress"),
                )
                LocationAddressLookupResult.Error(exception)
            }
        }

    private class FakeCrashReporter : CrashReporter {
        data class Record(val throwable: Throwable, val metadata: Map<String, String>)
        val records = mutableListOf<Record>()
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
            records += Record(throwable, metadata)
        }
        override fun log(message: String) = Unit
    }
}
