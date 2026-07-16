package com.gasstation.feature.stationlist

import com.gasstation.core.model.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationListDecisionSummaryTest {
    @Test
    fun `empty list has no decision summary`() {
        assertNull(StationListDecisionSummary.from(emptyList()))
    }

    @Test
    fun `single item omits average comparison`() {
        val summary = requireNotNull(StationListDecisionSummary.from(listOf(item(1_712))))
        assertEquals(1, summary.count)
        assertEquals(1_712, summary.lowestPriceWon)
        assertNull(summary.averagePriceWon)
        assertNull(summary.savingsWon)
        assertFalse(summary.isLowestPriceTied)
    }

    @Test
    fun `positive half won average rounds upward`() {
        val summary = requireNotNull(StationListDecisionSummary.from(listOf(item(1_600), item(1_601))))
        assertEquals(1_601, summary.averagePriceWon)
        assertEquals(1, summary.savingsWon)
    }

    @Test
    fun `equal minima are reported as a tie`() {
        val summary = requireNotNull(StationListDecisionSummary.from(listOf(item(1_600), item(1_600), item(1_700))))
        assertTrue(summary.isLowestPriceTied)
        assertEquals(1_633, summary.averagePriceWon)
        assertEquals(33, summary.savingsWon)
    }
}

private fun item(priceWon: Int) = StationListItemUiModel(
    id = "station-$priceWon",
    name = "테스트 주유소",
    brand = Brand.GSC,
    brandLabel = "GS칼텍스",
    priceWon = priceWon,
    priceLabel = "${priceWon}원",
    distanceLabel = "0.3km",
    priceNumberLabel = priceWon.toString(),
    priceUnitLabel = "원",
    distanceNumberLabel = "0.3",
    distanceUnitLabel = "km",
    priceDeltaLabel = "-",
    isWatched = false,
    latitude = 37.49,
    longitude = 127.02,
)
