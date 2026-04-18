package com.gasstation.feature.stationlist

import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta

data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val priceDeltaLabel: String,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
) {
    constructor(entry: StationListEntry) : this(
        id = entry.station.id,
        name = entry.station.name,
        brandLabel = entry.station.brand.toLabel(),
        priceLabel = "${entry.station.price.value}원",
        distanceLabel = "${entry.station.distance.value}m",
        priceDeltaLabel = entry.priceDelta.toLabel(),
        isWatched = entry.isWatched,
        latitude = entry.station.coordinates.latitude,
        longitude = entry.station.coordinates.longitude,
    )
}

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
