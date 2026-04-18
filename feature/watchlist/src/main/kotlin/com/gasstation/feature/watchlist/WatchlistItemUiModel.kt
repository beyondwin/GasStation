package com.gasstation.feature.watchlist

import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val priceDeltaLabel: String,
    val lastSeenLabel: String,
    val latitude: Double,
    val longitude: Double,
) {
    constructor(summary: WatchedStationSummary) : this(
        id = summary.station.id,
        name = summary.station.name,
        brandLabel = summary.station.brand.toLabel(),
        priceLabel = "${summary.station.price.value}원",
        distanceLabel = "${summary.station.distance.value}m",
        priceDeltaLabel = summary.priceDelta.toLabel(),
        lastSeenLabel = summary.lastSeenAt.toLabel(),
        latitude = summary.station.coordinates.latitude,
        longitude = summary.station.coordinates.longitude,
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

private fun Instant?.toLabel(): String {
    if (this == null) return "마지막 확인 기록 없음"

    return DateTimeFormatter.ofPattern("M월 d일 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)
}
