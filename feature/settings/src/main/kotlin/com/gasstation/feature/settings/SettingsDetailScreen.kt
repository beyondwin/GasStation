package com.gasstation.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.component.LegacyChromeCard
import com.gasstation.core.designsystem.component.LegacyListRow
import com.gasstation.core.designsystem.component.LegacySectionHeading
import com.gasstation.core.designsystem.component.LegacyTopBar
import com.gasstation.core.designsystem.component.LegacyYellowBackground

internal const val SETTINGS_SELECTED_CHECK_TAG = "settings-selected-check"

@Composable
fun SettingsDetailScreen(
    section: SettingsSection,
    options: List<SettingOptionUiModel>,
    onBackClick: () -> Unit,
    onOptionClick: (SettingOptionUiModel) -> Unit,
) {
    LegacyYellowBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LegacyTopBar(
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
                    LegacyChromeCard {
                        LegacySectionHeading(
                            title = section.title,
                            subtitle = section.subtitle,
                        )
                    }
                }
                items(options, key = SettingOptionUiModel::label) { option ->
                    SettingsDetailRow(
                        section = section,
                        option = option,
                        onClick = { onOptionClick(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailRow(
    section: SettingsSection,
    option: SettingOptionUiModel,
    onClick: () -> Unit,
) {
    LegacyListRow(
        modifier = Modifier
            .animateContentSize()
            .semantics {
                selected = option.isSelected
                role = Role.RadioButton
            },
        overline = section.overline,
        title = option.label,
        subtitle = option.subtitle,
        onClick = onClick,
        trailingContent = {
            if (option.isSelected) {
                SelectedCheckIcon()
            }
        },
    )
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
