package com.gasstation.domain.location

sealed interface LocationAddressLookupResult {
    data class Success(val addressLabel: String) : LocationAddressLookupResult

    data object Unavailable : LocationAddressLookupResult

    data class Error(val throwable: Throwable) : LocationAddressLookupResult
}
