package com.gasstation.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsSectionTest {

    @Test
    fun `settings sections expose stable titles and route segments in legacy menu order`() {
        val sections = SettingsSection.entries

        assertEquals(
            listOf(
                "찾기 범위" to "search-radius",
                "오일 타입" to "fuel-type",
                "주유소 브랜드" to "brand-filter",
                "정렬기준" to "sort-order",
                "연동지도 서비스" to "map-provider",
            ),
            sections.map { section -> section.title to section.routeSegment },
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
