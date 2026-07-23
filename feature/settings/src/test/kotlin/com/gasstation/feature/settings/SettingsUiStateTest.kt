package com.gasstation.feature.settings

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUiStateTest {
    @Test
    fun `brand filter options expose one grouped alteul entry in filter order`() {
        val uiState = SettingsUiState.Ready(
            preferences = UserPreferences(
                searchRadius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
                brandFilter = BrandFilter.ALTEUL,
                sortOrder = SortOrder.DISTANCE,
                mapProvider = MapProvider.TMAP,
            ),
        )
        val options = uiState.optionsFor(SettingsSection.BrandFilter)

        assertEquals(
            listOf("전체", "SK에너지", "GS칼텍스", "현대오일뱅크", "S-OIL", "알뜰", "E1", "SK가스", "자가상표"),
            options.map { (it.label as StringResource.Raw).value },
        )
        val alteulOption = options.single { it.action == SettingsAction.BrandFilterSelected(BrandFilter.ALTEUL) }
        assertEquals(StringResource.raw("알뜰"), uiState.selectedLabelFor(SettingsSection.BrandFilter))
        assertEquals(StringResource.fromId(R.string.settings_brand_alteul_desc), alteulOption.subtitle)
        assertEquals(Brand.RTO, alteulOption.brandIconBrand)
        assertEquals("ALTEUL", alteulOption.brandIconTag)
    }

    @Test
    fun `option keys use stable enum names for every settings section`() {
        val uiState = SettingsUiState.Ready(UserPreferences.default())

        assertEquals(SearchRadius.entries.map(Enum<*>::name), uiState.optionsFor(SettingsSection.SearchRadius).map { it.key })
        assertEquals(FuelType.entries.map(Enum<*>::name), uiState.optionsFor(SettingsSection.FuelType).map { it.key })
        assertEquals(BrandFilter.entries.map(Enum<*>::name), uiState.optionsFor(SettingsSection.BrandFilter).map { it.key })
        assertEquals(SortOrder.entries.map(Enum<*>::name), uiState.optionsFor(SettingsSection.SortOrder).map { it.key })
        assertEquals(MapProvider.entries.map(Enum<*>::name), uiState.optionsFor(SettingsSection.MapProvider).map { it.key })
    }
}
