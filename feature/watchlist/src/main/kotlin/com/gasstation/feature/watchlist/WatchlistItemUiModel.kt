package com.gasstation.feature.watchlist

import androidx.compose.ui.graphics.Color
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.model.Brand
import com.gasstation.core.model.DistanceMeters
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
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
        brand = summary.station.brand,
        brandLabel = summary.station.brand.gasStationBrandLabel(),
        priceLabel = summary.station.price.value.toPriceLabel(),
        priceNumberLabel = summary.station.price.value.toGroupedDigits(),
        priceUnitLabel = "\uC6D0",
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

private fun Int.toPriceLabel(): String = "${toGroupedDigits()}\uC6D0"

private fun Int.toGroupedDigits(): String = DecimalFormat("#,###").format(this)

private fun DistanceMeters.toDistanceLabel(): String = "${toDistanceNumberLabel()}km"

private fun DistanceMeters.toDistanceNumberLabel(): String = DecimalFormat("#,##0.0").format(value / 1000.0)

private fun StationPriceDelta.toLabel(): String = when (this) {
    StationPriceDelta.Unavailable -> "-"
    StationPriceDelta.Unchanged -> "-"
    is StationPriceDelta.Increased -> "${amountWon}\uC6D0"
    is StationPriceDelta.Decreased -> "${amountWon}\uC6D0"
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
    if (this == null) return "\uB9C8\uC9C0\uB9C9 \uD655\uC778 \uAE30\uB85D \uC5C6\uC74C"

    return DateTimeFormatter.ofPattern("M\uC6D4 d\uC77C HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)
}
