package com.gasstation.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
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
        setContentAfterFullInvalidation {
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
        setContentAfterFullInvalidation {
            GasStationTheme(darkTheme = darkTheme) {
                SettingsDetailScreen(
                    section = SettingsSection.BrandFilter,
                    options = uiState.optionsFor(SettingsSection.BrandFilter),
                    onBackClick = {},
                    onOptionClick = {},
                )
            }
        }

        val alteulTop = composeRule.onNodeWithText("알뜰")
            .fetchSemanticsNode()
            .boundsInRoot.top
        val etcTop = composeRule.onNodeWithText("자가상표")
            .fetchSemanticsNode()
            .boundsInRoot.top
        assertTrue("Grouped Alteul should render before the final ETC option.", alteulTop < etcTop)
        composeRule.onNodeWithText("알뜰주유소 전체를 표시합니다.").assertExists()
        listOf("자영알뜰", "고속도로알뜰", "농협알뜰").forEach { legacyLabel ->
            composeRule.onAllNodesWithText(legacyLabel).assertCountEquals(0)
        }

        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name")
    }

    private fun setContentAfterFullInvalidation(content: @Composable () -> Unit) {
        // Robolectric's native canvas can retain pixels between screenshot tests. An opaque,
        // conspicuous staging frame forces a full redraw and makes any incomplete redraw visible.
        val showContent = mutableStateOf(false)
        composeRule.setContent {
            if (showContent.value) {
                content()
            } else {
                Box(Modifier.fillMaxSize().background(Color.Magenta))
            }
        }
        composeRule.runOnIdle { showContent.value = true }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }
}
