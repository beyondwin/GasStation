package com.gasstation.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.GasStationTheme

enum class GasStationMetricEmphasis(
    val numberRole: ChromeTextRole,
    val unitBottomPadding: Dp,
) {
    Primary(
        numberRole = ChromeTextRole.PriceHero,
        unitBottomPadding = 4.dp,
    ),
    Secondary(
        numberRole = ChromeTextRole.MetricValue,
        unitBottomPadding = 3.dp,
    ),
}

@Composable
fun GasStationMetricBlock(
    label: String,
    number: String,
    unit: String,
    emphasis: GasStationMetricEmphasis,
    modifier: Modifier = Modifier,
    labelColor: Color = ColorGray3,
    numberColor: Color = ColorBlack,
    unitColor: Color = ColorGray2,
) {
    val spacing = GasStationTheme.spacing
    val metaStyle = ChromeTextRole.Meta.style()
    val numberStyle = emphasis.numberRole.style()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = metaStyle,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                style = numberStyle,
                color = numberColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(
                    start = spacing.space4,
                    bottom = emphasis.unitBottomPadding,
                ),
                style = metaStyle,
                color = unitColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
