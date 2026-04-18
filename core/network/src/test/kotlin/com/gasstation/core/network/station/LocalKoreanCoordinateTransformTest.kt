package com.gasstation.core.network.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalKoreanCoordinateTransformTest {
    @Test
    fun `converter round-trips gangnam station exit 2 between WGS84 and KTM`() {
        val ktm = LocalKoreanCoordinateTransform.wgs84ToKtm(
            latitude = 37.497927,
            longitude = 127.027583,
        )

        assertTrue(ktm.x > 0.0)
        assertTrue(ktm.y > 0.0)

        val wgs84 = LocalKoreanCoordinateTransform.ktmToWgs84(
            x = ktm.x,
            y = ktm.y,
        )

        assertEquals(37.497927, wgs84.latitude, 0.0005)
        assertEquals(127.027583, wgs84.longitude, 0.0005)
    }
}
