package com.gasstation.domain.location

sealed interface LocationPermissionState {
    data object Denied : LocationPermissionState
    data object ApproximateGranted : LocationPermissionState
    data object PreciseGranted : LocationPermissionState
}
