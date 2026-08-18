package com.gasstation.feature.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.designsystem.gasStationPriceDigits
import com.gasstation.core.designsystem.gasStationPriceLabel
import com.gasstation.core.model.Brand
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MoneyWon
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
class WatchlistScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `five saved stations render as complete 108 to 116dp rows at default scale`() {
        renderFiveRows()

        val rowNodes = composeRule
            .onAllNodesWithTag(WATCHLIST_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(5, rowNodes.size)

        val rowHeights = rowNodes.map { node ->
            with(composeRule.density) { node.boundsInRoot.height.toDp() }
        }
        assertTrue("Expected every dense row in 108..116dp, got $rowHeights", rowHeights.all { it in 108.dp..116.dp })

        val viewportBottom = with(composeRule.density) { 800.dp.toPx() }
        assertTrue(
            "Expected all five rows complete inside 800dp, bottom=${rowNodes.last().boundsInRoot.bottom}",
            rowNodes.last().boundsInRoot.bottom <= viewportBottom,
        )
    }

    @Test
    fun `dense rows use real compact brand logos without duplicate visible labels`() {
        renderFiveRows()

        listOf("SK에너지", "GS칼텍스", "S-OIL", "자영알뜰", "자가상표").forEach { label ->
            composeRule.onNodeWithContentDescription("$label 브랜드").apply {
                assertExists()
                assertWidthIsEqualTo(34.dp)
                assertHeightIsEqualTo(34.dp)
            }
        }
        composeRule.onAllNodesWithText("자영알뜰").assertCountEquals(0)
        composeRule.onAllNodesWithText("자가상표").assertCountEquals(0)
    }

    @Test
    fun `saved comparison summary exposes count average and latest check`() {
        renderFiveRows()

        composeRule.onNodeWithText("휘발유 기준").assertExists()
        composeRule.onNodeWithText("저장한 5곳").assertExists()
        composeRule.onNodeWithText("평균 1,700원").assertExists()
        composeRule.onNodeWithText("최근 확인 7월 17일 11:00").assertExists()
    }

    @Test
    fun `price change metadata names rise fall and no change instead of relying on color`() {
        composeRule.setContent {
            GasStationTheme {
                WatchlistScreen(
                    uiState = deltaState(),
                    onAction = {},
                    onNavigateNearby = {},
                )
            }
        }

        composeRule.onNodeWithText("상승 14원 · 7월 17일 11:00").assertExists()
        composeRule.onNodeWithText("하락 27원 · 7월 17일 11:00").assertExists()
        composeRule.onNodeWithText("변동 없음 · 7월 17일 11:00").assertExists()
    }

