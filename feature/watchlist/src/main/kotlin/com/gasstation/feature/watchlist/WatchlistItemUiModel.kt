package com.gasstation.feature.watchlist

import com.gasstation.core.designsystem.GAS_STATION_DISTANCE_UNIT
import com.gasstation.core.designsystem.GAS_STATION_WON_UNIT
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.designsystem.gasStationDistanceDigits
import com.gasstation.core.designsystem.gasStationDistanceLabel
import com.gasstation.core.designsystem.gasStationPriceDigits
import com.gasstation.core.designsystem.gasStationPriceLabel
import com.gasstation.core.model.Brand
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
    val priceWon: Int,
    val priceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaWon: Int?,
    val priceDeltaTone: WatchlistPriceDeltaTone = WatchlistPriceDeltaTone.Neutral,
    val lastSeenAt: Instant?,
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
        priceWon = summary.station.price.value,
        priceLabel = summary.station.price.gasStationPriceLabel(),
        priceNumberLabel = summary.station.price.gasStationPriceDigits(),
        priceUnitLabel = GAS_STATION_WON_UNIT,
        distanceLabel = summary.station.distance.gasStationDistanceLabel(),
        distanceNumberLabel = summary.station.distance.gasStationDistanceDigits(),
        distanceUnitLabel = GAS_STATION_DISTANCE_UNIT,
        priceDeltaWon = summary.priceDelta.amountWonOrNull,
        priceDeltaTone = summary.priceDelta.direction.toTone(),
        lastSeenAt = summary.lastSeenAt,
        lastSeenLabel = summary.lastSeenAt.toWatchlistLastSeenLabel(),
        latitude = summary.station.coordinates.latitude,
        longitude = summary.station.coordinates.longitude,
    )
}

enum class WatchlistPriceDeltaTone {
    Rise,
    Fall,
    Neutral,
}

internal fun StationPriceDelta.PriceDirection.toTone(): WatchlistPriceDeltaTone = when (this) {
    StationPriceDelta.PriceDirection.RISE -> WatchlistPriceDeltaTone.Rise
    StationPriceDelta.PriceDirection.FALL -> WatchlistPriceDeltaTone.Fall
    StationPriceDelta.PriceDirection.NEUTRAL -> WatchlistPriceDeltaTone.Neutral
}

internal fun Instant?.toWatchlistLastSeenLabel(zoneId: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    if (this == null) return "-"

    val pattern = if (locale.language == Locale.KOREAN.language) "M월 d일 HH:mm" else "MMM d, HH:mm"
    return DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zoneId)
        .format(this)
}
