package com.gasstation.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.GasStationTheme

enum class GasStationRowSlot {
    Leading,
    TitleLine,
    Body,
    Trailing,
}

data class GasStationRowContent(
    val title: String,
    val value: String? = null,
    val body: String? = null,
    val hasLeadingContent: Boolean = false,
    val hasTrailingContent: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Row title is required." }
        require(value == null || value.isNotBlank()) { "Row value must not be blank when provided." }
        require(body == null || body.isNotBlank()) { "Row body must not be blank when provided." }
    }

    val titleLine: String = if (value != null) {
        "$title : $value"
    } else {
        title
    }

    fun orderedSlots(): List<GasStationRowSlot> = buildList {
        if (hasLeadingContent) add(GasStationRowSlot.Leading)
        add(GasStationRowSlot.TitleLine)
        if (body != null) add(GasStationRowSlot.Body)
        if (hasTrailingContent) add(GasStationRowSlot.Trailing)
    }

    fun orderedTextSlots(): List<TextSlotRole> = buildList {
        add(
            TextSlotRole(
                slot = StructuredTextSlot.Title,
                role = ChromeTextRole.CardTitle,
            ),
        )
        if (body != null) {
            add(
                TextSlotRole(
                    slot = StructuredTextSlot.Body,
                    role = ChromeTextRole.Body,
                ),
            )
        }
    }
}

@Composable
fun GasStationRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    body: String? = null,
    titleColor: Color = ColorBlack,
    bodyColor: Color = ColorGray2,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val content = GasStationRowContent(
        title = title,
        value = value,
        body = body,
        hasLeadingContent = leadingContent != null,
        hasTrailingContent = trailingContent != null,
    )
    val spacing = GasStationTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Text(
                text = content.titleLine,
                style = ChromeTextRole.CardTitle.style(),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content.body?.let { bodyText ->
                Text(
                    text = bodyText,
                    style = ChromeTextRole.Body.style(),
                    color = bodyColor,
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun GasStationRowDivider(
    modifier: Modifier = Modifier,
    color: Color = ColorGray,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}
