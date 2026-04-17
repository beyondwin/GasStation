package com.gasstation.core.network.service

import com.gasstation.core.network.model.KakaoTransCoordDto
import retrofit2.http.GET
import retrofit2.http.Query

interface KakaoService {
    @GET("v2/local/geo/transcoord.json")
    suspend fun transCoord(
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("input_coord") inputCoord: String,
        @Query("output_coord") outputCoord: String,
    ): KakaoTransCoordDto
}
