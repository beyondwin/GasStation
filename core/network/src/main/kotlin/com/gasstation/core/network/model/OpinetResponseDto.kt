package com.gasstation.core.network.model

import com.google.gson.annotations.SerializedName

data class OpinetResponseDto(
    @SerializedName("RESULT")
    val result: ResultDto? = null,
) {
    data class ResultDto(
        @SerializedName("OIL")
        val stations: List<OpinetStationDto>? = null,
    )
}
