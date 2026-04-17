package com.gasstation.core.network.di

import com.gasstation.core.network.service.KakaoService
import com.gasstation.core.network.service.OpinetService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val APP_BUILD_CONFIG_CLASS = "com.gasstation.BuildConfig"
    private const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
    private const val OPINET_BASE_URL = "http://www.opinet.co.kr/"

    @Provides
    @Singleton
    @Named("kakaoBaseUrl")
    fun provideKakaoBaseUrl(): String = KAKAO_BASE_URL

    @Provides
    @Singleton
    @Named("opinetBaseUrl")
    fun provideOpinetBaseUrl(): String = OPINET_BASE_URL

    @Provides
    @Singleton
    @Named("kakaoApiKey")
    fun provideKakaoApiKey(): String = readBuildConfigString("KAKAO_API_KEY")

    @Provides
    @Singleton
    @Named("opinetApiKey")
    fun provideOpinetApiKey(): String = readBuildConfigString("OPINET_API_KEY")

    @Provides
    @Singleton
    fun provideOpinetService(
        @Named("opinetBaseUrl") baseUrl: String,
    ): OpinetService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(defaultOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpinetService::class.java)

    @Provides
    @Singleton
    fun provideKakaoService(
        @Named("kakaoBaseUrl") baseUrl: String,
        @Named("kakaoApiKey") apiKey: String,
    ): KakaoService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            defaultOkHttpClient().newBuilder()
                .addInterceptor(KakaoAuthorizationInterceptor(apiKey))
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KakaoService::class.java)

    private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    private fun readBuildConfigString(fieldName: String): String = runCatching {
        val buildConfigClass = Class.forName(APP_BUILD_CONFIG_CLASS)
        buildConfigClass.getField(fieldName).get(null) as? String
    }.getOrNull().orEmpty()

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
