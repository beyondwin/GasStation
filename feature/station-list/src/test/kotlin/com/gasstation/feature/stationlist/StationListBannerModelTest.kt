package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

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

            assertEquals(
                listOf<StationListBannerModel>(
                    StationListBannerModel(
                        title = "대략적인 위치 기준입니다.",
                        detail = "정확한 거리 비교가 필요하면 위치 권한을 정확도로 바꿔주세요.",
                        tone = StationListBannerTone.Info,
                    ),
                    StationListBannerModel(
                        title = "저장된 결과를 표시 중입니다.",
                        detail = "마지막 갱신 04.18 00:30",
                        tone = StationListBannerTone.Warning,
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
