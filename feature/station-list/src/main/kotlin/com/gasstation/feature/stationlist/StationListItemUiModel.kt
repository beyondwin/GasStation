package com.gasstation.feature.stationlist

import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.Brand
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import java.text.DecimalFormat

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
    constructor(entry: StationListEntry) : this(
        id = entry.station.id,
        name = entry.station.name,
        brand = entry.station.brand,
        brandLabel = entry.station.brand.toLabel(),
        priceLabel = entry.station.price.value.toPriceLabel(),
        distanceLabel = entry.station.distance.toDistanceLabel(),
        priceNumberLabel = entry.station.price.value.toGroupedDigits(),
        priceUnitLabel = "원",
        distanceNumberLabel = entry.station.distance.toDistanceNumberLabel(),
        distanceUnitLabel = "km",
        priceDeltaLabel = entry.priceDelta.toLabel(),
        priceDeltaTone = entry.priceDelta.toTone(),
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
    StationPriceDelta.Unavailable -> "-"
    StationPriceDelta.Unchanged -> "-"
    is StationPriceDelta.Increased -> "${amountWon}원"
    is StationPriceDelta.Decreased -> "${amountWon}원"
}

private fun StationPriceDelta.toTone(): PriceDeltaTone = when (this) {
    is StationPriceDelta.Increased -> PriceDeltaTone.Rise
    is StationPriceDelta.Decreased -> PriceDeltaTone.Fall
    StationPriceDelta.Unavailable,
    StationPriceDelta.Unchanged,
    -> PriceDeltaTone.Neutral
}
