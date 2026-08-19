package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates

public sealed interface LocationLookupResult {
    public data class Success(val coordinates: Coordinates) : LocationLookupResult

    public data object PermissionDenied : LocationLookupResult

    public data object Unavailable : LocationLookupResult

    public data object TimedOut : LocationLookupResult

    public data class Error(val throwable: Throwable) : LocationLookupResult
}
