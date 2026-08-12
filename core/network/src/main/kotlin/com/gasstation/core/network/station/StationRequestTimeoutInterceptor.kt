package com.gasstation.core.network.station

import okhttp3.Interceptor
import okhttp3.Response

/** Lets the application retry policy exclusively own HTTP 408 retries. */
internal object StationRequestTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HTTP_REQUEST_TIMEOUT || response.header(RETRY_AFTER_HEADER) != null) {
            return response
        }

        return response.newBuilder()
            .header(RETRY_AFTER_HEADER, DO_NOT_RETRY_IMMEDIATELY_SECONDS)
            .build()
    }

    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val RETRY_AFTER_HEADER = "Retry-After"
    private const val DO_NOT_RETRY_IMMEDIATELY_SECONDS = "1"
}
