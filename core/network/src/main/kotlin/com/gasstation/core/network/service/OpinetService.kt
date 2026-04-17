package com.gasstation.core.network.service

import com.gasstation.core.network.model.OpinetResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpinetService {
    @GET("/api/aroundAll.do")
    suspend fun findStations(
        @Query("code") code: String,
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("radius") radius: Int,
        @Query("sort") sort: String,
        @Query("prodcd") fuelType: String,
        @Query("out") out: String = "json",
    ): OpinetResponseDto
}
