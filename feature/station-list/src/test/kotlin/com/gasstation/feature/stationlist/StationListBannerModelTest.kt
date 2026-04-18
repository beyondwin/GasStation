package com.gasstation.feature.stationlist

import com.gasstation.core.location.LocationPermissionState
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StationListBannerModelTest {

    @Test
    fun `from orders approximate stale and refreshing banners with formatted timestamp`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val uiState = StationListUiState(
                permissionState = LocationPermissionState.ApproximateGranted,
                isRefreshing = true,
                isStale = true,
                lastUpdatedAt = Instant.parse("2026-04-18T00:30:00Z"),
            )

            assertEquals(
                listOf<StationListBannerModel>(
                    StationListBannerModel(
                        title = "대략적인 위치 기준으로 주변 주유소를 찾고 있습니다.",
                        tone = StationListBannerTone.Info,
                    ),
                    StationListBannerModel(
                        title = "오래된 결과를 표시 중입니다.",
                        detail = "마지막 갱신 04.18 00:30",
                        tone = StationListBannerTone.Warning,
                    ),
                    StationListBannerModel(
                        title = "새로고침 중입니다.",
                        tone = StationListBannerTone.Neutral,
                    ),
                ),
                StationListBannerModel.from(uiState),
            )
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
