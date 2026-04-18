package com.gasstation.feature.settings

enum class SettingsSection(
    val routeSegment: String,
    val title: String,
) {
    SearchRadius(
        routeSegment = "search-radius",
        title = "찾기 범위",
    ),
    FuelType(
        routeSegment = "fuel-type",
        title = "오일 타입",
    ),
    BrandFilter(
        routeSegment = "brand-filter",
        title = "주유소 브랜드",
    ),
    SortOrder(
        routeSegment = "sort-order",
        title = "정렬기준",
    ),
    MapProvider(
        routeSegment = "map-provider",
        title = "연동지도 서비스",
    );

    companion object {
        fun fromRouteSegment(routeSegment: String): SettingsSection? =
            entries.firstOrNull { section -> section.routeSegment == routeSegment }

        fun requireFromRouteSegment(routeSegment: String): SettingsSection =
            requireNotNull(fromRouteSegment(routeSegment)) {
                "Unknown settings section route segment: $routeSegment"
            }
    }
}
