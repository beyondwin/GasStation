package com.gasstation.feature.stationlist

import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.Station

data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val latitude: Double,
    val longitude: Double,
) {
    constructor(station: Station) : this(
        id = station.id,
        name = station.name,
        brandLabel = station.brand.toLabel(),
        priceLabel = "${station.price.value}원",
        distanceLabel = "${station.distance.value}m",
        latitude = station.coordinates.latitude,
        longitude = station.coordinates.longitude,
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
