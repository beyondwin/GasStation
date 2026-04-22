package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState
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
                        title = "대략적인 위치 기준입니다.",
                        detail = "정확한 거리 비교가 필요하면 위치 권한을 정확도로 바꿔주세요.",
                        tone = StationListBannerTone.Info,
                    ),
                )
            }
            if (uiState.isStale) {
                add(
                    StationListBannerModel(
                        title = "저장된 결과를 표시 중입니다.",
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
