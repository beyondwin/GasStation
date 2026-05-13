package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinatesDistanceTest {
    @Test
    fun `distanceTo returns rounded haversine distance in meters`() {
        val origin = Coordinates(37.498095, 127.027610)
        val destination = Coordinates(37.499095, 127.027610)

        assertEquals(111, origin.distanceTo(destination).value)
    }

    @Test
    fun `distanceTo returns zero for identical coordinates`() {
        val origin = Coordinates(37.498095, 127.027610)

        assertEquals(DistanceMeters(0), origin.distanceTo(origin))
    }

    @Test
    fun `distanceTo returns valid distance for near antipodal coordinates`() {
        val origin = Coordinates(89.986, 0.0)
        val destination = Coordinates(-89.986, 180.0)

        val distance = origin.distanceTo(destination)

        assertTrue(distance.value >= 0)
        assertTrue(distance.value in 20_015_000..20_016_000)
    }
}
