package com.gasstation.feature.stationlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.designsystem.gasStationBrandFilterLabel
import com.gasstation.core.designsystem.gasStationSearchRadiusLabel
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
class StationListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `external map final failure shows Korean resource snackbar`() {
        assertExternalMapFailureSnackbar("지도 앱을 열지 못했습니다.")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-xhdpi")
    fun `external map final failure shows English resource snackbar`() {
        assertExternalMapFailureSnackbar("Could not open the map app.")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun assertExternalMapFailureSnackbar(expectedMessage: String) = runTest {
        val snackbarHostState = SnackbarHostState()
        val command = StationListUiCommand(
            id = 17L,
            payload = StationListCommandPayload.OpenExternalMap(
                provider = MapProvider.NAVER_MAP,
                stationName = "강남주유소",
                originLatitude = 37.498095,
                originLongitude = 127.027610,
                latitude = 37.499095,
                longitude = 127.128610,
            ),
        )
        val acknowledgements = mutableListOf<Long>()

        val showJob = launch {
            handleAndAcknowledgeStationListCommand(
                command = command,
                handle = { payload ->
                    openExternalMapOrShowFailure(
                        command = payload as StationListCommandPayload.OpenExternalMap,
                        onOpenExternalMap = { false },
                        snackbarHostState = snackbarHostState,
                        resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources,
                    )
                },
                acknowledge = acknowledgements::add,
            )
        }
        runCurrent()

        assertEquals(
            expectedMessage,
            snackbarHostState.currentSnackbarData?.visuals?.message,
        )
        assertTrue(acknowledgements.isEmpty())
        snackbarHostState.currentSnackbarData?.dismiss()
        showJob.join()
        assertEquals(listOf(17L), acknowledgements)
    }

    @Test
    fun `filter radius menu selects an option once`() {
        val actions = mutableListOf<StationListAction>()
        setFilterContent(actions = actions)

        composeRule.onNodeWithTag(STATION_LIST_RADIUS_FILTER_TAG).performClick()

        composeRule.onNodeWithText("검색 반경").assertExists()
        SearchRadius.entries.forEach { radius ->
            assertFilterOptionText(
                radius.name,
                radius.gasStationSearchRadiusLabel().resolve(ApplicationProvider.getApplicationContext()),
            )
        }
        composeRule.onNodeWithTag("$STATION_LIST_FILTER_OPTION_TAG_PREFIX${SearchRadius.KM_4.name}")
            .performClick()

        assertEquals(listOf(StationListAction.SearchRadiusSelected(SearchRadius.KM_4)), actions)
    }

    @Test
    fun `filter fuel menu contains every option below the shared menu`() {
        setFilterContent()

        composeRule.onNodeWithTag(STATION_LIST_FUEL_FILTER_TAG).performClick()

        composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG).assertExists()
        composeRule.onNodeWithText("유종 선택").assertExists()
        FuelType.entries.forEach { fuelType ->
            assertFilterOptionText(fuelType.name, filterFuelLabel(fuelType))
        }
    }

