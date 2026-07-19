package com.gasstation.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.component.GasStationBrandLogoTile
import com.gasstation.core.designsystem.component.GasStationComparisonRow
import com.gasstation.core.designsystem.component.GasStationGuidanceCard
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
import com.gasstation.core.designsystem.component.GasStationNavigationBar
import com.gasstation.core.designsystem.component.GasStationNavigationBarItem
import com.gasstation.core.designsystem.component.GasStationRow
import com.gasstation.core.designsystem.component.GasStationStatusBanner
import com.gasstation.core.designsystem.component.GasStationStatusTone
import com.gasstation.core.designsystem.component.GasStationSummaryStrip
import com.gasstation.core.designsystem.component.GasStationSupportingInfo
import com.gasstation.core.designsystem.component.UrbanSignalTokens
import com.gasstation.core.model.Brand
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziDesignSystemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runner_initializes() {
        // TDD gate: confirm Robolectric + Compose rule wire up before adding captures
    }

    @Test
    fun metric_block_primary_emphasis_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationMetricBlock(
                        label = "리터당",
                        number = "1,712",
                        unit = "원",
                        emphasis = GasStationMetricEmphasis.Primary,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/metric-block-price.png")
    }

    @Test
    fun status_banner_warning_tone_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationStatusBanner(
                        text = "데이터가 오래되었습니다",
                        detail = "05.11 09:30",
                        tone = GasStationStatusTone.Warning,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/status-banner-warning.png")
    }

    @Test
    fun guidance_card_with_action_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationGuidanceCard(
                        title = "위치 권한이 필요합니다",
                        body = "주변 주유소를 찾으려면 위치 권한을 허용해 주세요.",
                        actionLabel = "권한 허용",
                        onAction = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/guidance-card-with-action.png")
    }

    @Test
    fun row_with_body_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationRow(
                        title = "SK에너지",
                        value = "1,712원",
                        body = "서울 강남구 역삼동",
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/row-with-body.png")
    }

    @Test
    fun supporting_info_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationSupportingInfo(
                        label = "브랜드",
                        value = "SK에너지",
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/supporting-info.png")
    }

    @Test
    fun urban_signal_summary_strip_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationSummaryStrip(Modifier.fillMaxWidth()) {
                        Text(
                            text = "최저가",
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = "1,689원")
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/urban-signal-summary-strip.png")
    }

    @Test
    fun urban_signal_main_brand_tile_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationBrandLogoTile(
                        brand = Brand.SKE,
                        contentDescription = "SK에너지",
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/urban-signal-main-brand-tile.png")
    }

    @Test
    fun urban_signal_compact_brand_tile_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(16.dp)) {
                    GasStationBrandLogoTile(
                        brand = Brand.GSC,
                        contentDescription = "GS칼텍스",
                        tileSize = UrbanSignalTokens.compactLogoTileSize,
                        logoSize = UrbanSignalTokens.compactLogoSize,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/urban-signal-compact-brand-tile.png")
    }

    @Test
    fun urban_signal_comparison_row_renders() {
        composeRule.setContent {
            GasStationTheme {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GasStationComparisonRow(
                        leading = {
                            GasStationBrandLogoTile(
                                brand = Brand.SOL,
                                contentDescription = "S-OIL",
                                tileSize = UrbanSignalTokens.compactLogoTileSize,
                                logoSize = UrbanSignalTokens.compactLogoSize,
                            )
                        },
                        primary = {
                            Text(text = "도심 셀프주유소")
                            Text(text = "0.3km")
                        },
                        trailing = {
                            Text(text = "1,689원")
                        },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/urban-signal-comparison-row.png")
    }

    @Test
    fun urban_signal_navigation_bar_renders() {
        composeRule.setContent {
            GasStationTheme {
                Column {
                    GasStationNavigationBar(Modifier.fillMaxWidth()) {
                        GasStationNavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { Text(text = "₩") },
                        )
                        GasStationNavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { Text(text = "★") },
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/urban-signal-navigation-bar.png")
    }
}
