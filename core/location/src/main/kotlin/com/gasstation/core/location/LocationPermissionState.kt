package com.gasstation.core.location

sealed interface LocationPermissionState {
    data object Denied : LocationPermissionState
    data object ApproximateGranted : LocationPermissionState
    data object PreciseGranted : LocationPermissionState
}
