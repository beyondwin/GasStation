package com.gasstation.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorSurface
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationBrandIcon
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationRow
import com.gasstation.core.designsystem.component.GasStationRowDivider
import com.gasstation.core.designsystem.component.GasStationSectionHeading
import com.gasstation.core.designsystem.component.GasStationTopBar

internal const val SETTINGS_SELECTED_CHECK_TAG = "settings-selected-check"
internal const val SETTINGS_OPTIONS_GROUP_TAG = "settings-options-group"

@Composable
fun SettingsDetailScreen(
    section: SettingsSection,
    options: List<SettingOptionUiModel>,
    onBackClick: () -> Unit,
    onOptionClick: (SettingOptionUiModel) -> Unit,
) {
    GasStationBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
                    title = { Text(text = section.title) },
                    navigationIcon = {
                        SettingsDetailTopBarAction(
                            contentDescription = "뒤로가기",
                            onClick = onBackClick,
                        ) {
                            LegacyBackIcon()
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GasStationCard(
                        modifier = Modifier.testTag(SETTINGS_OPTIONS_GROUP_TAG),
                    ) {
                        GasStationSectionHeading(
                            title = section.group.title,
                            subtitle = section.subtitle,
                        )
                        options.forEachIndexed { index, option ->
                            SettingsDetailOptionRow(
                                section = section,
                                option = option,
                                onClick = { onOptionClick(option) },
                            )
                            if (index != options.lastIndex) {
                                SettingsDetailDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailOptionRow(
    section: SettingsSection,
    option: SettingOptionUiModel,
    onClick: () -> Unit,
) {
    GasStationRow(
        title = option.label,
        body = option.subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(GasStationTheme.corner.small))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics {
                selected = option.isSelected
                role = Role.RadioButton
            },
        bodyColor = ColorGray2,
        leadingContent = if (section == SettingsSection.BrandFilter) {
            { SettingsDetailBrandLeadingSlot(option = option) }
        } else {
            null
        },
        trailingContent = if (option.isSelected) {
            { SelectedCheckIcon() }
        } else {
            null
        },
    )
}

@Composable
private fun SettingsDetailBrandLeadingSlot(option: SettingOptionUiModel) {
    val brand = option.brandIconBrand
    if (brand != null) {
        GasStationBrandIcon(
            brand = brand,
            contentDescription = "${option.label} 브랜드",
        )
    } else {
        AllBrandFilterIcon()
    }
}

@Composable
private fun AllBrandFilterIcon() {
    Canvas(modifier = Modifier.size(30.dp)) {
        val iconSize = size.minDimension
        val strokeWidth = iconSize * 0.065f

        fun drawPump(
            topLeft: Offset,
            bodySize: Size,
            fill: Color,
            windowColor: Color,
        ) {
            val cornerRadius = CornerRadius(iconSize * 0.045f, iconSize * 0.045f)
            drawRoundRect(
                color = fill,
                topLeft = topLeft,
                size = bodySize,
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = ColorBlack,
                topLeft = topLeft,
                size = bodySize,
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth),
            )

            val windowInsetX = bodySize.width * 0.20f
            val windowTop = topLeft.y + bodySize.height * 0.18f
            val windowSize = Size(
                width = bodySize.width * 0.60f,
                height = bodySize.height * 0.22f,
            )
            drawRoundRect(
                color = windowColor,
                topLeft = Offset(topLeft.x + windowInsetX, windowTop),
                size = windowSize,
                cornerRadius = CornerRadius(iconSize * 0.025f, iconSize * 0.025f),
            )

            val baseY = topLeft.y + bodySize.height + iconSize * 0.045f
            drawLine(
                color = ColorBlack,
                start = Offset(topLeft.x - iconSize * 0.015f, baseY),
                end = Offset(topLeft.x + bodySize.width + iconSize * 0.015f, baseY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        drawPump(
            topLeft = Offset(iconSize * 0.10f, iconSize * 0.36f),
            bodySize = Size(iconSize * 0.24f, iconSize * 0.38f),
            fill = ColorYellow,
            windowColor = ColorBlack,
        )
        drawPump(
            topLeft = Offset(iconSize * 0.66f, iconSize * 0.36f),
            bodySize = Size(iconSize * 0.24f, iconSize * 0.38f),
            fill = ColorYellow,
            windowColor = ColorBlack,
        )
        drawPump(
            topLeft = Offset(iconSize * 0.34f, iconSize * 0.18f),
            bodySize = Size(iconSize * 0.32f, iconSize * 0.56f),
            fill = ColorSurface,
            windowColor = ColorYellow,
        )

        drawLine(
            color = ColorBlack,
            start = Offset(iconSize * 0.68f, iconSize * 0.34f),
            end = Offset(iconSize * 0.82f, iconSize * 0.44f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorBlack,
            start = Offset(iconSize * 0.82f, iconSize * 0.44f),
            end = Offset(iconSize * 0.82f, iconSize * 0.62f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SettingsDetailDivider() {
    GasStationRowDivider()
}

@Composable
private fun SettingsDetailTopBarAction(
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            icon()
        }
    }
}

@Composable
private fun LegacyBackIcon() {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 18.dp)) {
        val strokeWidth = size.minDimension * 0.16f
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.75f, y = size.height * 0.15f),
            end = center.copy(x = size.width * 0.25f, y = size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.25f, y = size.height * 0.5f),
            end = center.copy(x = size.width * 0.75f, y = size.height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SelectedCheckIcon() {
    Canvas(
        modifier = Modifier
            .testTag(SETTINGS_SELECTED_CHECK_TAG)
            .size(24.dp),
    ) {
        val strokeWidth = size.minDimension * 0.18f
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.18f, y = size.height * 0.55f),
            end = center.copy(x = size.width * 0.42f, y = size.height * 0.78f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.42f, y = size.height * 0.78f),
            end = center.copy(x = size.width * 0.82f, y = size.height * 0.24f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