    @Test
    fun `directional price change requires its numeric delta`() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                GasStationTheme {
                    WatchlistScreen(
                        uiState = WatchlistUiState(
                            isLoading = false,
                            fuelType = FuelType.GASOLINE,
                            stations = listOf(testItem("invalid-rise", null, WatchlistPriceDeltaTone.Rise)),
                        ),
                        onAction = {},
                        onNavigateNearby = {},
                    )
                }
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `remove action is a 48dp Korean-labelled target and dispatches station id`() {
        val actions = mutableListOf<WatchlistAction>()
        renderFiveRows(onAction = actions::add)

        composeRule.onNodeWithTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-1").apply {
            assertHeightIsAtLeast(48.dp)
            performClick()
        }
        composeRule.onAllNodesWithContentDescription("관심 주유소에서 제거").assertCountEquals(5)
        assertEquals(listOf(WatchlistAction.RemoveClicked("station-1")), actions)
    }

    @Test
    fun `top level watchlist has title only and no close refresh or location controls`() {
        renderFiveRows()

        composeRule.onNodeWithText("관심 주유소").assertExists()
        composeRule.onNodeWithContentDescription("닫기").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("새로고침").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("현재 위치").assertDoesNotExist()
    }

    @Test
    fun `two hundred percent font scale expands rows and scrolls without clipping identity price or action`() {
        val currentDensity = composeRule.density
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 2f,
                ),
            ) {
                GasStationTheme {
                    Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                        WatchlistScreen(
                            uiState = fiveRowState(),
                            onAction = {},
                            onNavigateNearby = {},
                        )
                    }
                }
            }
        }

        val firstRowHeight = composeRule
            .onAllNodesWithTag(WATCHLIST_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot.height
        assertTrue(
            "Expected accessibility row to expand beyond the default cap.",
            with(currentDensity) { firstRowHeight.toDp() > 116.dp },
        )
        composeRule.onNodeWithText("1,680").assertIsDisplayed()
        composeRule.onNodeWithText("강남 제일 주유소").assertIsDisplayed()
        composeRule.onNodeWithTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-1").assertIsDisplayed()

        composeRule.onNodeWithTag(WATCHLIST_LIST_TAG).performScrollToNode(
            hasTestTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-5"),
        )
        composeRule.onNodeWithText("1,720").assertIsDisplayed()
        composeRule.onNodeWithText("비교 주유소 5").assertIsDisplayed()
        composeRule.onNodeWithTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-5").assertIsDisplayed()
    }

    @Test
    fun `empty guidance explains saving and navigates to Nearby`() {
        var nearbyClicks = 0
        composeRule.setContent {
            GasStationTheme {
                WatchlistScreen(
                    uiState = WatchlistUiState(
                        isLoading = false,
                        fuelType = FuelType.GASOLINE,
                    ),
                    onAction = {},
                    onNavigateNearby = { nearbyClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("저장한 주유소가 없습니다.").assertExists()
        composeRule.onNodeWithText("주변 주유소 보기").performClick()
        assertEquals(1, nearbyClicks)
    }

    @Test
    fun `loading and failure states do not masquerade as an empty watchlist`() {
        val actions = mutableListOf<WatchlistAction>()
        var uiState by mutableStateOf(WatchlistUiState(isLoading = true))
        composeRule.setContent {
            GasStationTheme {
                WatchlistScreen(
                    uiState = uiState,
                    onAction = actions::add,
                    onNavigateNearby = {},
                )
            }
        }

        composeRule.onNodeWithText("관심 주유소를 불러오는 중입니다.").assertExists()
        composeRule.onNodeWithText("저장한 주유소가 없습니다.").assertDoesNotExist()

        composeRule.runOnIdle {
            uiState = WatchlistUiState(isLoading = false, loadFailed = true)
        }

        composeRule.onNodeWithText("관심 주유소를 불러오지 못했습니다.").assertExists()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(listOf(WatchlistAction.RetryLoad), actions)
    }

    @Test
    fun `unavailable selected fuel keeps identity distance and removal without won or delta`() {
        val unavailable = WatchlistItemUiModel(
            id = "station-unavailable",
            name = "저장 주유소",
            brand = Brand.GSC,
            brandLabel = Brand.GSC.gasStationBrandLabel(),
            priceWon = null,
            priceLabel = null,
            priceNumberLabel = null,
            priceUnitLabel = null,
            distanceLabel = "0.3km",
            distanceNumberLabel = "0.3",
            distanceUnitLabel = "km",
            priceDeltaWon = null,
            lastSeenAt = null,
            lastSeenLabel = "-",
            latitude = 37.49,
            longitude = 127.02,
        )
        composeRule.setContent {
            GasStationTheme {
                WatchlistScreen(
                    uiState = WatchlistUiState(
                        isLoading = false,
                        fuelType = FuelType.DIESEL,
                        stations = listOf(unavailable),
                        summary = WatchlistSummaryUiModel.from(listOf(unavailable)),
                    ),
                    onAction = {},
                    onNavigateNearby = {},
                )
            }
        }

        composeRule.onNodeWithText("경유 기준").assertExists()
        composeRule.onNodeWithText("선택 유종 가격 없음").assertExists()
        composeRule.onNodeWithText("저장 주유소").assertExists()
        composeRule.onNodeWithText("0.3km").assertExists()
        composeRule.onNodeWithTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-unavailable").assertExists()
        composeRule.onAllNodesWithText("원").assertCountEquals(0)
        composeRule.onAllNodesWithText("변동 없음 · -").assertCountEquals(0)
    }

    @Test
    fun `row tag preserves legacy watchlist card selector value`() {
        assertEquals("watchlist-card", WATCHLIST_ROW_TAG)
        assertEquals(WATCHLIST_ROW_TAG, WATCHLIST_CARD_TEST_TAG)
    }

    private fun renderFiveRows(onAction: (WatchlistAction) -> Unit = {}) {
        composeRule.setContent {
            GasStationTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    WatchlistScreen(
                        uiState = fiveRowState(),
                        onAction = onAction,
                        onNavigateNearby = {},
                    )
                }
            }
        }
    }

    private fun fiveRowState(): WatchlistUiState {
        val items = listOf(Brand.SKE, Brand.GSC, Brand.SOL, Brand.RTO, Brand.ETC)
            .mapIndexed { index, brand ->
                val price = 1_680 + index * 10
                WatchlistItemUiModel(
                    id = "station-${index + 1}",
                    name = if (index == 0) "강남 제일 주유소" else "비교 주유소 ${index + 1}",
                    brand = brand,
                    brandLabel = brand.gasStationBrandLabel(),
                    priceWon = price,
                    priceLabel = MoneyWon(price).gasStationPriceLabel(),
                    priceNumberLabel = MoneyWon(price).gasStationPriceDigits(),
                    priceUnitLabel = "원",
                    distanceLabel = "${index + 1}.0km",
                    distanceNumberLabel = "${index + 1}.0",
                    distanceUnitLabel = "km",
                    priceDeltaWon = null,
                    lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"),
                    lastSeenLabel = "7월 17일 11:00",
                    latitude = 37.49 + index * 0.001,
                    longitude = 127.02 + index * 0.001,
                )
            }
        return WatchlistUiState(
            isLoading = false,
            fuelType = FuelType.GASOLINE,
            stations = items,
            summary = WatchlistSummaryUiModel.from(items),
        )
    }

    private fun deltaState(): WatchlistUiState = WatchlistUiState(
        isLoading = false,
        fuelType = FuelType.GASOLINE,
        stations = listOf(
            testItem("rise", 14, WatchlistPriceDeltaTone.Rise),
            testItem("fall", 27, WatchlistPriceDeltaTone.Fall),
            testItem("neutral", null, WatchlistPriceDeltaTone.Neutral),
        ),
    )

    private fun testItem(id: String, deltaWon: Int?, tone: WatchlistPriceDeltaTone) = WatchlistItemUiModel(
        id = id,
        name = "테스트 주유소 $id",
        brand = Brand.GSC,
        brandLabel = Brand.GSC.gasStationBrandLabel(),
        priceWon = 1_689,
        priceLabel = "1,689원",
        priceNumberLabel = "1,689",
        priceUnitLabel = "원",
        distanceLabel = "0.3km",
        distanceNumberLabel = "0.3",
        distanceUnitLabel = "km",
        priceDeltaWon = deltaWon,
        priceDeltaTone = tone,
        lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"),
        lastSeenLabel = "7월 17일 11:00",
        latitude = 37.49,
        longitude = 127.02,
    )
}
