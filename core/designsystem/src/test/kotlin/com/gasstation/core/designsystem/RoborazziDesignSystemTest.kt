package com.gasstation.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
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
}
