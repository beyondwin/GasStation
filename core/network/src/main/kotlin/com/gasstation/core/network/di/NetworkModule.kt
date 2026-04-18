package com.gasstation.core.network.di

import com.gasstation.core.network.service.OpinetService
import java.time.Duration
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private const val OPINET_BASE_URL = "http://www.opinet.co.kr/"

    fun provideOpinetBaseUrl(): String = OPINET_BASE_URL

    fun provideOpinetApiKey(config: NetworkRuntimeConfig): String = config.opinetApiKey

    fun provideOpinetService(
        baseUrl: String,
    ): OpinetService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(defaultOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpinetService::class.java)

    private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(8))
        .connectTimeout(Duration.ofSeconds(4))
        .readTimeout(Duration.ofSeconds(8))
        .build()
}
