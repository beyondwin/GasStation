package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class StationListItemUiModelTest {

    @Test
    fun `station list item maps increased delta to rise tone`() {
        val item = StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Increased(amountWon = 32)),
        )

        assertEquals("32원 상승", item.priceDeltaLabel)
        assertEquals(PriceDeltaTone.Rise, item.priceDeltaTone)
    }

    @Test
    fun `station list item maps decreased delta to fall tone`() {
        val item = StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Decreased(amountWon = 18)),
        )

        assertEquals("18원 하락", item.priceDeltaLabel)
        assertEquals(PriceDeltaTone.Fall, item.priceDeltaTone)
    }

    @Test
    fun `price delta tone resolves stock colors`() {
        assertEquals(com.gasstation.core.designsystem.ColorSupportError, PriceDeltaTone.Rise.toColor())
        assertEquals(com.gasstation.core.designsystem.ColorSupportInfo, PriceDeltaTone.Fall.toColor())
        assertEquals(com.gasstation.core.designsystem.ColorGray2, PriceDeltaTone.Neutral.toColor())
    }
}

private fun stationEntry(
    priceDelta: StationPriceDelta = StationPriceDelta.Unchanged,
): StationListEntry = StationListEntry(
    station = Station(
        id = "station-1",
        name = "테스트 주유소",
        brand = Brand.GSC,
        price = MoneyWon(1689),
        distance = DistanceMeters(320),
        coordinates = Coordinates(
            latitude = 37.498095,
            longitude = 127.02761,
        ),
    ),
    priceDelta = priceDelta,
    isWatched = false,
    lastSeenAt = null,
)
