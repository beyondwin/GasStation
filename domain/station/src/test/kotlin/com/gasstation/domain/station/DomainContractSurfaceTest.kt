package com.gasstation.domain.station

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
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
            Coordinates::class.java,
        )
        assertEquals(Flow::class.java, observeWatchlist.returnType)
        assertTrue(observeWatchlist.genericReturnType.typeName.contains(WatchedStationSummary::class.java.name))

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
