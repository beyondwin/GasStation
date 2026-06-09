package com.gasstation.feature.settings

import androidx.annotation.StringRes

enum class SettingsSectionGroup(@param:StringRes val titleResId: Int, @param:StringRes val subtitleResId: Int) {
    Explore(
        titleResId = R.string.settings_group_explore_title,
        subtitleResId = R.string.settings_group_explore_subtitle,
    ),
    Display(
        titleResId = R.string.settings_group_display_title,
        subtitleResId = R.string.settings_group_display_subtitle,
    ),
    Connection(
        titleResId = R.string.settings_group_connection_title,
        subtitleResId = R.string.settings_group_connection_subtitle,
    ),
}

enum class SettingsSection(
    val routeSegment: String,
    val group: SettingsSectionGroup,
    @param:StringRes val titleResId: Int,
    @param:StringRes val subtitleResId: Int,
) {
    SearchRadius(
        routeSegment = "search-radius",
        group = SettingsSectionGroup.Explore,
        titleResId = R.string.settings_radius_title,
        subtitleResId = R.string.settings_radius_subtitle,
    ),
    FuelType(
        routeSegment = "fuel-type",
        group = SettingsSectionGroup.Explore,
        titleResId = R.string.settings_fuel_type_title,
        subtitleResId = R.string.settings_fuel_type_subtitle,
    ),
    BrandFilter(
        routeSegment = "brand-filter",
        group = SettingsSectionGroup.Explore,
        titleResId = R.string.settings_brand_filter_title,
        subtitleResId = R.string.settings_brand_filter_subtitle,
    ),
    SortOrder(
        routeSegment = "sort-order",
        group = SettingsSectionGroup.Display,
        titleResId = R.string.settings_sort_order_title,
        subtitleResId = R.string.settings_sort_order_subtitle,
    ),
    MapProvider(
        routeSegment = "map-provider",
        group = SettingsSectionGroup.Connection,
        titleResId = R.string.settings_map_provider_title,
        subtitleResId = R.string.settings_map_provider_subtitle,
    ),
    ;

    val overlineResId: Int
        get() = group.titleResId

    companion object {
        fun fromRouteSegment(routeSegment: String): SettingsSection? =
            entries.firstOrNull { section -> section.routeSegment == routeSegment }

        fun requireFromRouteSegment(routeSegment: String): SettingsSection = requireNotNull(fromRouteSegment(routeSegment)) {
            "Unknown settings section route segment: $routeSegment"
        }
    }
}
