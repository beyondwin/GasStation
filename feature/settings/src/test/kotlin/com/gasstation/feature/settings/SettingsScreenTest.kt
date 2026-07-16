package com.gasstation.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.designsystem.component.gasStationBrandIconResource
import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun StringResource.resolve(): String = resolve(context)

    @Test
    fun `settings overview has title no close and separate title value body`() {
        val uiState = SettingsUiState.from(UserPreferences.default())
        val radiusLabel = uiState.selectedLabelFor(SettingsSection.SearchRadius).resolve()

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onSectionClick = {},
            )
        }

        composeRule.onNodeWithText("설정").assertExists()
        composeRule.onNodeWithContentDescription("닫기").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-group-Explore").assertExists()
        composeRule.onNodeWithText("찾기 범위").assertExists()
        composeRule.onNodeWithText(radiusLabel).assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러올 반경을 정합니다.").assertExists()
        composeRule.onAllNodesWithText("찾기 범위 : $radiusLabel").assertCountEquals(0)
    }

    @Test
    fun `settings overview preserves grouped flat rows`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onSectionClick = {},
            )
        }

        SettingsSectionGroup.entries.forEach { group ->
            composeRule
                .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
                .performScrollToNode(hasTestTag("$SETTINGS_GROUP_TAG_PREFIX${group.name}"))
            composeRule.onNodeWithTag("$SETTINGS_GROUP_TAG_PREFIX${group.name}").assertExists()
        }
        SettingsSection.entries.forEach { section ->
            composeRule
                .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
                .performScrollToNode(hasTestTag("$SETTINGS_ROW_TAG_PREFIX${section.routeSegment}"))
            composeRule.onNodeWithTag("$SETTINGS_ROW_TAG_PREFIX${section.routeSegment}").assertExists()
        }
    }

    @Test
    fun `settings current value and description stay inside row at 200 percent font scale`() {
        val uiState = SettingsUiState(
            searchRadius = SearchRadius.KM_5,
            fuelType = FuelType.PREMIUM_GASOLINE,
            brandFilter = BrandFilter.RTX,
            sortOrder = SortOrder.PRICE,
            mapProvider = MapProvider.KAKAO_NAVI,
        )
        val brandLabel = uiState.selectedLabelFor(SettingsSection.BrandFilter).resolve()

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    SettingsScreen(
                        uiState = uiState,
                        onSectionClick = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag("$SETTINGS_ROW_TAG_PREFIX${SettingsSection.BrandFilter.routeSegment}"))
        val rowBounds = composeRule
            .onNodeWithTag("$SETTINGS_ROW_TAG_PREFIX${SettingsSection.BrandFilter.routeSegment}", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithText("주유소 브랜드", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val valueBounds = composeRule
            .onNodeWithText(brandLabel, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val bodyBounds = composeRule
            .onNodeWithText("브랜드 범위를 좁혀 비교 기준을 빠르게 맞춥니다.", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        listOf(titleBounds, valueBounds, bodyBounds).forEach { bounds ->
            assertTrue("Expected expanded text to stay inside its row: $bounds vs $rowBounds", bounds in rowBounds)
        }
        assertTrue("Expected value to stack below the title at large font scale.", valueBounds.top >= titleBounds.bottom)
        assertTrue("Expected body to stack below the current value at large font scale.", bodyBounds.top >= valueBounds.bottom)
    }

    @Test
    fun `settings detail shows description once and flat radio rows`() {
        val options = SettingsUiState.from(UserPreferences.default()).optionsFor(SettingsSection.SearchRadius)

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.SearchRadius,
                options = options,
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("뒤로가기").assertExists()
        composeRule.onAllNodesWithText("주변 주유소를 불러올 반경을 정합니다.").assertCountEquals(1)
        composeRule.onNodeWithText("탐색 설정").assertDoesNotExist()
        composeRule.onNodeWithTag(SETTINGS_OPTIONS_GROUP_TAG).assertExists()
        options.forEach { option ->
            val actualRole = composeRule
                .onNodeWithText(option.label.resolve())
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Role)
            assertEquals(Role.RadioButton, actualRole)
        }
        composeRule.onNodeWithText("3km").assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("4km").assertIsNotSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(SETTINGS_SELECTED_CHECK_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `brand filter detail uses real assets visible labels and no duplicate icon announcement`() {
        val options = SettingsUiState.from(UserPreferences.default()).optionsFor(SettingsSection.BrandFilter)

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.BrandFilter,
                options = options,
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onNodeWithText("전체").assertExists()
        composeRule.onNodeWithTag("settings-brand-logo-ALL").assertDoesNotExist()
        listOf(BrandFilter.RTO, BrandFilter.RTX, BrandFilter.NHO, BrandFilter.ETC).forEach { filter ->
            composeRule
                .onNodeWithTag(SETTINGS_OPTIONS_GROUP_TAG)
                .performScrollToNode(hasText(filter.toLabel().resolve()))
            composeRule.onNodeWithText(filter.toLabel().resolve()).assertExists()
            composeRule.onNodeWithTag("settings-brand-logo-${filter.name}", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithContentDescription("${filter.toLabel().resolve()} 브랜드").assertDoesNotExist()
        }

        assertEquals(
            com.gasstation.core.designsystem.R.drawable.ic_rtx,
            Brand.RTO.gasStationBrandIconResource(),
        )
        assertEquals(
            com.gasstation.core.designsystem.R.drawable.ic_rtx,
            Brand.RTX.gasStationBrandIconResource(),
        )
        assertEquals(
            com.gasstation.core.designsystem.R.drawable.ic_rtx,
            Brand.NHO.gasStationBrandIconResource(),
        )
        assertEquals(
            com.gasstation.core.designsystem.R.drawable.ic_etc,
            Brand.ETC.gasStationBrandIconResource(),
        )
    }

    @Test
    fun `all brand option has no logo and keeps text inset contract explicit`() {
        val options = SettingsUiState.from(UserPreferences.default()).optionsFor(SettingsSection.BrandFilter)

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.BrandFilter,
                options = options,
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onNodeWithText("전체").assertLeftPositionInRootIsEqualTo(16.dp)
        composeRule.onNodeWithTag("settings-brand-logo-ALL").assertDoesNotExist()
    }

    private operator fun androidx.compose.ui.geometry.Rect.contains(other: androidx.compose.ui.geometry.Rect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}
