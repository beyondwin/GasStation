package com.gasstation.domain.station.model

enum class SearchRadius(val meters: Int) {
    KM_3(meters = 3_000),
    KM_4(meters = 4_000),
    KM_5(meters = 5_000),
}
