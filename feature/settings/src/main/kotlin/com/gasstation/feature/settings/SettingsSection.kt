package com.gasstation.feature.settings

enum class SettingsSectionGroup(
    val title: String,
    val subtitle: String,
) {
    Explore(
        title = "탐색 설정",
        subtitle = "주변 주유소를 어떤 기준으로 좁혀볼지 정합니다.",
    ),
    Display(
        title = "표시 설정",
        subtitle = "목록에서 먼저 읽을 우선순위를 고릅니다.",
    ),
    Connection(
        title = "연결 설정",
        subtitle = "길찾기에서 열 지도 서비스를 맞춥니다.",
    ),
}

enum class SettingsSection(
    val routeSegment: String,
    val group: SettingsSectionGroup,
    val title: String,
    val subtitle: String,
) {
    SearchRadius(
        routeSegment = "search-radius",
        group = SettingsSectionGroup.Explore,
        title = "찾기 범위",
        subtitle = "주변 주유소를 불러올 반경을 정합니다.",
    ),
    FuelType(
        routeSegment = "fuel-type",
        group = SettingsSectionGroup.Explore,
        title = "오일 타입",
        subtitle = "목록과 북마크에서 우선 확인할 유종을 고릅니다.",
    ),
    BrandFilter(
        routeSegment = "brand-filter",
        group = SettingsSectionGroup.Explore,
        title = "주유소 브랜드",
        subtitle = "브랜드 범위를 좁혀 비교 기준을 빠르게 맞춥니다.",
    ),
    SortOrder(
        routeSegment = "sort-order",
        group = SettingsSectionGroup.Display,
        title = "정렬기준",
        subtitle = "가격 우선 또는 거리 우선 스캔 방식을 선택합니다.",
    ),
    MapProvider(
        routeSegment = "map-provider",
        group = SettingsSectionGroup.Connection,
        title = "연동지도 서비스",
        subtitle = "길찾기 버튼에서 열 지도를 정합니다.",
    );

    val overline: String
        get() = group.title

    companion object {
        fun fromRouteSegment(routeSegment: String): SettingsSection? =
            entries.firstOrNull { section -> section.routeSegment == routeSegment }

        fun requireFromRouteSegment(routeSegment: String): SettingsSection =
            requireNotNull(fromRouteSegment(routeSegment)) {
                "Unknown settings section route segment: $routeSegment"
            }
    }
}
