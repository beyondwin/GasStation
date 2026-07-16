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
        renderOverview("settings-overview.png")
    }

    @Test
    fun settings_overview_dark() {
        renderOverview("settings-overview-dark.png", darkTheme = true)
    }

    private fun renderOverview(name: String, darkTheme: Boolean = false) {
        composeRule.setContent {
            GasStationTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    uiState = uiState,
                    onSectionClick = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h1400dp-xhdpi")
    fun settings_brand_detail() {
        renderBrandDetail("settings-brand-detail.png")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h1400dp-xhdpi")
    fun settings_brand_detail_dark() {
        renderBrandDetail("settings-brand-detail-dark.png", darkTheme = true)
    }

    private fun renderBrandDetail(name: String, darkTheme: Boolean = false) {
        composeRule.setContent {
            GasStationTheme(darkTheme = darkTheme) {
                SettingsDetailScreen(
                    section = SettingsSection.BrandFilter,
                    options = uiState.optionsFor(SettingsSection.BrandFilter),
                    onBackClick = {},
                    onOptionClick = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name")
    }
}
