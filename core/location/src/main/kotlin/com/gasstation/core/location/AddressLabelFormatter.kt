package com.gasstation.core.location

import android.location.Address

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
        ?.toAdministrativeDongLabel()
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
    val parts = mapNotNull(String?::cleanAddressPart)
        .flatMap(String::toAddressTokens)
        .distinct()
    return parts.toAdministrativeDongLabel()
}

private fun String.toAdministrativeDongLabel(): String? {
    return toAddressTokens().toAdministrativeDongLabel()
}

private fun List<String>.toAdministrativeDongLabel(): String? {
    val dongIndex = indexOfLast(String::isAdministrativeDongPart)
    if (dongIndex < 0) return null

    val districtIndex = findLastAdminIndexBefore(dongIndex, suffixes = listOf("구", "군"))
    val regionIndex = if (districtIndex >= 0) {
        findLastAdminIndexBefore(districtIndex, suffixes = listOf("시", "도"))
            .takeIf { it >= 0 } ?: findFallbackRegionIndexBefore(districtIndex)
    } else {
        findLastAdminIndexBefore(dongIndex, suffixes = listOf("시", "도"))
    }

    return listOf(regionIndex, districtIndex, dongIndex)
        .filter { it >= 0 }
        .distinct()
        .map(::get)
        .joinToString(separator = " ")
        .takeIf(String::isNotBlank)
}

private fun String.isAdministrativeDongPart(): Boolean {
    val normalized = trim('(', ')', '[', ']', ',', '.')
    return normalized.endsWith("동") && normalized.dropLast(1).any { it in '가'..'힣' }
}

private fun String.toAddressTokens(): List<String> =
    split(Regex("\\s+"))
        .asSequence()
        .map { it.trim('(', ')', '[', ']', ',', '.') }
        .filter(String::isNotBlank)
        .filterNot { it == "대한민국" || it.equals("KR", ignoreCase = true) }
        .toList()
        .joinSplitAdministrativeTokens()

private fun List<String>.joinSplitAdministrativeTokens(): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < size) {
        val current = this[index]
        val next = getOrNull(index + 1)
        if (next in setOf("특별시", "광역시", "특별자치시", "특별자치도")) {
            result += current + next
            index += 2
        } else {
            result += current
            index += 1
        }
    }
    return result
}

private fun List<String>.findLastAdminIndexBefore(
    endExclusive: Int,
    suffixes: List<String>,
): Int = asSequence()
    .take(endExclusive)
    .withIndex()
    .filter { (_, token) -> suffixes.any(token::endsWith) && token.dropLast(1).any { it in '가'..'힣' } }
    .lastOrNull()
    ?.index ?: -1

private fun List<String>.findFallbackRegionIndexBefore(endExclusive: Int): Int =
    asSequence()
        .take(endExclusive)
        .withIndex()
        .filter { (_, token) -> token in setOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종") }
        .lastOrNull()
        ?.index ?: -1

private fun List<String?>.joinAddressParts(): String? =
    mapNotNull(String?::cleanAddressPart)
        .distinct()
        .joinToString(separator = " ")
        .takeIf(String::isNotBlank)

private fun String?.cleanAddressPart(): String? =
    this?.trim()?.takeIf(String::isNotBlank)
