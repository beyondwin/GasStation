package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueObjectInvariantTest {

    @Test
    fun `coordinates require latitude within range`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Coordinates(latitude = 91.0, longitude = 127.0)
        }

        assertEquals("latitude must be between -90.0 and 90.0", error.message)
    }

    @Test
    fun `coordinates require longitude within range`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Coordinates(latitude = 37.0, longitude = 181.0)
        }

        assertEquals("longitude must be between -180.0 and 180.0", error.message)
    }

    @Test
    fun `distance meters must be non negative`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DistanceMeters(-1)
        }

        assertEquals("distance meters must be non-negative", error.message)
    }

    @Test
    fun `money won must be non negative`() {
        val error = assertFailsWith<IllegalArgumentException> {
            MoneyWon(-1)
        }

        assertEquals("money won must be non-negative", error.message)
    }
}
