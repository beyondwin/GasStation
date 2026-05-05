package com.gasstation.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidAddressResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AddressResolver {
    override suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult =
        try {
            val address = Geocoder(context, Locale.KOREA)
                .firstAddressFor(
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                )
            val label = address?.toDisplayLabel()

            if (label == null) {
                LocationAddressLookupResult.Unavailable
            } else {
                LocationAddressLookupResult.Success(label)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LocationAddressLookupResult.Error(exception)
        }

    private suspend fun Geocoder.firstAddressFor(
        latitude: Double,
        longitude: Double,
    ): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AndroidGeocoderAsyncLookup(this)
                .awaitFromLocation(
                    latitude = latitude,
                    longitude = longitude,
                    maxResults = 1,
                )
                ?.firstOrNull()
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun interface GeocoderAsyncLookup {
    fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int,
        listener: Geocoder.GeocodeListener,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal suspend fun GeocoderAsyncLookup.awaitFromLocation(
    latitude: Double,
    longitude: Double,
    maxResults: Int,
): List<Address>? =
    suspendCancellableCoroutine { continuation ->
        try {
            getFromLocation(
                latitude,
                longitude,
                maxResults,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resumeIfActive(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resumeExceptionIfActive(
                            GeocoderLookupException(errorMessage),
                        )
                    }
                },
            )
        } catch (exception: Exception) {
            continuation.resumeExceptionIfActive(exception)
        }
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidGeocoderAsyncLookup(
    private val geocoder: Geocoder,
) : GeocoderAsyncLookup {
    override fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int,
        listener: Geocoder.GeocodeListener,
    ) {
        geocoder.getFromLocation(latitude, longitude, maxResults, listener)
    }
}

internal class GeocoderLookupException(
    message: String?,
) : IOException(message ?: "Geocoder lookup failed")

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) {
        resume(value)
    }
}

private fun <T> CancellableContinuation<T>.resumeExceptionIfActive(exception: Exception) {
    if (isActive) {
        resumeWithException(exception)
    }
}
