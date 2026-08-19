package com.gasstation.domain.location

public sealed interface LocationPermissionState {
    public data object Denied : LocationPermissionState
    public data object ApproximateGranted : LocationPermissionState
    public data object PreciseGranted : LocationPermissionState
}
