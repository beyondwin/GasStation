package com.gasstation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigResourceTest {
    @Test
    fun `default station endpoint mode stays direct`() {
        assertEquals("direct", BuildConfig.STATION_ENDPOINT_MODE)
        assertTrue(BuildConfig.PROXY_BASE_URL.isBlank())
    }
}
