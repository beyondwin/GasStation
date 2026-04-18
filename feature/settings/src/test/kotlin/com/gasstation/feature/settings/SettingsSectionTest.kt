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
                listOf("탐색 설정", "찾기 범위", "주변 주유소를 불러올 반경을 정합니다.", "search-radius"),
                listOf("탐색 설정", "오일 타입", "목록과 관심 비교에서 우선 확인할 유종을 고릅니다.", "fuel-type"),
                listOf("탐색 설정", "주유소 브랜드", "브랜드 범위를 좁혀 비교 기준을 빠르게 맞춥니다.", "brand-filter"),
                listOf("표시 설정", "정렬기준", "가격 우선 또는 거리 우선 스캔 방식을 선택합니다.", "sort-order"),
                listOf("연결 설정", "연동지도 서비스", "길찾기 버튼에서 열 지도를 정합니다.", "map-provider"),
            ),
            sections.map { section ->
                listOf(
                    section.overline,
                    section.title,
                    section.subtitle,
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
