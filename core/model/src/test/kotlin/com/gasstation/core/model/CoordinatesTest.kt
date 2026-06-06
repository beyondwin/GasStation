package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CoordinatesTest {

    @Test
    fun `ofOrNull returns instance for in-range coordinates`() {
        val coordinates = Coordinates.ofOrNull(latitude = 37.4987, longitude = 127.0285)

        assertEquals(Coordinates(latitude = 37.4987, longitude = 127.0285), coordinates)
    }

    @Test
    fun `ofOrNull returns null for out-of-range latitude`() {
        assertNull(Coordinates.ofOrNull(latitude = 200.0, longitude = 0.0))
    }

    @Test
    fun `ofOrNull returns null for out-of-range longitude`() {
        assertNull(Coordinates.ofOrNull(latitude = 0.0, longitude = 200.0))
    }

    @Test
    fun `constructor still fails fast for out-of-range latitude`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinates(latitude = 200.0, longitude = 0.0)
        }
    }
}
