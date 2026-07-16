package com.gasstation.feature.watchlist

import com.gasstation.core.model.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WatchlistSummaryUiModelTest {
    @Test
    fun `watchlist summary rounds average and keeps latest check`() {
        val first = item(priceWon = 1_600, lastSeenAt = Instant.parse("2026-07-17T01:00:00Z"))
        val second = item(priceWon = 1_601, lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"))

        val summary = WatchlistSummaryUiModel.from(listOf(first, second))

        assertEquals(2, summary.count)
        assertEquals(1_601, summary.averagePriceWon)
        assertEquals(Instant.parse("2026-07-17T02:00:00Z"), summary.latestSeenAt)
    }

    @Test
    fun `watchlist summary uses long accumulation for large typed prices`() {
        val first = item(priceWon = Int.MAX_VALUE, lastSeenAt = null)
        val second = item(priceWon = Int.MAX_VALUE, lastSeenAt = null)

        val summary = WatchlistSummaryUiModel.from(listOf(first, second))

        assertEquals(Int.MAX_VALUE, summary.averagePriceWon)
    }

    @Test
    fun `empty watchlist summary has no average or latest check`() {
        val summary = WatchlistSummaryUiModel.from(emptyList())

        assertEquals(0, summary.count)
        assertNull(summary.averagePriceWon)
        assertNull(summary.latestSeenAt)
    }

    @Test
    fun `single item summary keeps its typed price and absent check`() {
        val summary = WatchlistSummaryUiModel.from(
            listOf(item(priceWon = 1_689, lastSeenAt = null)),
        )

        assertEquals(1, summary.count)
        assertEquals(1_689, summary.averagePriceWon)
        assertNull(summary.latestSeenAt)
    }

    @Test
    fun `last seen label is a final timezone aware projection`() {
        assertEquals(
            "7월 17일 11:00",
            Instant.parse("2026-07-17T02:00:00Z")
                .toWatchlistLastSeenLabel(ZoneId.of("Asia/Seoul")),
        )
        assertEquals("-", null.toWatchlistLastSeenLabel(ZoneId.of("Asia/Seoul")))
    }

    private fun item(priceWon: Int, lastSeenAt: Instant?) = WatchlistItemUiModel(
        id = "station-$priceWon",
        name = "테스트 주유소",
        brand = Brand.GSC,
        brandLabel = "GS칼텍스",
        priceWon = priceWon,
        priceLabel = "${priceWon}원",
        priceNumberLabel = priceWon.toString(),
        priceUnitLabel = "원",
        distanceLabel = "0.3km",
        distanceNumberLabel = "0.3",
        distanceUnitLabel = "km",
        priceDeltaLabel = "-",
        lastSeenAt = lastSeenAt,
        lastSeenLabel = lastSeenAt.toWatchlistLastSeenLabel(ZoneId.of("Asia/Seoul")),
        latitude = 37.49,
        longitude = 127.02,
    )
}
