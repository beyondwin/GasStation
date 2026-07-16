package com.gasstation.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val uiState = SettingsUiState(
        searchRadius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
        mapProvider = MapProvider.TMAP,
    )

    @Test
    fun settings_overview() {
        composeRule.setContent {
            GasStationTheme {
                SettingsScreen(
                    uiState = uiState,
                    onSectionClick = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/snapshots/settings-overview.png")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h1400dp-xhdpi")
    fun settings_brand_detail() {
        composeRule.setContent {
            GasStationTheme {
                SettingsDetailScreen(
                    section = SettingsSection.BrandFilter,
                    options = uiState.optionsFor(SettingsSection.BrandFilter),
                    onBackClick = {},
                    onOptionClick = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/snapshots/settings-brand-detail.png")
    }
}
