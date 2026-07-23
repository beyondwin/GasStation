package com.gasstation.domain.station

import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.flow.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
