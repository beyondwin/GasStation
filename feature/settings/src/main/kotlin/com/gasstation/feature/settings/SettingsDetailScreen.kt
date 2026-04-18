package com.gasstation.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.component.LegacyTopBar
import com.gasstation.core.designsystem.component.LegacyYellowBackground

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(ColorGray4),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = ColorWhite,
                        ) {
                            options.forEachIndexed { index, option ->
                                SettingsDetailRow(
                                    label = option.label,
                                    isSelected = option.isSelected,
                                    onClick = { onOptionClick(option) },
                                )
                                if (index != options.lastIndex) {
                                    HorizontalDivider(color = ColorGray4)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                selected = isSelected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = ColorBlack,
        )
        if (isSelected) {
            Spacer(modifier = Modifier.size(12.dp))
            SelectedCheckIcon()
        }
    }
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
    Canvas(modifier = Modifier.size(18.dp)) {
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
