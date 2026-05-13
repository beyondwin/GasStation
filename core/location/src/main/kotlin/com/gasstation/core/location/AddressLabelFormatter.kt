package com.gasstation.core.location

import android.location.Address
import com.gasstation.domain.location.administrativeDongLabelOrNull
import com.gasstation.domain.location.normalizeCurrentAddressLabel

internal fun Address.toDisplayLabel(): String? {
    listOf(
        adminArea,
        subAdminArea,
        locality,
        subLocality,
        thoroughfare,
        featureName,
    ).joinThroughAdministrativeDong()?.let { return it }

    getAddressLine(0)
        ?.cleanAddressPart()
        ?.let(::normalizeCurrentAddressLabel)
        ?.let { return it }

    val roadLabel = listOf(
        adminArea,
        subAdminArea,
        locality,
        subLocality,
        thoroughfare,
        subThoroughfare,
    ).joinAddressParts()
    if (roadLabel != null) return roadLabel

    return listOf(
        adminArea,
        subAdminArea,
        locality,
        subLocality,
        featureName,
    ).joinAddressParts()
}

private fun List<String?>.joinThroughAdministrativeDong(): String? {
    val cleanAddressPart = mapNotNull(String?::cleanAddressPart)
        .joinToString(separator = " ")
    return administrativeDongLabelOrNull(cleanAddressPart)
}

private fun List<String?>.joinAddressParts(): String? = mapNotNull(String?::cleanAddressPart)
    .distinct()
    .joinToString(separator = " ")
    .takeIf(String::isNotBlank)

private fun String?.cleanAddressPart(): String? = this?.trim()?.takeIf(String::isNotBlank)
