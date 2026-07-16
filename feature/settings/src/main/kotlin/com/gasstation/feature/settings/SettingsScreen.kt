package com.gasstation.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationRowDivider
import com.gasstation.core.designsystem.component.GasStationTopBar
import com.gasstation.core.designsystem.string.StringResource

internal const val SETTINGS_SCREEN_LIST_TAG = "settings-screen-list"
internal const val SETTINGS_GROUP_TAG_PREFIX = "settings-group-"
internal const val SETTINGS_ROW_TAG_PREFIX = "settings-row-"

@Composable
fun SettingsScreen(uiState: SettingsUiState, onSectionClick: (SettingsSection) -> Unit) {
    GasStationBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
                    title = { Text(text = stringResource(R.string.settings_title)) },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SETTINGS_SCREEN_LIST_TAG)
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
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
    ) {
        SettingsGroupHeader(group = group)
        sections.forEachIndexed { index, section ->
            SettingsMenuRow(
                section = section,
                selectedLabel = uiState.selectedLabelFor(section),
                onClick = { onSectionClick(section) },
            )
            if (index < sections.lastIndex) {
                GasStationRowDivider()
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(group: SettingsSectionGroup) {
    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(group.titleResId),
            style = GasStationTheme.typography.sectionTitle,
            color = ColorBlack,
        )
        Text(
            text = stringResource(group.subtitleResId),
            style = GasStationTheme.typography.meta,
            color = ColorGray2,
        )
    }
}

@Composable
private fun SettingsMenuRow(section: SettingsSection, selectedLabel: StringResource, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$SETTINGS_ROW_TAG_PREFIX${section.routeSegment}")
            .heightIn(min = 48.dp)
            .animateContentSize()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(section.titleResId),
                style = GasStationTheme.typography.cardTitle,
                color = ColorBlack,
            )
            Text(
                text = selectedLabel.resolve(context),
                style = GasStationTheme.typography.body,
                color = ColorBlack,
            )
            Text(
                text = stringResource(section.subtitleResId),
                style = GasStationTheme.typography.meta,
                color = ColorGray3,
            )
        }
        LegacyChevronIcon()
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
