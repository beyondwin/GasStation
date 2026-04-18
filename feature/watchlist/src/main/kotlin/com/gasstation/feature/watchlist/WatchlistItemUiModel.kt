package com.gasstation.feature.watchlist

import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.model.DistanceMeters
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.Color

data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: WatchlistPriceDeltaTone = WatchlistPriceDeltaTone.Neutral,
    val lastSeenLabel: String,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(priceNumberLabel.isNotBlank()) { "priceNumberLabel must not be blank" }
        require(priceUnitLabel.isNotBlank()) { "priceUnitLabel must not be blank" }
        require(distanceNumberLabel.isNotBlank()) { "distanceNumberLabel must not be blank" }
        require(distanceUnitLabel.isNotBlank()) { "distanceUnitLabel must not be blank" }
    }

    constructor(summary: WatchedStationSummary) : this(
        id = summary.station.id,
        name = summary.station.name,
        brandLabel = summary.station.brand.toLabel(),
        priceLabel = summary.station.price.value.toPriceLabel(),
        priceNumberLabel = summary.station.price.value.toGroupedDigits(),
        priceUnitLabel = "원",
        distanceLabel = summary.station.distance.toDistanceLabel(),
        distanceNumberLabel = summary.station.distance.toDistanceNumberLabel(),
        distanceUnitLabel = "km",
        priceDeltaLabel = summary.priceDelta.toLabel(),
        priceDeltaTone = summary.priceDelta.toTone(),
        lastSeenLabel = summary.lastSeenAt.toLabel(),
        latitude = summary.station.coordinates.latitude,
        longitude = summary.station.coordinates.longitude,
    )
}

enum class WatchlistPriceDeltaTone {
    Rise,
    Fall,
    Neutral,
}

private fun Int.toPriceLabel(): String = "${toGroupedDigits()}원"

private fun Int.toGroupedDigits(): String = DecimalFormat("#,###").format(this)

private fun DistanceMeters.toDistanceLabel(): String = "${toDistanceNumberLabel()}km"

private fun DistanceMeters.toDistanceNumberLabel(): String =
    DecimalFormat("#,##0.0").format(value / 1000.0)

private fun Brand.toLabel(): String = when (this) {
    Brand.SKE -> "SK에너지"
    Brand.GSC -> "GS칼텍스"
    Brand.HDO -> "현대오일뱅크"
    Brand.SOL -> "S-OIL"
    Brand.RTO -> "알뜰주유소"
    Brand.RTX -> "자가상표"
    Brand.NHO -> "농협"
    Brand.ETC -> "기타"
    Brand.E1G -> "E1"
    Brand.SKG -> "SK가스"
}

private fun StationPriceDelta.toLabel(): String = when (this) {
    StationPriceDelta.Unavailable -> "가격 변동 정보 없음"
    StationPriceDelta.Unchanged -> "직전 가격과 동일"
    is StationPriceDelta.Increased -> "${amountWon}원 상승"
    is StationPriceDelta.Decreased -> "${amountWon}원 하락"
}

internal fun StationPriceDelta.toTone(): WatchlistPriceDeltaTone = when (this) {
    is StationPriceDelta.Increased -> WatchlistPriceDeltaTone.Rise
    is StationPriceDelta.Decreased -> WatchlistPriceDeltaTone.Fall
    StationPriceDelta.Unavailable,
    StationPriceDelta.Unchanged,
    -> WatchlistPriceDeltaTone.Neutral
}

internal fun WatchlistPriceDeltaTone.toColor(): Color = when (this) {
    WatchlistPriceDeltaTone.Rise -> ColorSupportError
    WatchlistPriceDeltaTone.Fall -> ColorSupportInfo
    WatchlistPriceDeltaTone.Neutral -> ColorGray2
}

private fun Instant?.toLabel(): String {
    if (this == null) return "마지막 확인 기록 없음"

    return DateTimeFormatter.ofPattern("M월 d일 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)
}
