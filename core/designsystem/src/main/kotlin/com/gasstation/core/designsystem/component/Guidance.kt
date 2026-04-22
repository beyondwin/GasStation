package com.gasstation.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme

enum class GuidanceCardSlot {
    Leading,
    Title,
    Body,
    Action,
}

data class GuidanceCardContent(
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val hasLeadingContent: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Guidance card title is required." }
        require(body.isNotBlank()) { "Guidance card body is required." }
        require(actionLabel == null || actionLabel.isNotBlank()) {
            "Guidance card action label must not be blank when provided."
        }
    }

    fun orderedSlots(): List<GuidanceCardSlot> = buildList {
        if (hasLeadingContent) add(GuidanceCardSlot.Leading)
        add(GuidanceCardSlot.Title)
        add(GuidanceCardSlot.Body)
        if (actionLabel != null) add(GuidanceCardSlot.Action)
    }

    fun orderedTextSlots(): List<TextSlotRole> = listOf(
        TextSlotRole(
            slot = StructuredTextSlot.Title,
            role = ChromeTextRole.SectionTitle,
        ),
        TextSlotRole(
            slot = StructuredTextSlot.Body,
            role = ChromeTextRole.Body,
        ),
    )
}

@Composable
fun GasStationGuidanceCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    require((actionLabel == null) == (onAction == null)) {
        "Guidance card action label and callback must be provided together."
    }

    val content = GuidanceCardContent(
        title = title,
        body = body,
        actionLabel = actionLabel,
        hasLeadingContent = leadingContent != null,
    )
    val spacing = GasStationTheme.spacing

    GasStationCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space16,
        ),
    ) {
        if (leadingContent == null) {
            GasStationSectionHeading(
                title = content.title,
                subtitle = content.body,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingContent()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    Text(
                        text = content.title,
                        style = ChromeTextRole.SectionTitle.style(),
                        color = ColorBlack,
                    )
                    Text(
                        text = content.body,
                        style = ChromeTextRole.Body.style(),
                        color = ColorGray2,
                    )
                }
            }
        }
        if (content.actionLabel != null && onAction != null) {
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorBlack,
                    contentColor = ColorYellow,
                ),
                onClick = onAction,
            ) {
                Text(
                    text = content.actionLabel,
                    modifier = Modifier.padding(horizontal = spacing.space4),
                    style = ChromeTextRole.Body.style(),
                )
            }
        }
    }
}
