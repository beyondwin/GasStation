package com.gasstation.location

import android.annotation.SuppressLint
import android.content.Context
import com.gasstation.BuildConfig
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidForegroundLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates? {
        loadDemoCoordinates(permissionState)?.let { return it }

        if (permissionState == LocationPermissionState.Denied) {
            return null
        }

        val priority = when (permissionState) {
            LocationPermissionState.PreciseGranted -> Priority.PRIORITY_HIGH_ACCURACY
            LocationPermissionState.ApproximateGranted -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationPermissionState.Denied -> return null
        }
        val client = LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(priority, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    continuation.resume(
                        location?.let {
                            Coordinates(
                                latitude = it.latitude,
                                longitude = it.longitude,
                            )
                        },
                    )
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    private fun loadDemoCoordinates(permissionState: LocationPermissionState): Coordinates? {
        if (!BuildConfig.DEMO_MODE) {
            return null
        }

        return runCatching {
            val type = Class.forName("com.gasstation.DemoLocationModule")
            val instance = type.getField("INSTANCE").get(null)
            type.getMethod(
                "currentLocation",
                LocationPermissionState::class.java,
            ).invoke(instance, permissionState) as? Coordinates
        }.getOrNull()
    }
}
