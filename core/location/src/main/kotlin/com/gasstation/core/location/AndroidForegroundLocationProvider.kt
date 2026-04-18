package com.gasstation.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.gasstation.core.model.Coordinates
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidForegroundLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
) : ForegroundLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates? {
        if (demoLocationOverride.isPresent) {
            return demoLocationOverride.get().currentLocation(permissionState)
        }
        if (permissionState == LocationPermissionState.Denied) return null

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
                        location?.let { Coordinates(it.latitude, it.longitude) },
                    )
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}
