package com.gasstation.core.location

import android.content.Context
import android.location.Geocoder
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidAddressResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AddressResolver {
    @Suppress("DEPRECATION")
    override suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult =
        withContext(Dispatchers.IO) {
            try {
                val address = Geocoder(context, Locale.KOREA)
                    .getFromLocation(
                        coordinates.latitude,
                        coordinates.longitude,
                        1,
                    )
                    ?.firstOrNull()
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
        }
}
