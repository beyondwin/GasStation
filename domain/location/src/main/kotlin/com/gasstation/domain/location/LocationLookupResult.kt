package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates

sealed interface LocationLookupResult {
    data class Success(val coordinates: Coordinates) : LocationLookupResult

    data object PermissionDenied : LocationLookupResult

    data object Unavailable : LocationLookupResult

    data object TimedOut : LocationLookupResult

    data class Error(val throwable: Throwable) : LocationLookupResult
}
