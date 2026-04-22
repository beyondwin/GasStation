package com.gasstation.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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

enum class SupportingInfoSlot {
    Label,
    Value,
    Trailing,
}

data class SupportingInfoSlotRole(
    val slot: SupportingInfoSlot,
    val role: ChromeTextRole,
)

data class SupportingInfoContent(
    val label: String,
    val value: String,
    val hasTrailingContent: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "Supporting info label is required." }
        require(value.isNotBlank()) { "Supporting info value is required." }
    }

    fun orderedSlots(): List<SupportingInfoSlotRole> = buildList {
        add(
            SupportingInfoSlotRole(
                slot = SupportingInfoSlot.Label,
                role = ChromeTextRole.Meta,
            ),
        )
        add(
            SupportingInfoSlotRole(
                slot = SupportingInfoSlot.Value,
                role = ChromeTextRole.Body,
            ),
        )
        if (hasTrailingContent) {
            add(
                SupportingInfoSlotRole(
                    slot = SupportingInfoSlot.Trailing,
                    role = ChromeTextRole.Body,
                ),
            )
        }
    }
}

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = number,
                modifier = Modifier.weight(1f, fill = false),
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

@Composable
fun GasStationSupportingInfo(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
    labelColor: Color = ColorBlack,
    valueColor: Color = ColorBlack,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val content = SupportingInfoContent(
        label = label,
        value = value,
        hasTrailingContent = trailingContent != null,
    )
    val spacing = GasStationTheme.spacing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text = content.label,
            style = ChromeTextRole.Meta.style(),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingContent == null) {
            Text(
                text = content.value,
                modifier = valueModifier,
                style = ChromeTextRole.Body.style(),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = content.value,
                    modifier = valueModifier.weight(1f, fill = false),
                    style = ChromeTextRole.Body.style(),
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailingContent()
            }
        }
    }
}
