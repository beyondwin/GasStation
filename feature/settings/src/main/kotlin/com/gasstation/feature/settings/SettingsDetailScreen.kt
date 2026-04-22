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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
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
        val strokeWidth = size.minDimension * 0.085f
        val funnel = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.24f)
            lineTo(size.width * 0.78f, size.height * 0.24f)
            lineTo(size.width * 0.58f, size.height * 0.50f)
            lineTo(size.width * 0.58f, size.height * 0.68f)
            lineTo(size.width * 0.42f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.50f)
            close()
        }
        drawPath(
            path = funnel,
            color = ColorBlack,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
            ),
        )
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.20f, y = size.height * 0.80f),
            end = center.copy(x = size.width * 0.80f, y = size.height * 0.20f),
            strokeWidth = strokeWidth * 1.08f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorBlack,
            start = center.copy(x = size.width * 0.20f, y = size.height * 0.80f),
            end = center.copy(x = size.width * 0.80f, y = size.height * 0.20f),
            strokeWidth = strokeWidth * 0.46f,
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
