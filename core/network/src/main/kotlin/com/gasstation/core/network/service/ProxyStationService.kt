package com.gasstation.core.network.service

import com.gasstation.core.network.model.ProxyStationSearchRequestDto
import com.gasstation.core.network.model.ProxyStationSearchResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface ProxyStationService {
    @POST("/v1/stations/nearby")
    suspend fun findStations(@Body request: ProxyStationSearchRequestDto): ProxyStationSearchResponseDto
}
