package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.domain.station.StationRefreshFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class StationRefreshFailureCopyTest {
    @Test
    fun `timeout maps to the timeout copy`() {
        assertEquals(
            StringResource.fromId(R.string.station_list_refresh_timeout),
            StationRefreshFailureReason.Timeout.toStationListRefreshFailureCopy(),
        )
    }

    @Test
    fun `non-timeout failures map to the generic refresh copy`() {
        val genericFailures = listOf(
            StationRefreshFailureReason.Network,
            StationRefreshFailureReason.InvalidPayload,
            StationRefreshFailureReason.Http(statusCode = 503),
            StationRefreshFailureReason.Unknown,
        )

        genericFailures.forEach { failure ->
            assertEquals(
                "wrong copy for $failure",
                StringResource.fromId(R.string.station_list_refresh_failed),
                failure.toStationListRefreshFailureCopy(),
            )
        }
    }

    @Test
    fun `missing failure reason maps to the generic refresh copy`() {
        val failure: StationRefreshFailureReason? = null

        assertEquals(
            StringResource.fromId(R.string.station_list_refresh_failed),
            failure.toStationListRefreshFailureCopy(),
        )
    }
}
