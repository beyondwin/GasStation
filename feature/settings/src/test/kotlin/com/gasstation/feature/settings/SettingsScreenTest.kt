package com.gasstation.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performClick
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
    fun `settings option selector uses exact enum name`() {
        assertEquals("settings-option-KM_4", settingsOptionTestTag(SearchRadius.KM_4.name))
        assertEquals("settings-option-GASOLINE", settingsOptionTestTag(FuelType.GASOLINE.name))
        assertEquals("settings-option-ALL", settingsOptionTestTag(BrandFilter.ALL.name))
        assertEquals("settings-option-DISTANCE", settingsOptionTestTag(SortOrder.DISTANCE.name))
    }

    @Test
    fun `settings overview has title no close and separate title value body`() {
        val uiState = SettingsUiState.Ready(UserPreferences.default())
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
        composeRule.onNodeWithText("주변 목록 검색에 사용할 반경을 정합니다.").assertExists()
        composeRule.onAllNodesWithText("찾기 범위 : $radiusLabel").assertCountEquals(0)
    }

    @Test
    fun `settings overview preserves grouped flat rows`() {
        val uiState = SettingsUiState.Ready(UserPreferences.default())

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
    fun `settings overview names each preference consumer scope and KakaoMap`() {
        val uiState = SettingsUiState.Ready(
            UserPreferences.default().copy(mapProvider = MapProvider.KAKAO_MAP),
        )

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onSectionClick = {},
            )
        }

        listOf(
            "주변 목록 검색에 사용할 반경을 정합니다.",
            "주변 목록과 관심 주유소 비교에 사용할 유종을 고릅니다.",
            "주변 목록에서 비교할 브랜드 범위를 정합니다.",
            "주변 목록의 가격·거리 정렬 기준을 정합니다.",
            "카카오맵",
        ).forEach { expected ->
            composeRule
                .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
                .performScrollToNode(hasText(expected))
            composeRule.onNodeWithText(expected).assertExists()
        }
    }

    @Test
    fun `settings current value and description stay inside row at 200 percent font scale`() {
        val uiState = SettingsUiState.Ready(
            preferences = UserPreferences(
                searchRadius = SearchRadius.KM_5,
                fuelType = FuelType.PREMIUM_GASOLINE,
                brandFilter = BrandFilter.ALTEUL,
                sortOrder = SortOrder.PRICE,
                mapProvider = MapProvider.KAKAO_MAP,
            ),
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
            .onNodeWithText("주변 목록에서 비교할 브랜드 범위를 정합니다.", useUnmergedTree = true)
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
        val options = SettingsUiState.Ready(UserPreferences.default()).optionsFor(SettingsSection.SearchRadius)

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.SearchRadius,
                options = options,
                isSaving = false,
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("뒤로가기").assertExists()
        composeRule.onAllNodesWithText("주변 목록 검색에 사용할 반경을 정합니다.").assertCountEquals(1)
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
        composeRule.onNodeWithTag("settings-option-KM_4").assertExists()
        composeRule.onNodeWithTag(SETTINGS_SELECTED_CHECK_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `brand filter detail exposes one grouped alteul row and stable logo tag`() {
        val options = SettingsUiState.Ready(UserPreferences.default()).optionsFor(SettingsSection.BrandFilter)
        var selectedAction: SettingsAction? = null

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.BrandFilter,
                options = options,
                isSaving = false,
                onBackClick = {},
                onOptionClick = { selectedAction = it.action },
            )
        }

        composeRule.onNodeWithText("전체").assertExists()
        composeRule.onNodeWithTag("settings-option-ALL").assertExists()
        composeRule.onNodeWithTag("settings-brand-logo-ALL").assertDoesNotExist()
        composeRule
            .onNodeWithTag(SETTINGS_OPTIONS_GROUP_TAG)
            .performScrollToNode(hasText("알뜰"))
        composeRule.onNodeWithText("알뜰").assertExists()
        composeRule.onNodeWithTag("settings-option-ALTEUL").assertExists().performClick()
        composeRule.runOnIdle {
            assertEquals(
                SettingsAction.BrandFilterSelected(BrandFilter.ALTEUL),
                selectedAction,
            )
        }
        composeRule.onNodeWithText("알뜰주유소 전체를 표시합니다.").assertExists()
        composeRule.onNodeWithTag("settings-brand-logo-ALTEUL", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings-brand-logo-RTO").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-brand-logo-RTX").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-brand-logo-NHO").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("알뜰 브랜드").assertDoesNotExist()

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
        val options = SettingsUiState.Ready(UserPreferences.default()).optionsFor(SettingsSection.BrandFilter)

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.BrandFilter,
                options = options,
                isSaving = false,
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onNodeWithText("전체").assertLeftPositionInRootIsEqualTo(16.dp)
        composeRule.onNodeWithTag("settings-brand-logo-ALL").assertDoesNotExist()
    }

    @Test
    fun `settings state screens distinguish loading from retryable load failure`() {
        var retryCount = 0
        val showFailure = mutableStateOf(false)

        composeRule.setContent {
            if (showFailure.value) {
                SettingsLoadFailureScreen(onRetry = { retryCount += 1 })
            } else {
                SettingsLoadingScreen()
            }
        }
        composeRule.onNodeWithText("설정을 불러오는 중입니다.").assertExists()
        composeRule.onNodeWithText("다시 시도").assertDoesNotExist()

        composeRule.runOnIdle { showFailure.value = true }
        composeRule.onNodeWithText("설정을 불러오지 못했습니다.").assertExists()
        composeRule.onNodeWithText("저장된 설정을 다시 확인해 주세요.").assertExists()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun `settings detail uses enum option tags and disables navigation while saving`() {
        val options = SettingsUiState.Ready(UserPreferences.default()).optionsFor(SettingsSection.FuelType)

        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.FuelType,
                options = options,
                isSaving = true,
                onBackClick = {},
                onOptionClick = {},
            )
        }

        options.forEach { option ->
            composeRule
                .onNodeWithTag("$SETTINGS_OPTION_TAG_PREFIX${option.key}")
                .assertExists()
                .assertIsNotEnabled()
        }
        composeRule.onNodeWithContentDescription("뒤로가기").assertIsNotEnabled()
        composeRule.onNodeWithText("저장 중").assertExists()
    }

    private operator fun androidx.compose.ui.geometry.Rect.contains(other: androidx.compose.ui.geometry.Rect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}
