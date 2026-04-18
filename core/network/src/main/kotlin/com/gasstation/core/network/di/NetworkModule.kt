package com.gasstation.core.network.di

import com.gasstation.core.network.service.KakaoService
import com.gasstation.core.network.service.OpinetService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
    private const val OPINET_BASE_URL = "http://www.opinet.co.kr/"

    fun provideKakaoBaseUrl(): String = KAKAO_BASE_URL

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

    fun provideKakaoService(
        baseUrl: String,
        config: NetworkRuntimeConfig,
    ): KakaoService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            defaultOkHttpClient().newBuilder()
                .addInterceptor(KakaoAuthorizationInterceptor(config.kakaoApiKey))
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KakaoService::class.java)

    private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    private class KakaoAuthorizationInterceptor(
        private val apiKey: String,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain) = chain.proceed(
            chain.request().newBuilder().apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "KakaoAK $apiKey")
                }
            }.build(),
        )
    }
}
