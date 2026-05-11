package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class StationListBannerModel(
    val titleResId: Int,
    val detailResId: Int? = null,
    val detailArg: String? = null,
    val tone: StationListBannerTone = StationListBannerTone.Neutral,
) {
    companion object {
        fun from(uiState: StationListUiState): List<StationListBannerModel> = buildList {
            if (uiState.permissionState == LocationPermissionState.ApproximateGranted) {
                add(
                    StationListBannerModel(
                        titleResId = R.string.station_list_banner_approx_location_title,
                        detailResId = R.string.station_list_banner_approx_location_detail,
                        tone = StationListBannerTone.Info,
                    ),
                )
            }
            if (uiState.isStale) {
                add(
                    StationListBannerModel(
                        titleResId = R.string.station_list_banner_stale_title,
                        detailResId = uiState.lastUpdatedAt?.let { R.string.station_list_banner_stale_detail },
                        detailArg = uiState.lastUpdatedAt?.toFormattedTimestamp(),
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

private fun Instant.toFormattedTimestamp(): String = BannerTimestampFormatter.format(this)
