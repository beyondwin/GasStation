package com.gasstation.domain.station

import com.gasstation.domain.station.model.StationPriceDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StationPriceDeltaTest {
    @Test
    fun `from prices returns decreased when current price is cheaper`() {
        assertEquals(
            StationPriceDelta.Decreased(20),
            StationPriceDelta.from(previousPriceWon = 1700, currentPriceWon = 1680),
        )
    }

    @Test
    fun `from prices returns unavailable when previous price is missing`() {
        assertEquals(
            StationPriceDelta.Unavailable,
            StationPriceDelta.from(previousPriceWon = null, currentPriceWon = 1680),
        )
    }

    @Test
    fun `from prices returns unchanged when prices match`() {
        assertEquals(
            StationPriceDelta.Unchanged,
            StationPriceDelta.from(previousPriceWon = 1680, currentPriceWon = 1680),
        )
    }

    @Test
    fun `from prices returns increased when current price is more expensive`() {
        assertEquals(
            StationPriceDelta.Increased(20),
            StationPriceDelta.from(previousPriceWon = 1680, currentPriceWon = 1700),
        )
    }

    @Test
    fun `from prices rejects negative current price`() {
        assertThrows(IllegalArgumentException::class.java) {
            StationPriceDelta.from(previousPriceWon = 1680, currentPriceWon = -1)
        }
    }

    @Test
    fun `delta variants reject zero amounts`() {
        assertThrows(IllegalArgumentException::class.java) {
            StationPriceDelta.Increased(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StationPriceDelta.Decreased(0)
        }
    }
}
