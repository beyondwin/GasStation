package com.gasstation.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationBrandLogoTile
import com.gasstation.core.designsystem.component.GasStationRow
import com.gasstation.core.designsystem.component.GasStationRowDivider
import com.gasstation.core.designsystem.component.GasStationTopBar
import com.gasstation.core.designsystem.component.UrbanSignalTokens

internal const val SETTINGS_SELECTED_CHECK_TAG = "settings-selected-check"
internal const val SETTINGS_OPTIONS_GROUP_TAG = "settings-options-group"
internal const val SETTINGS_BRAND_LOGO_TAG_PREFIX = "settings-brand-logo-"

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
                    title = { Text(text = stringResource(section.titleResId)) },
                    navigationIcon = {
                        SettingsDetailTopBarAction(
                            contentDescription = stringResource(R.string.settings_back),
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
                    .testTag(SETTINGS_OPTIONS_GROUP_TAG)
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            ) {
                item {
                    Text(
                        text = stringResource(section.subtitleResId),
                        style = GasStationTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                itemsIndexed(
                    items = options,
                    key = { _, option -> option.action.toString() },
                ) { index, option ->
                    SettingsDetailOptionRow(
                        section = section,
                        option = option,
                        onClick = { onOptionClick(option) },
                    )
                    if (index < options.lastIndex) {
                        GasStationRowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailOptionRow(section: SettingsSection, option: SettingOptionUiModel, onClick: () -> Unit) {
    val context = LocalContext.current
    val leadingContent: (@Composable RowScope.() -> Unit)? = option.brandIconBrand
        ?.takeIf { section == SettingsSection.BrandFilter }
        ?.let { brand ->
            {
                GasStationBrandLogoTile(
                    brand = brand,
                    contentDescription = null,
                    modifier = Modifier.testTag("$SETTINGS_BRAND_LOGO_TAG_PREFIX${brand.name}"),
                    tileSize = UrbanSignalTokens.compactLogoTileSize,
                    logoSize = UrbanSignalTokens.compactLogoSize,
                )
            }
        }

    GasStationRow(
        title = option.label.resolve(context),
        body = option.subtitle?.resolve(context),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                selected = option.isSelected
                role = Role.RadioButton
            }
            .padding(vertical = 12.dp),
        leadingContent = leadingContent,
        trailingContent = if (option.isSelected) {
            { SelectedCheckIcon() }
        } else {
            null
        },
    )
}

@Composable
private fun SettingsDetailTopBarAction(contentDescription: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
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
