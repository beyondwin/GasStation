package com.gasstation.core.location

import android.location.Address

internal fun Address.toDisplayLabel(): String? {
    getAddressLine(0)?.cleanAddressPart()?.let { return it }

    val roadLabel = listOf(
        adminArea,
        locality,
        subLocality,
        thoroughfare,
        subThoroughfare,
    ).joinAddressParts()
    if (roadLabel != null) return roadLabel

    return listOf(
        adminArea,
        locality,
        subLocality,
        featureName,
    ).joinAddressParts()
}

private fun List<String?>.joinAddressParts(): String? =
    mapNotNull(String?::cleanAddressPart)
        .distinct()
        .joinToString(separator = " ")
        .takeIf(String::isNotBlank)

private fun String?.cleanAddressPart(): String? =
    this?.trim()?.takeIf(String::isNotBlank)
