package com.gasstation.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidForegroundLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
    private val currentLocationClient: CurrentLocationClient,
) : ForegroundLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        if (demoLocationOverride.isPresent) {
            return demoLocationOverride.get().currentLocation(permissionState)
                ?.let(LocationLookupResult::Success)
                ?: LocationLookupResult.Unavailable
        }
        if (permissionState == LocationPermissionState.Denied) return LocationLookupResult.PermissionDenied

        val priority = when (permissionState) {
            LocationPermissionState.PreciseGranted -> Priority.PRIORITY_HIGH_ACCURACY
            LocationPermissionState.ApproximateGranted -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationPermissionState.Denied -> return LocationLookupResult.PermissionDenied
        }
        val cancellationTokenSource = CancellationTokenSource()

        val result = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<LocationLookupResult> { continuation ->
                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
                try {
                    currentLocationClient.getCurrentLocation(
                        context = context,
                        priority = priority,
                        cancellationTokenSource = cancellationTokenSource,
                        onSuccess = { coordinates ->
                            if (continuation.isActive) {
                                continuation.resume(
                                    coordinates?.let(LocationLookupResult::Success)
                                        ?: LocationLookupResult.Unavailable,
                                )
                            }
                        },
                        onFailure = { throwable ->
                            if (continuation.isActive) {
                                continuation.resume(LocationLookupResult.Error(throwable))
                            }
                        },
                    )
                } catch (throwable: CancellationException) {
                    continuation.cancel(throwable)
                } catch (exception: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(LocationLookupResult.Error(exception))
                    }
                }
            }
        }

        if (result != null) {
            return result
        }

        cancellationTokenSource.cancel()
        return LocationLookupResult.TimedOut
    }
}
