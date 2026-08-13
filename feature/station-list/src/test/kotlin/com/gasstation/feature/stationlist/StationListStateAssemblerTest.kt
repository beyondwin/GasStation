package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StationListStateAssemblerTest {
    private val coordinates = Coordinates(37.498095, 127.027610)
    private val fetchedAt = Instant.parse("2026-08-13T00:00:00Z")
    private val preferences = UserPreferences.default()

    @Test
    fun `ready inputs map every location preference refresh search and failure field`() {
        val stations = listOf(StationListItemUiModel(stationEntry()))
        val commands = listOf(command(id = 7L))
        val inputs = stateInputs(
            preference = PreferenceLoadState.Ready(preferences),
            preferenceMutation = StationListPreferenceMutationState(pendingPreferenceWrite = true),
            location = LocationState(
                permissionState = LocationPermissionState.ApproximateGranted,
                needsRecoveryRefresh = true,
                isGpsEnabled = true,
                isAvailabilityKnown = true,
                currentCoordinates = coordinates,
                currentAddressLabel = "서울 강남구 역삼동",
            ),
            refresh = RefreshCoordinatorState(isLoading = true, isRefreshing = true),
            search = StationListSearchProjection(
                sourceStations = listOf(stationEntry()),
                stations = stations,
                freshness = StationFreshness.Stale,
                fetchedAt = fetchedAt,
                hasCachedSnapshot = true,
            ),
            blockingFailure = StationListFailureReason.RefreshTimedOut,
            pendingCommands = commands,
        )

        val actual = StationListStateAssembler.assemble(inputs)

        assertEquals(coordinates, actual.currentCoordinates)
        assertEquals("서울 강남구 역삼동", actual.currentAddressLabel)
        assertEquals(LocationPermissionState.ApproximateGranted, actual.permissionState)
        assertTrue(actual.needsRecoveryRefresh)
        assertTrue(actual.isGpsEnabled)
        assertTrue(actual.isAvailabilityKnown)
        assertTrue(actual.isLoading)
        assertTrue(actual.isRefreshing)
        assertTrue(actual.isStale)
        assertEquals(StationListFailureReason.RefreshTimedOut, actual.blockingFailure)
        assertSame(stations, actual.stations)
        assertSame(preferences, actual.preferences)
        assertFalse(actual.preferenceLoadFailed)
        assertTrue(actual.pendingPreferenceWrite)
        assertEquals(fetchedAt, actual.lastUpdatedAt)
        assertTrue(actual.hasCachedSnapshot)
        assertSame(commands, actual.pendingCommands)
    }

    @Test
    fun `loading preference contributes loading without default preferences`() {
        val actual = StationListStateAssembler.assemble(
            stateInputs(
                preference = PreferenceLoadState.Loading,
                refresh = RefreshCoordinatorState(isLoading = false, isRefreshing = false),
            ),
        )

        assertTrue(actual.isLoading)
        assertNull(actual.preferences)
        assertFalse(actual.preferenceLoadFailed)
    }

    @Test
    fun `failed preference exposes failure flag without default preferences`() {
        val actual = StationListStateAssembler.assemble(
            stateInputs(preference = PreferenceLoadState.Failed),
        )

        assertTrue(actual.preferenceLoadFailed)
        assertNull(actual.preferences)
        assertFalse(actual.isLoading)
    }

    @Test
    fun `no-cache stale sentinel is not exposed as stale content`() {
        val actual = StationListStateAssembler.assemble(
            stateInputs(
                search = StationListSearchProjection(
                    freshness = StationFreshness.Stale,
                    hasCachedSnapshot = false,
                ),
            ),
        )

        assertFalse(actual.isStale)
    }

    @Test
    fun `cached stale snapshot exposes stale and preserves fetchedAt`() {
        val actual = StationListStateAssembler.assemble(
            stateInputs(
                search = StationListSearchProjection(
                    freshness = StationFreshness.Stale,
                    fetchedAt = fetchedAt,
                    hasCachedSnapshot = true,
                ),
            ),
        )

        assertTrue(actual.isStale)
        assertEquals(fetchedAt, actual.lastUpdatedAt)
    }

    @Test
    fun `pending commands preserve exact list instance order ids and payloads`() {
        val first = command(id = 10L)
        val second = command(id = 11L)
        val commands = listOf(first, second)

        val actual = StationListStateAssembler.assemble(
            stateInputs(pendingCommands = commands),
        )

        assertSame(commands, actual.pendingCommands)
        assertEquals(listOf(10L, 11L), actual.pendingCommands.map { it.id })
        assertSame(first.payload, actual.pendingCommands[0].payload)
        assertSame(second.payload, actual.pendingCommands[1].payload)
    }

    @Test
    fun `assembler preserves exact mapped station list instance`() {
        val stations = listOf(StationListItemUiModel(stationEntry()))

        val actual = StationListStateAssembler.assemble(
            stateInputs(search = StationListSearchProjection(stations = stations)),
        )

        assertSame(stations, actual.stations)
    }

    @Test
    fun `assembler is referentially transparent for equal immutable inputs`() {
        val stations = listOf(StationListItemUiModel(stationEntry()))
        val commands = listOf(command(id = 20L))
        val inputs = stateInputs(
            search = StationListSearchProjection(stations = stations),
            pendingCommands = commands,
        )

        val first = StationListStateAssembler.assemble(inputs)
        val second = StationListStateAssembler.assemble(inputs)

        assertEquals(first, second)
        assertSame(stations, first.stations)
        assertSame(stations, second.stations)
        assertSame(commands, first.pendingCommands)
        assertSame(commands, second.pendingCommands)
    }

    @Test
    fun `equal source entries reuse previous UI list across freshness metadata change`() {
        val source = listOf(stationEntry())
        val uiStations = listOf(StationListItemUiModel(source.single()))
        val previous = StationListSearchProjection(
            sourceStations = source,
            stations = uiStations,
            freshness = StationFreshness.Fresh,
            fetchedAt = fetchedAt,
            hasCachedSnapshot = true,
        )

        val actual = projectStationSearchResult(
            previous = previous,
            result = StationSearchResult(
                stations = source.toList(),
                freshness = StationFreshness.Stale,
                fetchedAt = fetchedAt.plusSeconds(60),
                hasCachedSnapshot = true,
            ),
        )

        assertSame(uiStations, actual.stations)
        assertEquals(StationFreshness.Stale, actual.freshness)
        assertEquals(fetchedAt.plusSeconds(60), actual.fetchedAt)
    }

    @Test
    fun `changed source entries create a new mapped UI list`() {
        val previousSource = listOf(stationEntry(id = "old"))
        val previousItems = listOf(StationListItemUiModel(previousSource.single()))

        val actual = projectStationSearchResult(
            previous = StationListSearchProjection(
                sourceStations = previousSource,
                stations = previousItems,
            ),
            result = StationSearchResult(
                stations = listOf(stationEntry(id = "new")),
                freshness = StationFreshness.Fresh,
                fetchedAt = fetchedAt,
                hasCachedSnapshot = true,
            ),
        )

        assertNotSame(previousItems, actual.stations)
        assertEquals(listOf("new"), actual.stations.map { it.id })
    }

    @Test
    fun `empty successful snapshot propagates hasCachedSnapshot true`() {
        val actual = projectStationSearchResult(
            previous = StationListSearchProjection(),
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Fresh,
                fetchedAt = fetchedAt,
                hasCachedSnapshot = true,
            ),
        )

        assertTrue(actual.hasCachedSnapshot)
        assertTrue(actual.stations.isEmpty())
    }

    @Test
    fun `projection does not infer cache snapshot from fetchedAt`() {
        val actual = projectStationSearchResult(
            previous = StationListSearchProjection(),
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = fetchedAt,
                hasCachedSnapshot = false,
            ),
        )

        assertFalse(actual.hasCachedSnapshot)
    }

    @Test
    fun `denied permission outranks gps preference failure and cached rows`() {
        assertEquals(
            StationListBodyState.PermissionRequired,
            bodyState(
                permissionState = LocationPermissionState.Denied,
                isGpsEnabled = false,
                preferenceLoadFailed = true,
                hasCachedSnapshot = true,
                stations = listOf(StationListItemUiModel(stationEntry())),
            ),
        )
    }

    @Test
    fun `disabled gps outranks preference failure and cached rows`() {
        assertEquals(
            StationListBodyState.GpsRequired,
            bodyState(
                isGpsEnabled = false,
                preferenceLoadFailed = true,
                hasCachedSnapshot = true,
                stations = listOf(StationListItemUiModel(stationEntry())),
            ),
        )
    }

    @Test
    fun `preference failure outranks cached rows`() {
        assertEquals(
            StationListBodyState.Failure(StationListFailureReason.PreferencesFailed),
            bodyState(
                preferenceLoadFailed = true,
                hasCachedSnapshot = true,
                stations = listOf(StationListItemUiModel(stationEntry())),
            ),
        )
    }

    @Test
    fun `preference loading outranks cached rows`() {
        assertEquals(
            StationListBodyState.InitialLoading,
            bodyState(
                preferences = null,
                hasCachedSnapshot = true,
                stations = listOf(StationListItemUiModel(stationEntry())),
            ),
        )
    }

    @Test
    fun `no-cache blocking failure outranks active refresh`() {
        assertEquals(
            StationListBodyState.Failure(StationListFailureReason.RefreshFailed),
            bodyState(
                isRefreshing = true,
                blockingFailure = StationListFailureReason.RefreshFailed,
            ),
        )
    }

    @Test
    fun `no-cache refresh without failure stays initial loading`() {
        assertEquals(
            StationListBodyState.InitialLoading,
            bodyState(isRefreshing = true),
        )
    }

    @Test
    fun `no-cache idle without failure stays initial loading`() {
        assertEquals(
            StationListBodyState.InitialLoading,
            bodyState(),
        )
    }

    @Test
    fun `cached non-empty snapshot remains results during refresh`() {
        assertEquals(
            StationListBodyState.Results,
            bodyState(
                isRefreshing = true,
                hasCachedSnapshot = true,
                stations = listOf(StationListItemUiModel(stationEntry())),
            ),
        )
    }

    @Test
    fun `cached non-empty snapshot remains results during blocking failure`() {
        assertEquals(
            StationListBodyState.Results,
            bodyState(
                blockingFailure = StationListFailureReason.RefreshFailed,
                hasCachedSnapshot = true,
                stations = listOf(StationListItemUiModel(stationEntry())),
            ),
        )
    }

    @Test
    fun `cached empty successful snapshot is results while idle`() {
        assertEquals(
            StationListBodyState.Results,
            bodyState(hasCachedSnapshot = true),
        )
    }

    @Test
    fun `cached empty successful snapshot is results during refresh`() {
        assertEquals(
            StationListBodyState.Results,
            bodyState(isRefreshing = true, hasCachedSnapshot = true),
        )
    }

    @Test
    fun `visible rows defensively remain results when cache marker is false`() {
        assertEquals(
            StationListBodyState.Results,
            bodyState(stations = listOf(StationListItemUiModel(stationEntry()))),
        )
    }

    @Test
    fun `cached empty results are first usable content during refresh`() {
        assertTrue(
            bodyUiState(
                isRefreshing = true,
                hasCachedSnapshot = true,
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `no-cache idle and loading are not first usable content`() {
        assertFalse(bodyUiState().hasFirstUsableContent())
        assertFalse(bodyUiState(isLoading = true).hasFirstUsableContent())
    }

    @Test
    fun `blocking failure is first usable content`() {
        assertTrue(
            bodyUiState(
                blockingFailure = StationListFailureReason.RefreshFailed,
            ).hasFirstUsableContent(),
        )
    }

    private fun stateInputs(
        preference: PreferenceLoadState = PreferenceLoadState.Ready(preferences),
        preferenceMutation: StationListPreferenceMutationState = StationListPreferenceMutationState(),
        location: LocationState = LocationState(
            permissionState = LocationPermissionState.PreciseGranted,
            isGpsEnabled = true,
            isAvailabilityKnown = true,
            currentCoordinates = coordinates,
        ),
        refresh: RefreshCoordinatorState = RefreshCoordinatorState(),
        search: StationListSearchProjection = StationListSearchProjection(),
        blockingFailure: StationListFailureReason? = null,
        pendingCommands: List<StationListUiCommand> = emptyList(),
    ) = StationListStateInputs(
        preference = preference,
        preferenceMutation = preferenceMutation,
        location = location,
        refresh = refresh,
        search = search,
        blockingFailure = blockingFailure,
        pendingCommands = pendingCommands,
    )

    private fun bodyState(
        permissionState: LocationPermissionState = LocationPermissionState.PreciseGranted,
        isGpsEnabled: Boolean = true,
        preferences: UserPreferences? = this.preferences,
        preferenceLoadFailed: Boolean = false,
        isLoading: Boolean = false,
        isRefreshing: Boolean = false,
        blockingFailure: StationListFailureReason? = null,
        hasCachedSnapshot: Boolean = false,
        stations: List<StationListItemUiModel> = emptyList(),
    ): StationListBodyState = bodyUiState(
        permissionState = permissionState,
        isGpsEnabled = isGpsEnabled,
        preferences = preferences,
        preferenceLoadFailed = preferenceLoadFailed,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        blockingFailure = blockingFailure,
        hasCachedSnapshot = hasCachedSnapshot,
        stations = stations,
    ).toBodyState()

    private fun bodyUiState(
        permissionState: LocationPermissionState = LocationPermissionState.PreciseGranted,
        isGpsEnabled: Boolean = true,
        preferences: UserPreferences? = this.preferences,
        preferenceLoadFailed: Boolean = false,
        isLoading: Boolean = false,
        isRefreshing: Boolean = false,
        blockingFailure: StationListFailureReason? = null,
        hasCachedSnapshot: Boolean = false,
        stations: List<StationListItemUiModel> = emptyList(),
    ) = StationListUiState(
        permissionState = permissionState,
        isGpsEnabled = isGpsEnabled,
        preferences = preferences,
        preferenceLoadFailed = preferenceLoadFailed,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        blockingFailure = blockingFailure,
        hasCachedSnapshot = hasCachedSnapshot,
        stations = stations,
    )

    private fun command(id: Long) = StationListUiCommand(
        id = id,
        payload = StationListCommandPayload.ShowSnackbar(
            StringResource.fromId(R.string.station_list_refresh_failed),
        ),
    )

    private fun stationEntry(id: String = "station-1") = StationListEntry(
        station = Station(
            id = id,
            name = "강남 주유소",
            brand = Brand.GSC,
            price = MoneyWon(1_689),
            distance = DistanceMeters(800),
            coordinates = coordinates,
        ),
        priceDelta = StationPriceDelta.Unavailable,
        isWatched = false,
        lastSeenAt = fetchedAt,
    )
}
