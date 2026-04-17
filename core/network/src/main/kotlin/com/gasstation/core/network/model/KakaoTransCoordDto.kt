package com.gasstation.core.network.model

data class KakaoTransCoordDto(
    val documents: List<Document> = emptyList(),
) {
    data class Document(
        val x: Double,
        val y: Double,
    )
}
