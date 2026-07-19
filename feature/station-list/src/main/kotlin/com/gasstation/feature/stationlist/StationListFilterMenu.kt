package com.gasstation.feature.stationlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorSurface
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBrandLogoTile
import com.gasstation.core.designsystem.component.UrbanSignalTokens
import com.gasstation.core.model.Brand

internal const val STATION_LIST_RADIUS_FILTER_TAG = "station-list-filter-radius"
internal const val STATION_LIST_FUEL_FILTER_TAG = "station-list-filter-fuel"
internal const val STATION_LIST_BRAND_FILTER_TAG = "station-list-filter-brand"
internal const val STATION_LIST_FILTER_MENU_TAG = "station-list-filter-menu"
internal const val STATION_LIST_FILTER_OPTION_TAG_PREFIX = "station-list-filter-option-"
internal const val STATION_LIST_FILTER_BRAND_LOGO_TAG_PREFIX = "station-list-filter-brand-logo-"
internal const val STATION_LIST_FILTER_SELECTED_CHECK_TAG_PREFIX = "station-list-filter-selected-check-"

internal enum class StationListFilterMenuKind { Radius, Fuel, Brand }

internal data class StationListFilterOption<T>(val value: T, val label: String, val testKey: String, val brand: Brand? = null)

@Composable
internal fun <T> StationListFilterMenu(
    expanded: Boolean,
    title: String,
    options: List<StationListFilterOption<T>>,
    selected: T,
    onDismissRequest: () -> Unit,
    onSelected: (T) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .testTag(STATION_LIST_FILTER_MENU_TAG)
            .widthIn(min = 220.dp, max = 300.dp)
            .border(2.dp, ColorBlack, shape)
            .background(ColorSurface, shape)
            .semantics {
                dismiss {
                    onDismissRequest()
                    true
                }
            },
        shape = shape,
        containerColor = ColorSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Text(
            text = title,
            style = GasStationTheme.typography.meta,
            color = ColorBlack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        options.forEach { option ->
            StationListFilterMenuRow(
                option = option,
                selected = option.value == selected,
                onClick = { onSelected(option.value) },
            )
        }
    }
}

@Composable
private fun <T> StationListFilterMenuRow(option: StationListFilterOption<T>, selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) ColorYellow else ColorBlack
    val backgroundColor = if (selected) ColorBlack else ColorSurface
    Row(
        modifier = Modifier
            .testTag("$STATION_LIST_FILTER_OPTION_TAG_PREFIX${option.testKey}")
            .fillMaxWidth()
            .heightIn(min = UrbanSignalTokens.minimumTouchTarget)
            .background(backgroundColor)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        option.brand?.let { brand ->
            GasStationBrandLogoTile(
                brand = brand,
                contentDescription = null,
                modifier = Modifier
                    .size(UrbanSignalTokens.filterMenuLogoTileSize)
                    .testTag("$STATION_LIST_FILTER_BRAND_LOGO_TAG_PREFIX${option.testKey}"),
                tileSize = UrbanSignalTokens.filterMenuLogoTileSize,
                logoSize = UrbanSignalTokens.filterMenuLogoSize,
            )
        }
        Text(
            text = option.label,
            style = GasStationTheme.typography.chip,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.testTag("$STATION_LIST_FILTER_SELECTED_CHECK_TAG_PREFIX${option.testKey}"),
                tint = ColorYellow,
            )
        }
    }
}
