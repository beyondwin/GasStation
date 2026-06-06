package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.GAS_STATION_DISTANCE_UNIT
import com.gasstation.core.designsystem.GAS_STATION_WON_UNIT
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.designsystem.gasStationDistanceDigits
import com.gasstation.core.designsystem.gasStationDistanceLabel
import com.gasstation.core.designsystem.gasStationPriceDigits
import com.gasstation.core.designsystem.gasStationPriceLabel
import com.gasstation.core.model.Brand
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta

data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: PriceDeltaTone = PriceDeltaTone.Neutral,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(priceNumberLabel.isNotBlank()) { "priceNumberLabel must not be blank" }
        require(priceUnitLabel.isNotBlank()) { "priceUnitLabel must not be blank" }
        require(distanceNumberLabel.isNotBlank()) { "distanceNumberLabel must not be blank" }
        require(distanceUnitLabel.isNotBlank()) { "distanceUnitLabel must not be blank" }
    }

    constructor(entry: StationListEntry) : this(
        id = entry.station.id,
        name = entry.station.name,
        brand = entry.station.brand,
        brandLabel = entry.station.brand.gasStationBrandLabel(),
        priceLabel = entry.station.price.gasStationPriceLabel(),
        distanceLabel = entry.station.distance.gasStationDistanceLabel(),
        priceNumberLabel = entry.station.price.gasStationPriceDigits(),
        priceUnitLabel = GAS_STATION_WON_UNIT,
        distanceNumberLabel = entry.station.distance.gasStationDistanceDigits(),
        distanceUnitLabel = GAS_STATION_DISTANCE_UNIT,
        priceDeltaLabel = entry.priceDelta.toDeltaLabel(),
        priceDeltaTone = entry.priceDelta.direction.toTone(),
        isWatched = entry.isWatched,
        latitude = entry.station.coordinates.latitude,
        longitude = entry.station.coordinates.longitude,
    )
}

enum class PriceDeltaTone {
    Rise,
    Fall,
    Neutral,
}

private fun StationPriceDelta.toDeltaLabel(): String =
    amountWonOrNull?.let { "$it$GAS_STATION_WON_UNIT" } ?: "-"

private fun StationPriceDelta.PriceDirection.toTone(): PriceDeltaTone = when (this) {
    StationPriceDelta.PriceDirection.RISE -> PriceDeltaTone.Rise
    StationPriceDelta.PriceDirection.FALL -> PriceDeltaTone.Fall
    StationPriceDelta.PriceDirection.NEUTRAL -> PriceDeltaTone.Neutral
}
