package com.gasstation.core.model

public enum class SearchRadius(public val meters: Int) {
    KM_3(meters = 3_000),
    KM_4(meters = 4_000),
    KM_5(meters = 5_000),
}
