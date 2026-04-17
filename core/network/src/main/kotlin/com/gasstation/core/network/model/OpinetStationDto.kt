package com.gasstation.core.network.model

import com.google.gson.annotations.SerializedName

data class OpinetStationDto(
    @SerializedName("UNI_ID")
    val stationId: String? = null,
    @SerializedName("OS_NM")
    val name: String? = null,
    @SerializedName("POLL_DIV_CD")
    val brandCode: String? = null,
    @SerializedName("PRICE")
    val priceWon: String? = null,
    @SerializedName("DISTANCE")
    val distanceMeters: String? = null,
    @SerializedName("GIS_X_COOR")
    val gisX: String? = null,
    @SerializedName("GIS_Y_COOR")
    val gisY: String? = null,
)
