package com.gasstation.domain.location

public sealed interface LocationAddressLookupResult {
    public data class Success(val addressLabel: String) : LocationAddressLookupResult

    public data object Unavailable : LocationAddressLookupResult

    public data class Error(val throwable: Throwable) : LocationAddressLookupResult
}
