package com.gasstation.domain.station

import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainContractSurfaceTest {

    @Test
    fun `domain module no longer depends on core common helper types`() {
        assertTrue(
            runCatching {
                Class.forName("com.gasstation.core.common.dispatchers.DispatcherProvider")
            }.isFailure,
        )
        assertTrue(
            runCatching {
                Class.forName("com.gasstation.core.common.result.AppResult")
            }.isFailure,
        )
    }

    @Test
    fun `station contracts expose watchlist and event read models`() {
        val stationSearchResultField = StationSearchResult::class.java.getDeclaredField("stations")
        assertEquals(List::class.java, stationSearchResultField.type)
        assertTrue(stationSearchResultField.genericType.typeName.contains(StationListEntry::class.java.name))
        assertTrue(
            StationSearchResult::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
            },
            "StationSearchResult creation must set cache snapshot presence explicitly.",
        )

        assertEquals(
            setOf("Unavailable", "Unchanged", "Increased", "Decreased"),
            StationPriceDelta::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
        )
        assertEquals(
            setOf(
                "SearchRefreshed",
                "WatchToggled",
                "CompareViewed",
                "ExternalMapOpened",
                "RefreshFailed",
                "LocationFailed",
                "RetryAttempted",
            ),
            StationEvent::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
        )
        val events =
            listOf(
                StationEvent.SearchRefreshed(SearchRadius.KM_3, FuelType.GASOLINE, SortOrder.PRICE, stale = false),
                StationEvent.WatchToggled("station-1", watched = true),
                StationEvent.CompareViewed(count = 2),
                StationEvent.ExternalMapOpened("station-1", MapProvider.KAKAO_MAP),
                StationEvent.RefreshFailed(StationRefreshFailureReason.Timeout),
                StationEvent.LocationFailed(resultType = "permission-denied"),
                StationEvent.RetryAttempted(StationRefreshFailureReason.Network, succeeded = true),
            )
        assertEquals(
            StationEvent::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
            events.map { it::class.java.simpleName }.toSet(),
        )

        val observeWatchlist = StationRepository::class.java.getDeclaredMethod(
            "observeWatchlist",
            WatchlistQuery::class.java,
        )
        assertEquals(Flow::class.java, observeWatchlist.returnType)
        assertTrue(observeWatchlist.genericReturnType.typeName.contains(WatchedStationSummary::class.java.name))

        assertEquals(
            setOf("origin", "fuelType"),
            WatchlistQuery::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
        assertEquals(Coordinates::class.java, WatchlistQuery::class.java.getDeclaredField("origin").type)
        assertEquals(FuelType::class.java, WatchlistQuery::class.java.getDeclaredField("fuelType").type)
        assertEquals(
            MoneyWon::class.java,
            WatchedStationSummary::class.java.getDeclaredField("price").type,
        )
        val unavailablePriceSummary = WatchedStationSummary(
            id = "station-1",
            name = "Saved Station",
            brand = Brand.GSC,
            price = null,
            distance = DistanceMeters(0),
            coordinates = Coordinates(37.498095, 127.027610),
            priceDelta = StationPriceDelta.Unavailable,
            lastSeenAt = null,
        )
        assertEquals(null, unavailablePriceSummary.price)

        assertTrue(
            StationRepository::class.java.declaredMethods.any { method ->
                method.name == "removeWatchedStation" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java
            },
        )

        assertTrue(
            StationRepository::class.java.declaredMethods.any { method ->
                method.name == "updateWatchState" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Station::class.java &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType
            },
        )
    }

    @Test
    fun `station refresh failures expose HTTP status without transport types`() {
        assertEquals(
            setOf("Timeout", "Network", "InvalidPayload", "Http", "Unknown"),
            StationRefreshFailureReason::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
        )
        assertEquals(429, StationRefreshFailureReason.Http(429).statusCode)
        val cause = IllegalStateException("transport unavailable")
        val exception = StationRefreshException(StationRefreshFailureReason.Network, cause)
        assertEquals(StationRefreshFailureReason.Network, exception.reason)
        assertEquals(cause, exception.cause)
        assertTrue(
            StationRefreshFailureReason::class.java.permittedSubclasses.none { subclass ->
                subclass.declaredFields.any { field -> field.type.name.startsWith("retrofit2.") }
            },
        )
    }

    @Test
    fun `station list entry retains the comparison snapshot`() {
        val station =
            Station(
                id = "station-1",
                name = "서울 주유소",
                brand = Brand.GSC,
                price = MoneyWon(1_675),
                distance = DistanceMeters(850),
                coordinates = Coordinates(37.498095, 127.027610),
            )
        val lastSeenAt = Instant.parse("2026-08-20T00:00:00Z")

        val entry =
            StationListEntry(
                station = station,
                priceDelta = StationPriceDelta.Decreased(25),
                isWatched = false,
                lastSeenAt = lastSeenAt,
            )

        assertEquals(station, entry.station)
        assertEquals(StationPriceDelta.Decreased(25), entry.priceDelta)
        assertFalse(entry.isWatched)
        assertEquals(lastSeenAt, entry.lastSeenAt)
    }
}
