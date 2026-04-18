package com.gasstation.feature.stationlist

import com.gasstation.core.location.LocationPermissionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class StationListBannerModel(
    val title: String,
    val detail: String? = null,
    val tone: StationListBannerTone = StationListBannerTone.Neutral,
) {
    companion object {
        fun from(uiState: StationListUiState): List<StationListBannerModel> = buildList {
            if (uiState.permissionState == LocationPermissionState.ApproximateGranted) {
                add(
                    StationListBannerModel(
                        title = "대략적인 위치 기준으로 주변 주유소를 찾고 있습니다.",
                        tone = StationListBannerTone.Info,
                    ),
                )
            }
            if (uiState.isStale) {
                add(
                    StationListBannerModel(
                        title = "오래된 결과를 표시 중입니다.",
                        detail = uiState.lastUpdatedAt?.toLastUpdatedLabel(),
                        tone = StationListBannerTone.Warning,
                    ),
                )
            }
        }
    }
}

enum class StationListBannerTone {
    Neutral,
    Info,
    Warning,
    Error,
}

private val BannerTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault())

private fun Instant.toLastUpdatedLabel(): String = "마지막 갱신 ${BannerTimestampFormatter.format(this)}"
