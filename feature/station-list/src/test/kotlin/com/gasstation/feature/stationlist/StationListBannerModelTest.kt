package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class StationListBannerModelTest {

    @Test
    fun `from orders approximate and stale banners with formatted timestamp`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val uiState = StationListUiState(
                permissionState = LocationPermissionState.ApproximateGranted,
                isRefreshing = true,
                isStale = true,
                lastUpdatedAt = Instant.parse("2026-04-18T00:30:00Z"),
            )

            val banners = StationListBannerModel.from(uiState)
            assertEquals(2, banners.size)

            assertEquals(R.string.station_list_banner_approx_location_title, banners[0].titleResId)
            assertEquals(R.string.station_list_banner_approx_location_detail, banners[0].detailResId)
            assertEquals(null, banners[0].detailArg)
            assertEquals(StationListBannerTone.Info, banners[0].tone)

            assertEquals(R.string.station_list_banner_stale_title, banners[1].titleResId)
            assertEquals(R.string.station_list_banner_stale_detail, banners[1].detailResId)
            assertEquals("04.18 00:30", banners[1].detailArg)
            assertEquals(StationListBannerTone.Warning, banners[1].tone)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `from returns no banners for precise fresh state`() {
        val uiState = StationListUiState(
            permissionState = LocationPermissionState.PreciseGranted,
            isRefreshing = false,
            isStale = false,
        )

        assertEquals(emptyList<StationListBannerModel>(), StationListBannerModel.from(uiState))
    }
}
