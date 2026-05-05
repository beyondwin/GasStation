package com.gasstation.feature.settings

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUiStateTest {
    @Test
    fun `brand filter option uses canonical rtx label`() {
        val uiState = SettingsUiState(
            searchRadius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.RTX,
            sortOrder = SortOrder.DISTANCE,
            mapProvider = MapProvider.TMAP,
        )

        assertEquals("고속도로알뜰", uiState.selectedLabelFor(SettingsSection.BrandFilter))
        assertEquals(
            "고속도로알뜰",
            uiState.optionsFor(SettingsSection.BrandFilter)
                .single { it.action == SettingsAction.BrandFilterSelected(BrandFilter.RTX) }
                .label,
        )
    }
}
