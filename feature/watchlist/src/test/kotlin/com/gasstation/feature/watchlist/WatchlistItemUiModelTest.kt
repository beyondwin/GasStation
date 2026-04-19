package com.gasstation.feature.watchlist

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WatchlistItemUiModelTest {
    @Test
    fun `summary constructor exposes split legacy metric labels`() {
        val originalTimeZone = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))

            val item = WatchlistItemUiModel(
                WatchedStationSummary(
                    station = Station(
                        id = "station-1",
                        name = "Gangnam First",
                        brand = Brand.GSC,
                        price = MoneyWon(1689),
                        distance = DistanceMeters(300),
                        coordinates = Coordinates(37.498095, 127.02761),
                    ),
                    priceDelta = StationPriceDelta.Decreased(27),
                    lastSeenAt = Instant.parse("2026-04-18T03:00:00Z"),
                ),
            )

            assertEquals("1,689원", item.priceLabel)
            assertEquals("1,689", item.priceNumberLabel)
            assertEquals("원", item.priceUnitLabel)
            assertEquals(Brand.GSC, item.brand)
            assertEquals("0.3km", item.distanceLabel)
            assertEquals("0.3", item.distanceNumberLabel)
            assertEquals("km", item.distanceUnitLabel)
            assertEquals("27원", item.priceDeltaLabel)
            assertEquals(WatchlistPriceDeltaTone.Fall, item.priceDeltaTone)
            assertEquals("4월 18일 12:00", item.lastSeenLabel)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `summary constructor maps increased delta to rise tone`() {
        val item = WatchlistItemUiModel(
            WatchedStationSummary(
                station = Station(
                    id = "station-1",
                    name = "Gangnam First",
                    brand = Brand.GSC,
                    price = MoneyWon(1689),
                    distance = DistanceMeters(300),
                    coordinates = Coordinates(37.498095, 127.02761),
                ),
                priceDelta = StationPriceDelta.Increased(14),
                lastSeenAt = null,
            ),
        )

        assertEquals("14원", item.priceDeltaLabel)
        assertEquals(WatchlistPriceDeltaTone.Rise, item.priceDeltaTone)
    }

    @Test
    fun `summary constructor maps unchanged delta to neutral compact label`() {
        val item = WatchlistItemUiModel(
            WatchedStationSummary(
                station = Station(
                    id = "station-1",
                    name = "Gangnam First",
                    brand = Brand.GSC,
                    price = MoneyWon(1689),
                    distance = DistanceMeters(300),
                    coordinates = Coordinates(37.498095, 127.02761),
                ),
                priceDelta = StationPriceDelta.Unchanged,
                lastSeenAt = null,
            ),
        )

        assertEquals("-", item.priceDeltaLabel)
        assertEquals(WatchlistPriceDeltaTone.Neutral, item.priceDeltaTone)
    }

    @Test
    fun `watchlist price delta tone resolves stock colors`() {
        assertEquals(com.gasstation.core.designsystem.ColorSupportError, WatchlistPriceDeltaTone.Rise.toColor())
        assertEquals(com.gasstation.core.designsystem.ColorSupportInfo, WatchlistPriceDeltaTone.Fall.toColor())
        assertEquals(com.gasstation.core.designsystem.ColorGray2, WatchlistPriceDeltaTone.Neutral.toColor())
    }

    @Test
    fun `direct constructor accepts explicit split metric labels`() {
        val item = WatchlistItemUiModel(
            id = "station-1",
            name = "테스트 주유소",
            brand = Brand.GSC,
            brandLabel = "GS칼텍스",
            priceLabel = "1689원",
            priceNumberLabel = "1689",
            priceUnitLabel = "원",
            distanceLabel = "300m",
            distanceNumberLabel = "0.3",
            distanceUnitLabel = "km",
            priceDeltaLabel = "직전 가격과 동일",
            lastSeenLabel = "4월 18일 12:00",
            latitude = 37.498095,
            longitude = 127.02761,
        )

        assertEquals("1689", item.priceNumberLabel)
        assertEquals(Brand.GSC, item.brand)
        assertEquals("원", item.priceUnitLabel)
        assertEquals("0.3", item.distanceNumberLabel)
        assertEquals("km", item.distanceUnitLabel)
    }

    @Test
    fun `direct constructor rejects blank split metric labels`() {
        assertThrows(IllegalArgumentException::class.java) {
            WatchlistItemUiModel(
                id = "station-1",
                name = "테스트 주유소",
                brand = Brand.GSC,
                brandLabel = "GS칼텍스",
                priceLabel = "1689원",
                priceNumberLabel = "",
                priceUnitLabel = "원",
                distanceLabel = "300m",
                distanceNumberLabel = "0.3",
                distanceUnitLabel = "km",
                priceDeltaLabel = "직전 가격과 동일",
                lastSeenLabel = "4월 18일 12:00",
                latitude = 37.498095,
                longitude = 127.02761,
            )
        }
    }
}
