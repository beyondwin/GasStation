package com.gasstation.core.designsystem

import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import org.junit.Assert.assertEquals
import org.junit.Test

class ValueFormatsTest {
    @Test
    fun `price digits group thousands`() {
        assertEquals("1,689", MoneyWon(1689).gasStationPriceDigits())
    }

    @Test
    fun `price label appends won unit`() {
        assertEquals("1,689원", MoneyWon(1689).gasStationPriceLabel())
    }

    @Test
    fun `distance digits render one decimal kilometer`() {
        assertEquals("0.3", DistanceMeters(300).gasStationDistanceDigits())
    }

    @Test
    fun `distance label appends km unit`() {
        assertEquals("0.3km", DistanceMeters(300).gasStationDistanceLabel())
    }

    @Test
    fun `units expose canonical strings`() {
        assertEquals("원", GAS_STATION_WON_UNIT)
        assertEquals("km", GAS_STATION_DISTANCE_UNIT)
    }
}