    @Test
    fun `filter brand menu shows grouped alteul and omits individual alteul labels`() {
        setFilterContent()

        composeRule.onNodeWithTag(STATION_LIST_BRAND_FILTER_TAG).performClick()

        composeRule.onNodeWithText("브랜드 선택").assertExists()
        composeRule.onNodeWithText("알뜰").assertExists()
        composeRule.onNodeWithText("자가상표").assertExists()
        listOf("자영알뜰", "고속도로알뜰", "농협알뜰").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
        }
    }

    @Test
    fun `filter rail keeps exactly one menu open when a different chip is tapped`() {
        setFilterContent()

        composeRule.onNodeWithTag(STATION_LIST_RADIUS_FILTER_TAG).performClick()
        composeRule.onNodeWithTag(STATION_LIST_FUEL_FILTER_TAG).performClick()

        composeRule.onAllNodesWithTag(STATION_LIST_FILTER_MENU_TAG).assertCountEquals(1)
        composeRule.onNodeWithText("유종 선택").assertExists()
    }

    @Test
    fun `filter menu exposes accessible dismiss semantics`() {
        setFilterContent()
        composeRule.onNodeWithTag(STATION_LIST_RADIUS_FILTER_TAG).performClick()

        composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG)
            .performSemanticsAction(SemanticsActions.Dismiss) { action -> action() }

        composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `filter chips and options keep radio selection touch targets and brand logos`() {
        setFilterContent()

        listOf(
            STATION_LIST_RADIUS_FILTER_TAG,
            STATION_LIST_FUEL_FILTER_TAG,
            STATION_LIST_BRAND_FILTER_TAG,
        ).forEach(::assertMinimumTouchHeight)
        composeRule.onNodeWithTag(STATION_LIST_BRAND_FILTER_TAG).performClick()

        composeRule.onNodeWithTag("$STATION_LIST_FILTER_OPTION_TAG_PREFIX${BrandFilter.ALL.name}")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        BrandFilter.entries.forEach { brandFilter ->
            assertMinimumTouchHeight("$STATION_LIST_FILTER_OPTION_TAG_PREFIX${brandFilter.name}")
        }
        composeRule.onAllNodesWithTag("$STATION_LIST_FILTER_BRAND_LOGO_TAG_PREFIX${BrandFilter.ALL.name}")
            .assertCountEquals(0)
        composeRule.onNodeWithTag(
            "$STATION_LIST_FILTER_BRAND_LOGO_TAG_PREFIX${BrandFilter.ALTEUL.name}",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `opened filter chip exposes collapse indicator and selected option renders a trailing check`() {
        setFilterContent()

        composeRule.onNodeWithTag(STATION_LIST_RADIUS_FILTER_TAG).performClick()

        composeRule.onNodeWithTag(
            "$STATION_LIST_FILTER_CHEVRON_TAG_PREFIX${StationListFilterMenuKind.Radius.name}",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription("필터 메뉴 접기").assertExists()
        composeRule.onNodeWithTag(
            "$STATION_LIST_FILTER_SELECTED_CHECK_TAG_PREFIX${SearchRadius.KM_3.name}",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w320dp-h260dp-xhdpi")
    fun `filter menu scrolls an initially hidden last brand option into view before selection`() {
        val actions = mutableListOf<StationListAction>()
        setFilterContent(actions = actions, width = 320.dp, height = 260.dp)
        composeRule.onNodeWithTag(STATION_LIST_BRAND_FILTER_TAG).performClick()

        val rootBounds = composeRule.onNodeWithTag(STATION_LIST_ROOT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val menuBounds = composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("Expected menu left edge inside root.", menuBounds.left >= rootBounds.left)
        assertTrue("Expected menu right edge inside root.", menuBounds.right <= rootBounds.right)
        assertTrue("Expected menu top edge inside root.", menuBounds.top >= rootBounds.top)
        assertTrue("Expected menu bottom edge inside root.", menuBounds.bottom <= rootBounds.bottom)

        val etcOptionTag = "$STATION_LIST_FILTER_OPTION_TAG_PREFIX${BrandFilter.ETC.name}"
        composeRule.onNodeWithTag(etcOptionTag).assertIsNotDisplayed()

        composeRule.onNodeWithTag(etcOptionTag).performScrollTo()
        composeRule.onNodeWithTag(etcOptionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(etcOptionTag)
            .performClick()

        assertEquals(listOf(StationListAction.BrandFilterSelected(BrandFilter.ETC)), actions)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w320dp-h260dp-xhdpi")
    fun `radius filter label and chevron remain contained at two times font scale`() {
        setFilterContent(width = 320.dp, height = 260.dp, fontScale = 2f)

        val radiusChipBounds = composeRule.onNodeWithTag(
            STATION_LIST_RADIUS_FILTER_TAG,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val radiusChipLabelBounds = composeRule.onNode(
            matcher = hasText("3km") and hasAnyAncestor(hasTestTag(STATION_LIST_RADIUS_FILTER_TAG)),
            useUnmergedTree = true,
        ).assertTextEquals("3km").fetchSemanticsNode().boundsInRoot
        assertBoundsContained("radius filter chip label", radiusChipLabelBounds, radiusChipBounds)
        val radiusChevronBounds = composeRule.onNodeWithTag(
            "$STATION_LIST_FILTER_CHEVRON_TAG_PREFIX${StationListFilterMenuKind.Radius.name}",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertBoundsContained("radius filter chip chevron", radiusChevronBounds, radiusChipBounds)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w320dp-h260dp-xhdpi")
    fun `brand filter menu remains contained and selectable at two times font scale`() {
        val actions = mutableListOf<StationListAction>()
        setFilterContent(
            actions = actions,
            width = 320.dp,
            height = 260.dp,
            fontScale = 2f,
        )
        composeRule.onNodeWithTag(STATION_LIST_BRAND_FILTER_TAG).performClick()

        val rootBounds = composeRule.onNodeWithTag(STATION_LIST_ROOT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val menuBounds = composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertBoundsContained("brand menu", menuBounds, rootBounds)

        val titleNode = composeRule.onNodeWithText("브랜드 선택", useUnmergedTree = true)
            .assertTextEquals("브랜드 선택")
        assertTextFitsHorizontally("brand menu title", titleNode, menuBounds, rootBounds)

        BrandFilter.entries.forEach { brandFilter ->
            val label = brandFilter.gasStationBrandFilterLabel()
            val textNode = filterOptionTextNode(brandFilter, label)
                .assertTextEquals(label)
            assertTextFitsHorizontally("${brandFilter.name} option label", textNode, menuBounds, rootBounds)
        }

        val etcOptionTag = "$STATION_LIST_FILTER_OPTION_TAG_PREFIX${BrandFilter.ETC.name}"
        composeRule.onNodeWithTag(etcOptionTag).assertIsNotDisplayed()

        composeRule.onNodeWithTag(etcOptionTag).performScrollTo()
        val scrolledMenuBounds = composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val etcOption = composeRule.onNodeWithTag(etcOptionTag)
            .assertIsDisplayed()
        assertBoundsContained(
            label = "fully visible ETC option",
            inner = etcOption.fetchSemanticsNode().boundsInRoot,
            outer = scrolledMenuBounds,
        )
        assertBoundsContained(
            label = "ETC option inside root",
            inner = etcOption.fetchSemanticsNode().boundsInRoot,
            outer = rootBounds,
        )
        etcOption.performClick()

        assertEquals(listOf(StationListAction.BrandFilterSelected(BrandFilter.ETC)), actions)
    }

    @Test
    fun `nearby comparison chrome shows filters summary and flat rows`() {
        val stations = listOf(
            testStation(),
            testStation().copy(id = "station-2", priceWon = 1_699, priceNumberLabel = "1,699"),
            testStation().copy(
                id = "station-3",
                brand = Brand.RTO,
                brandLabel = "자영알뜰",
                priceWon = 1_709,
                priceNumberLabel = "1,709",
            ),
        )

        composeRule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                StationListScreen(
                    uiState = StationListUiState(
                        permissionState = LocationPermissionState.PreciseGranted,
                        stations = stations,
                        preferences = UserPreferences.default(),
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onAction = {},
                    onPermissionAction = {},
                    onOpenLocationSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("주변 주유소").assertExists()
        composeRule.onNodeWithContentDescription("새로고침").assertExists()
        composeRule.onAllNodesWithContentDescription("북마크").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("설정").assertCountEquals(0)
        composeRule.onNodeWithTag("station-list-filter-rail").assertExists()
        composeRule.onNodeWithTag("station-list-decision-summary").assertExists()
        composeRule.onAllNodesWithTag("station-list-row", useUnmergedTree = true).assertCountEquals(3)
        composeRule.onNodeWithContentDescription("자영알뜰 브랜드").assertExists()
    }

    @Test
    fun `results transition to preference failure keeps outgoing result snapshot safe`() {
        var uiState by mutableStateOf(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                stations = listOf(testStation()),
                preferences = UserPreferences.default(),
            ),
        )

        composeRule.setContent {
            StationListScreen(
                uiState = uiState,
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }
        composeRule.onNodeWithTag(STATION_LIST_FILTER_RAIL_TAG).assertExists()

        composeRule.runOnUiThread {
            uiState = uiState.copy(
                preferences = null,
                preferenceLoadFailed = true,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("설정을 불러오지 못했습니다.").assertExists()
    }

    @Test
    fun `decision summary visibly renders count lowest average and savings`() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                StationListDecisionSummaryStrip(
                    summary = decisionSummary(),
                )
            }
        }

        composeRule.onNodeWithTag(STATION_LIST_DECISION_LOWEST_TAG).assertTextEquals("최저 1,968원")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_COUNT_TAG).assertTextEquals("36곳")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_AVERAGE_TAG).assertTextEquals("평균 2,070원")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_SAVINGS_TAG).assertTextEquals("102원 저렴")
    }

    @Test
    fun `decision summary applies tabular numbers to every numeric metric`() {
        composeRule.setContent {
            StationListScreen(
                uiState = comparisonUiState(),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        listOf(
            STATION_LIST_DECISION_COUNT_TAG,
            STATION_LIST_DECISION_LOWEST_TAG,
            STATION_LIST_DECISION_AVERAGE_TAG,
            STATION_LIST_DECISION_SAVINGS_TAG,
        ).forEach { tag ->
            val textLayoutResults = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    action(textLayoutResults)
                }
            assertEquals(
                "Expected $tag to use tabular numbers.",
                "tnum",
                textLayoutResults.single().layoutInput.style.fontFeatureSettings,
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-xhdpi")
    fun `decision summary localizes average copy in English`() {
        composeRule.setContent {
            StationListDecisionSummaryStrip(
                summary = decisionSummary(),
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_DECISION_LOWEST_TAG).assertTextEquals("Lowest 1,968원")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_COUNT_TAG).assertTextEquals("36 stations")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_AVERAGE_TAG).assertTextEquals("Average 2,070원")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_SAVINGS_TAG).assertTextEquals("102원 below average")
    }

    @Test
    fun `decision summary remains within 320dp bounds at two times font scale`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                Box(modifier = Modifier.size(width = 320.dp, height = 800.dp)) {
                    StationListDecisionSummaryStrip(
                        summary = decisionSummary(),
                    )
                }
            }
        }

        val stripBounds = composeRule
            .onNodeWithTag(STATION_LIST_DECISION_SUMMARY_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        listOf(
            STATION_LIST_DECISION_COUNT_TAG,
            STATION_LIST_DECISION_LOWEST_TAG,
            STATION_LIST_DECISION_AVERAGE_TAG,
            STATION_LIST_DECISION_SAVINGS_TAG,
        ).forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue("Expected $tag to start inside the summary strip.", bounds.left >= stripBounds.left)
            assertTrue("Expected $tag to end inside the summary strip.", bounds.right <= stripBounds.right)
            assertTrue("Expected $tag to start below the summary top.", bounds.top >= stripBounds.top)
            assertTrue("Expected $tag to end above the summary bottom.", bounds.bottom <= stripBounds.bottom)
        }
    }

    @Test
    fun `single station summary omits average and savings`() {
        composeRule.setContent {
            StationListDecisionSummaryStrip(
                summary = StationListDecisionSummary(
                    count = 1,
                    lowestPriceWon = 1_968,
                    averagePriceWon = null,
                    savingsWon = null,
                    isLowestPriceTied = false,
                ),
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_DECISION_COUNT_TAG).assertTextEquals("1곳")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_LOWEST_TAG).assertTextEquals("최저 1,968원")
        composeRule.onNodeWithTag(STATION_LIST_DECISION_AVERAGE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(STATION_LIST_DECISION_SAVINGS_TAG).assertDoesNotExist()
    }

    @Test
    fun `tied lowest decision summary identifies the shared lowest price`() {
        composeRule.setContent {
            StationListDecisionSummaryStrip(
                summary = decisionSummary().copy(isLowestPriceTied = true),
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_DECISION_LOWEST_TAG).assertTextEquals("공동 최저 1,968원")
    }

    @Test
    fun `initial loading renders three flat skeleton rows`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onAllNodesWithTag("station-list-skeleton-row", useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun `query context shows current address only through dong and condition without old card copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    currentAddressLabel = "서울 영등포구 당산동",
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG).assertExists()
        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG).assertExists()
        composeRule.onNodeWithText("서울 영등포구 당산동").assertExists()
        composeRule.onNodeWithText("3km · 휘발유 기준").assertExists()
        composeRule.onNodeWithText("현재 조건").assertDoesNotExist()
        composeRule.onNodeWithText("반경과 유종 기준으로 정렬합니다.").assertDoesNotExist()
    }

    @Test
    fun `query context does not treat building dong as administrative dong`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    currentAddressLabel = "서울특별시 강남구 역삼동",
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("서울특별시 강남구 역삼동").assertExists()
    }

    @Test
    fun `query context shows condition when current address is unavailable`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    currentAddressLabel = null,
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG).assertExists()
        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("3km · 휘발유 기준").assertExists()
        composeRule.onNodeWithText("현재 조건").assertDoesNotExist()
        composeRule.onNodeWithText("반경과 유종 기준으로 정렬합니다.").assertDoesNotExist()
    }

    @Test
    fun `station card surfaces price above station name on the reference screen`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Unchanged,
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        val priceTop = composeRule
            .onNodeWithTag(STATION_LIST_METRIC_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val titleTop = composeRule
            .onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top

        assertTrue(
            "Expected the price hero to appear above the station title (priceTop=$priceTop, titleTop=$titleTop)",
            priceTop < titleTop,
        )
    }

    @Test
    fun `station cards remove price labels and describe every price history state`() {
        val station = testStation()
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        station.copy(id = "unavailable", priceHistory = StationListPriceHistoryUiModel.Unavailable),
                        station.copy(id = "unchanged", priceHistory = StationListPriceHistoryUiModel.Unchanged),
                        station.copy(id = "increased", priceHistory = StationListPriceHistoryUiModel.Increased(20)),
                        station.copy(id = "decreased", priceHistory = StationListPriceHistoryUiModel.Decreased(30)),
                    ),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onAllNodesWithText("가격", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("가격 이력 없음", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("변동 없음", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("▲ 20원", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("▼ 30원", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-xhdpi")
    fun `station cards localize every price history state in English`() {
        val station = testStation().copy(priceUnitLabel = "₩")
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        station.copy(id = "unavailable", priceHistory = StationListPriceHistoryUiModel.Unavailable),
                        station.copy(id = "unchanged", priceHistory = StationListPriceHistoryUiModel.Unchanged),
                        station.copy(id = "increased", priceHistory = StationListPriceHistoryUiModel.Increased(20)),
                        station.copy(id = "decreased", priceHistory = StationListPriceHistoryUiModel.Decreased(30)),
                    ),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onAllNodesWithText("Price", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("No price history", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("No change", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("▲ 20 won", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("▼ 30 won", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `station row keeps distance in the trailing comparison column`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Unchanged,
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        val priceBounds = composeRule
            .onNodeWithTag(STATION_LIST_METRIC_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val distanceBounds = composeRule
            .onNodeWithText("0.3km", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected trailing distance to sit to the right of the price hero.",
            distanceBounds.left > priceBounds.left,
        )
    }

    @Test
    fun `station card places price comparison to the right of fuel and brand icon row`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Decreased(17),
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        val brandIconRight = composeRule
            .onNodeWithContentDescription("GS칼텍스 브랜드", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.right
        val fuelChipBounds = composeRule
            .onNodeWithTag(STATION_LIST_FUEL_CHIP_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val priceComparisonBounds = composeRule
            .onNodeWithTag(STATION_LIST_PRICE_CHANGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected price comparison text to appear to the right of brand icon row.",
            priceComparisonBounds.left > brandIconRight,
        )
        assertEquals(
            "Expected fuel and price history to share the default-font metadata row.",
            fuelChipBounds.top,
            priceComparisonBounds.top,
            0.1f,
        )
    }

    @Test
    fun `station card wraps only metadata without displacing price or name at large font`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(modifier = Modifier.size(width = 320.dp, height = 720.dp)) {
                    StationListScreen(
                        uiState = StationListUiState(
                            permissionState = LocationPermissionState.PreciseGranted,
                            stations = listOf(
                                StationListItemUiModel(
                                    id = "station-1",
                                    name = "서울특별시강남구테헤란로초장문테스트주유소직영점",
                                    brand = Brand.HDO,
                                    brandLabel = "현대오일뱅크",
                                    priceWon = 123_456_789,
                                    priceLabel = "123,456,789원",
                                    distanceLabel = "123.4km",
                                    priceNumberLabel = "123,456,789",
                                    priceUnitLabel = "원",
                                    distanceNumberLabel = "123.4",
                                    distanceUnitLabel = "km",
                                    priceHistory = StationListPriceHistoryUiModel.Decreased(999),
                                    isWatched = false,
                                    latitude = 37.498095,
                                    longitude = 127.02761,
                                ),
                            ),
                            preferences = UserPreferences.default().copy(fuelType = FuelType.PREMIUM_GASOLINE),
                        ),
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        onAction = {},
                        onPermissionAction = {},
                        onOpenLocationSettings = {},
                    )
                }
            }
        }

        val rootRight = composeRule.onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .right
        val metricBounds = composeRule
            .onNodeWithTag(STATION_LIST_METRIC_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val fuelChipBounds = composeRule
            .onNodeWithTag(STATION_LIST_FUEL_CHIP_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val brandIconBounds = composeRule
            .onNodeWithContentDescription("현대오일뱅크 브랜드", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val priceComparisonBounds = composeRule
            .onNodeWithTag(STATION_LIST_PRICE_CHANGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("Expected metric row to stay inside the narrow screen.", metricBounds.right <= rootRight)
        assertTrue("Expected station title to stay inside the narrow screen.", titleBounds.right <= rootRight)
        assertTrue("Expected brand logo tile to stay inside the narrow screen.", brandIconBounds.right <= rootRight)
        assertTrue("Expected fuel chip to stay inside the narrow screen.", fuelChipBounds.right <= rootRight)
        assertTrue("Expected price delta to stay inside the narrow screen.", priceComparisonBounds.right <= rootRight)
        assertTrue("Expected price hero to remain above the station title.", metricBounds.bottom <= titleBounds.top)
        assertTrue("Expected station title to remain above metadata.", titleBounds.bottom <= fuelChipBounds.top)
        assertTrue(
            "Expected only price history to wrap below fuel metadata " +
                "(fuel=$fuelChipBounds, history=$priceComparisonBounds).",
            fuelChipBounds.bottom <= priceComparisonBounds.top,
        )
    }

    @Test
    fun `station card renders brand icon without visible brand label`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Unavailable,
                            isWatched = false,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithContentDescription("GS칼텍스 브랜드").assertExists()
        composeRule.onNodeWithText("GS칼텍스").assertDoesNotExist()
    }

    @Test
    fun `loading keeps rendered station list visible while showing top refresh rail`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Unchanged,
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("가격 갱신 중").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `cached refresh query context begins below the rail`() {
        setCachedRefreshContent(fontScale = 1f)

        assertQueryContextBeginsBelowRefreshRail()
    }

    @Test
    fun `cached refresh query context begins below the rail at two times font scale`() {
        setCachedRefreshContent(fontScale = 2f)

        assertQueryContextBeginsBelowRefreshRail()
    }

    @Test
    fun `refreshing keeps rendered station list visible without overlay loading card`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isRefreshing = true,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Unchanged,
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("가격 갱신 중").assertExists()
        composeRule.onNodeWithText("현재 조건으로 최신 가격을 확인하고 있습니다.").assertExists()
        composeRule.onNodeWithText("새로고침 중입니다.").assertDoesNotExist()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `refreshing state wins over loading overlay when cached results are visible`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    isRefreshing = true,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceWon = 1_689,
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceHistory = StationListPriceHistoryUiModel.Unchanged,
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("가격 갱신 중").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `blocking failure renders retryable failure card instead of empty results copy`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.LocationTimedOut,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("위치를 확인하는 데 시간이 오래 걸리고 있습니다.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(listOf(StationListAction.RetryClicked), actions)
    }

    @Test
    fun `cached results stay visible when blocking failure is null`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("위치를 확인하는 데 시간이 오래 걸리고 있습니다.").assertDoesNotExist()
    }

    @Test
    fun `cached results stay visible when blocking failure exists`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.RefreshFailed,
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("테스트 주유소").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertDoesNotExist()
        composeRule.onNodeWithText("네트워크 또는 서버 상태를 확인한 뒤 다시 시도해주세요.").assertDoesNotExist()
    }

    @Test
    fun `stale and approximate banners render as guidance without hiding cached results`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.ApproximateGranted,
                    isStale = true,
                    lastUpdatedAt = Instant.parse("2026-04-18T00:30:00Z"),
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("대략적인 위치 기준입니다.").assertExists()
        composeRule.onNodeWithText("정확한 거리 비교가 필요하면 위치 권한을 정확도로 바꿔주세요.").assertExists()
        composeRule.onNodeWithText("저장된 결과를 표시 중입니다.").assertExists()
        composeRule.onNodeWithText("테스트 주유소").assertExists()
    }

    @Test
    fun `location failure shows generic location failure copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.LocationFailed,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("현재 위치를 확인하지 못했습니다.").assertExists()
        composeRule.onNodeWithText("위치 권한과 위치 서비스 상태를 확인한 뒤 다시 시도해주세요.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
    }

    @Test
    fun `denied permission with stale coordinates still shows permission required state`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    currentCoordinates = com.gasstation.core.model.Coordinates(37.498095, 127.02761),
                    permissionState = LocationPermissionState.Denied,
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("위치 권한이 필요합니다.").assertExists()
        composeRule.onNodeWithText("테스트 주유소").assertDoesNotExist()
    }

    @Test
    fun `앱 설정에서 허용 action opens app settings callback`() {
        var appSettingsOpened = false

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.Denied,
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                permissionAction = PermissionAction.OpenAppSettings,
                onAction = {},
                onPermissionAction = { appSettingsOpened = true },
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("앱 설정에서 허용").performClick()

        assertTrue(appSettingsOpened)
    }

    @Test
    fun `permission request waits for explicit action across recomposition`() {
        var isLoading by mutableStateOf(false)
        var permissionRequestCount = 0

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.Denied,
                    isLoading = isLoading,
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                permissionAction = PermissionAction.Request,
                onAction = {},
                onPermissionAction = { permissionRequestCount += 1 },
                onOpenLocationSettings = {},
            )
        }

        composeRule.runOnIdle { isLoading = true }
        composeRule.waitForIdle()

        assertEquals(0, permissionRequestCount)

        composeRule.onNodeWithText("권한 요청").performClick()

        assertEquals(1, permissionRequestCount)
    }

    @Test
    fun `permission required state wins over initial loading guidance`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.Denied,
                    isLoading = true,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("위치 권한이 필요합니다.").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `denied permission without bypass shows permission required instead of stale failure`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.Denied,
                    blockingFailure = StationListFailureReason.LocationFailed,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("위치 권한이 필요합니다.").assertExists()
        composeRule.onNodeWithText(
            "주변 주유소를 찾고 거리순과 가격순 정렬을 사용하려면 위치 접근을 허용해주세요.",
        ).assertExists()
        composeRule.onNodeWithText("현재 위치를 확인하지 못했습니다.").assertDoesNotExist()
    }

    @Test
    fun `gps required state renders location settings guidance and opens settings`() {
        var locationSettingsOpened = false

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isGpsEnabled = false,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = { locationSettingsOpened = true },
            )
        }

        composeRule.onNodeWithText("위치 서비스를 켜야 합니다.").assertExists()
        composeRule.onNodeWithText(
            "GPS 또는 네트워크 위치를 활성화해야 주변 주유소와 북마크를 정확하게 불러올 수 있습니다.",
        ).assertExists()
        composeRule.onNodeWithText("위치 설정 열기").performClick()

        assertTrue(locationSettingsOpened)
    }

    @Test
    fun `gps required state wins over initial loading guidance`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isGpsEnabled = false,
                    isLoading = true,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("위치 서비스를 켜야 합니다.").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `refresh timeout shows slow server failure copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.RefreshTimedOut,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertExists()
        composeRule.onNodeWithText("서버 응답이 늦습니다. 잠시 후 같은 조건으로 다시 시도해주세요.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
    }

    @Test
    fun `refresh failure shows generic network failure copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.RefreshFailed,
                    preferences = UserPreferences.default().copy(fuelType = FuelType.DIESEL),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertExists()
        composeRule.onNodeWithText("네트워크 또는 서버 상태를 확인한 뒤 다시 시도해주세요.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
    }

    @Test
    fun `top bar exposes only the refresh action`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithContentDescription("새로고침").assertExists()
        composeRule.onAllNodesWithContentDescription("북마크").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("설정").assertCountEquals(0)
    }

    @Test
    fun `station list exposes stable benchmark tags without changing copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onAllNodesWithContentDescription("북마크").assertCountEquals(0)
        composeRule.onAllNodesWithTag(STATION_LIST_WATCHLIST_ACTION_TAG, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("저장").assertExists()
        composeRule.onNodeWithTag(STATION_LIST_WATCH_TOGGLE_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `empty results state renders empty guidance and retry action`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithText("조건에 맞는 주변 주유소가 없습니다.").assertExists()
        composeRule.onNodeWithText("반경, 유종, 브랜드 조건을 조정하거나 다시 조회해보세요.").assertExists()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(listOf(StationListAction.RetryClicked), actions)
    }

    @Test
    fun `pull to refresh on populated results requests refresh`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_PULL_REFRESH_TAG, useUnmergedTree = true)
            .performTouchInput { swipeDown() }

        assertEquals(listOf(StationListAction.RefreshRequested), actions)
        composeRule.onNodeWithTag(STATION_LIST_ROOT_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `pull to refresh on empty results requests refresh`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onPermissionAction = {},
                onOpenLocationSettings = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_PULL_REFRESH_TAG, useUnmergedTree = true)
            .performTouchInput { swipeDown() }

        assertEquals(listOf(StationListAction.RefreshRequested), actions)
    }

    @Test
    fun `first content callback waits during initial loading`() {
        var callbackCount = 0

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    stations = emptyList(),
                    preferences = UserPreferences.default(),
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
                onFirstContentDrawn = { callbackCount += 1 },
            )
        }

        composeRule.waitForIdle()

        assertEquals(0, callbackCount)
    }

    @Test
    fun `first content callback fires once after usable station content appears`() {
        var callbackCount = 0
        var uiState by mutableStateOf(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = emptyList(),
                preferences = UserPreferences.default(),
            ),
        )

        composeRule.setContent {
            StationListScreen(
                uiState = uiState,
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onPermissionAction = {},
                onOpenLocationSettings = {},
                onFirstContentDrawn = { callbackCount += 1 },
            )
        }

        composeRule.waitForIdle()
        assertEquals(0, callbackCount)

        composeRule.runOnUiThread {
            uiState = uiState.copy(
                isLoading = false,
                stations = listOf(testStation()),
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            uiState = uiState.copy(isRefreshing = true)
        }
        composeRule.waitForIdle()

        assertEquals(1, callbackCount)
    }

    private fun testStation() = StationListItemUiModel(
        id = "station-1",
        name = "테스트 주유소",
        brand = Brand.GSC,
        brandLabel = "GS칼텍스",
        priceWon = 1_689,
        priceLabel = "1,689원",
        distanceLabel = "0.3km",
        priceNumberLabel = "1,689",
        priceUnitLabel = "원",
        distanceNumberLabel = "0.3",
        distanceUnitLabel = "km",
        priceHistory = StationListPriceHistoryUiModel.Unchanged,
        isWatched = true,
        latitude = 37.498095,
        longitude = 127.02761,
    )

    private fun comparisonUiState() = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        preferences = UserPreferences.default(),
        stations = listOf(
            testStation(),
            testStation().copy(id = "station-2", priceWon = 1_699, priceNumberLabel = "1,699"),
            testStation().copy(id = "station-3", priceWon = 1_701, priceNumberLabel = "1,701"),
        ),
    )

    private fun decisionSummary() = StationListDecisionSummary(
        count = 36,
        lowestPriceWon = 1_968,
        averagePriceWon = 2_070,
        savingsWon = 102,
        isLowestPriceTied = false,
    )

    private fun setFilterContent(
        actions: MutableList<StationListAction> = mutableListOf(),
        width: androidx.compose.ui.unit.Dp = 360.dp,
        height: androidx.compose.ui.unit.Dp = 800.dp,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(modifier = Modifier.size(width = width, height = height)) {
                    StationListScreen(
                        uiState = StationListUiState(
                            permissionState = LocationPermissionState.PreciseGranted,
                            preferences = UserPreferences.default(),
                            stations = listOf(testStation()),
                        ),
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        onAction = actions::add,
                        onPermissionAction = {},
                        onOpenLocationSettings = {},
                    )
                }
            }
        }
    }

    private fun filterOptionTextNode(brandFilter: BrandFilter, label: String): SemanticsNodeInteraction = composeRule.onNode(
        matcher = hasText(label) and hasAnyAncestor(
            hasTestTag("$STATION_LIST_FILTER_OPTION_TAG_PREFIX${brandFilter.name}"),
        ),
        useUnmergedTree = true,
    )

    private fun assertTextFitsHorizontally(
        label: String,
        node: SemanticsNodeInteraction,
        menuBounds: androidx.compose.ui.geometry.Rect,
        rootBounds: androidx.compose.ui.geometry.Rect,
    ) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertHorizontallyContained(label, bounds, menuBounds)
        assertHorizontallyContained("$label inside root", bounds, rootBounds)

        val textLayoutResults = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(textLayoutResults)
        }
        val layoutResult = textLayoutResults.single()
        assertFalse(
            "Expected $label to render without horizontal overflow " +
                "(size=${layoutResult.size}, lines=${layoutResult.lineCount}, " +
                "constraints=${layoutResult.layoutInput.constraints}).",
            layoutResult.didOverflowWidth,
        )
    }

    private fun assertHorizontallyContained(
        label: String,
        inner: androidx.compose.ui.geometry.Rect,
        outer: androidx.compose.ui.geometry.Rect,
    ) {
        assertTrue("Expected $label left edge inside container: inner=$inner outer=$outer", inner.left >= outer.left)
        assertTrue("Expected $label right edge inside container: inner=$inner outer=$outer", inner.right <= outer.right)
    }

    private fun assertBoundsContained(label: String, inner: androidx.compose.ui.geometry.Rect, outer: androidx.compose.ui.geometry.Rect) {
        assertHorizontallyContained(label, inner, outer)
        assertTrue("Expected $label top edge inside container: inner=$inner outer=$outer", inner.top >= outer.top)
        assertTrue("Expected $label bottom edge inside container: inner=$inner outer=$outer", inner.bottom <= outer.bottom)
    }

    private fun assertMinimumTouchHeight(tag: String) {
        val height = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val minimumHeight = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("Expected $tag to be at least 48dp tall.", height >= minimumHeight)
    }

    private fun assertFilterOptionText(testKey: String, expected: String) {
        val text = composeRule
            .onNodeWithTag("$STATION_LIST_FILTER_OPTION_TAG_PREFIX$testKey")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
        assertEquals(expected, text)
    }

    private fun filterFuelLabel(fuelType: FuelType): String = when (fuelType) {
        FuelType.GASOLINE -> "휘발유"
        FuelType.DIESEL -> "경유"
        FuelType.PREMIUM_GASOLINE -> "고급휘발유"
        FuelType.KEROSENE -> "등유"
        FuelType.LPG -> "LPG"
    }

    private fun setCachedRefreshContent(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                StationListScreen(
                    uiState = comparisonUiState().copy(
                        currentAddressLabel = "서울특별시 강남구 역삼동",
                        isRefreshing = true,
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onAction = {},
                    onPermissionAction = {},
                    onOpenLocationSettings = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertQueryContextBeginsBelowRefreshRail() {
        val railBounds = composeRule
            .onNodeWithTag(STATION_LIST_REFRESH_RAIL_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val queryBounds = composeRule
            .onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected query context to begin below the refresh rail " +
                "(railBottom=${railBounds.bottom}, queryTop=${queryBounds.top}).",
            queryBounds.top >= railBounds.bottom,
        )
    }
}
