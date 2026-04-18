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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.component.LegacyListRow
import com.gasstation.core.designsystem.component.LegacyTopBar
import com.gasstation.core.designsystem.component.LegacyYellowBackground

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onSectionClick: (SettingsSection) -> Unit,
) {
    LegacyYellowBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LegacyTopBar(
                    title = { Text(text = "찾기 설정") },
                    actions = {
                        SettingsTopBarAction(
                            contentDescription = "닫기",
                            onClick = onCloseClick,
                        ) {
                            LegacyCloseIcon()
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
                items(SettingsSection.entries, key = SettingsSection::routeSegment) { section ->
                    SettingsMenuRow(
                        section = section,
                        selectedLabel = uiState.selectedLabelFor(section),
                        onClick = { onSectionClick(section) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    section: SettingsSection,
    selectedLabel: String,
    onClick: () -> Unit,
) {
    LegacyListRow(
        modifier = Modifier.animateContentSize(),
        overline = section.overline,
        title = "${section.title} : $selectedLabel",
        subtitle = section.subtitle,
        onClick = onClick,
        trailingContent = {
            LegacyChevronIcon()
        },
    )
}

@Composable
private fun SettingsTopBarAction(
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
private fun LegacyCloseIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = size.minDimension * 0.16f
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.2f, y = size.height * 0.2f),
            end = center.copy(x = size.width * 0.8f, y = size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.8f, y = size.height * 0.2f),
            end = center.copy(x = size.width * 0.2f, y = size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LegacyChevronIcon() {
    Canvas(modifier = Modifier.size(width = 10.dp, height = 16.dp)) {
        val strokeWidth = size.minDimension * 0.22f
        drawLine(
            color = ColorGray2,
            start = center.copy(x = size.width * 0.2f, y = size.height * 0.15f),
            end = center.copy(x = size.width * 0.8f, y = size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorGray2,
            start = center.copy(x = size.width * 0.8f, y = size.height * 0.5f),
            end = center.copy(x = size.width * 0.2f, y = size.height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
