package com.gasstation.feature.stationlist

import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class StationListItemUiModelTest {

    @Test
    fun `price history keeps unavailable distinct from unchanged`() {
        assertEquals(
            StationListPriceHistoryUiModel.Unavailable,
            StationListItemUiModel(
                entry = stationEntry(priceDelta = StationPriceDelta.Unavailable),
            ).priceHistory,
        )
        assertEquals(
            StationListPriceHistoryUiModel.Unchanged,
            StationListItemUiModel(
                entry = stationEntry(priceDelta = StationPriceDelta.Unchanged),
            ).priceHistory,
        )
    }

    @Test
    fun `price history keeps rise and fall amounts`() {
        val increasedItem = StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Increased(20)),
        )

        assertEquals(
            StationListPriceHistoryUiModel.Increased(20),
            increasedItem.priceHistory,
        )
        assertEquals(Brand.GSC, increasedItem.brand)
        assertEquals(1_689, increasedItem.priceWon)
        assertEquals(
            StationListPriceHistoryUiModel.Decreased(30),
            StationListItemUiModel(
                entry = stationEntry(priceDelta = StationPriceDelta.Decreased(30)),
            ).priceHistory,
        )
    }

    @Test
    fun `station list item uses canonical rtx brand label`() {
        val item = StationListItemUiModel(
            entry = stationEntry(brand = Brand.RTX),
        )

        assertEquals("고속도로알뜰", item.brandLabel)
    }

    @Test
    fun `price delta tone resolves stock colors`() {
        assertEquals(com.gasstation.core.designsystem.ColorSupportError, PriceDeltaTone.Rise.toColor())
        assertEquals(com.gasstation.core.designsystem.ColorSupportInfo, PriceDeltaTone.Fall.toColor())
        assertEquals(com.gasstation.core.designsystem.ColorGray2, PriceDeltaTone.Neutral.toColor())
    }

    @Test
    fun `price history states map to presentation tones`() {
        assertEquals(PriceDeltaTone.Neutral, StationListPriceHistoryUiModel.Unavailable.toTone())
        assertEquals(PriceDeltaTone.Neutral, StationListPriceHistoryUiModel.Unchanged.toTone())
        assertEquals(PriceDeltaTone.Rise, StationListPriceHistoryUiModel.Increased(20).toTone())
        assertEquals(PriceDeltaTone.Fall, StationListPriceHistoryUiModel.Decreased(30).toTone())
    }
}

private fun stationEntry(priceDelta: StationPriceDelta = StationPriceDelta.Unchanged, brand: Brand = Brand.GSC): StationListEntry =
    StationListEntry(
        station = Station(
            id = "station-1",
            name = "테스트 주유소",
            brand = brand,
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
