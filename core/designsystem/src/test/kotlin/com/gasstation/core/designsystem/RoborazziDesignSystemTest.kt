package com.gasstation.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.component.GasStationGuidanceCard
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
import com.gasstation.core.designsystem.component.GasStationRow
import com.gasstation.core.designsystem.component.GasStationStatusBanner
import com.gasstation.core.designsystem.component.GasStationStatusTone
import com.gasstation.core.designsystem.component.GasStationSupportingInfo
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
}
