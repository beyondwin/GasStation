package com.gasstation.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsSectionTest {

    @Test
    fun `settings sections expose stable resource IDs and route segments in legacy menu order`() {
        val sections = SettingsSection.entries

        assertEquals(
            listOf(
                listOf(
                    R.string.settings_group_explore_title,
                    R.string.settings_radius_title,
                    R.string.settings_radius_subtitle,
                    "search-radius",
                ),
                listOf(
                    R.string.settings_group_explore_title,
                    R.string.settings_fuel_type_title,
                    R.string.settings_fuel_type_subtitle,
                    "fuel-type",
                ),
                listOf(
                    R.string.settings_group_explore_title,
                    R.string.settings_brand_filter_title,
                    R.string.settings_brand_filter_subtitle,
                    "brand-filter",
                ),
                listOf(
                    R.string.settings_group_display_title,
                    R.string.settings_sort_order_title,
                    R.string.settings_sort_order_subtitle,
                    "sort-order",
                ),
                listOf(
                    R.string.settings_group_connection_title,
                    R.string.settings_map_provider_title,
                    R.string.settings_map_provider_subtitle,
                    "map-provider",
                ),
            ),
            sections.map { section ->
                listOf(
                    section.overlineResId,
                    section.titleResId,
                    section.subtitleResId,
                    section.routeSegment,
                )
            },
        )
    }

    @Test
    fun `require from route segment returns matching section and rejects unknown values`() {
        assertEquals(
            SettingsSection.SortOrder,
            SettingsSection.requireFromRouteSegment("sort-order"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            SettingsSection.requireFromRouteSegment("unknown")
        }
    }
}
