package com.gasstation.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationTopBar

internal const val SETTINGS_SCREEN_LIST_TAG = "settings-screen-list"
internal const val SETTINGS_GROUP_TAG_PREFIX = "settings-group-"
internal const val SETTINGS_ROW_TAG_PREFIX = "settings-row-"

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onCloseClick: () -> Unit,
    onSectionClick: (SettingsSection) -> Unit,
) {
    GasStationBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
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
                    .testTag(SETTINGS_SCREEN_LIST_TAG)
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(SettingsSectionGroup.entries, key = SettingsSectionGroup::name) { group ->
                    SettingsSectionGroupBlock(
                        group = group,
                        sections = SettingsSection.entries.filter { section -> section.group == group },
                        uiState = uiState,
                        onSectionClick = onSectionClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionGroupBlock(
    group: SettingsSectionGroup,
    sections: List<SettingsSection>,
    uiState: SettingsUiState,
    onSectionClick: (SettingsSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$SETTINGS_GROUP_TAG_PREFIX${group.name}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GasStationCard {
            SettingsGroupHeader(group = group)
            sections.forEachIndexed { index, section ->
                SettingsMenuRow(
                    section = section,
                    selectedLabel = uiState.selectedLabelFor(section),
                    onClick = { onSectionClick(section) },
                )
                if (index != sections.lastIndex) {
                    SettingsMenuDivider()
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(group: SettingsSectionGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = group.title,
            style = GasStationTheme.typography.sectionTitle,
            color = ColorBlack,
        )
        Text(
            text = group.subtitle,
            style = GasStationTheme.typography.bannerBody,
            color = ColorGray2,
        )
    }
}

@Composable
private fun SettingsMenuRow(
    section: SettingsSection,
    selectedLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$SETTINGS_ROW_TAG_PREFIX${section.routeSegment}")
            .animateContentSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(GasStationTheme.corner.small))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${section.title} : $selectedLabel",
                style = GasStationTheme.typography.cardTitle,
                color = ColorBlack,
            )
            Text(
                text = section.subtitle,
                style = GasStationTheme.typography.bannerBody,
                color = ColorGray3,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        LegacyChevronIcon()
    }
}

@Composable
private fun SettingsMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorGray),
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
