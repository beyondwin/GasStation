package com.gasstation.feature.stationlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.gasStationBrandFilterLabel
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder

internal const val STATION_LIST_FILTER_RAIL_TAG = "station-list-filter-rail"

@Composable
internal fun StationListFilterRail(uiState: StationListUiState, onAction: (StationListAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(STATION_LIST_FILTER_RAIL_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterActionChip(
            label = if (uiState.selectedSortOrder == SortOrder.DISTANCE) {
                stringResource(R.string.station_list_sort_distance)
            } else {
                stringResource(R.string.station_list_sort_price)
            },
            onClick = { onAction(StationListAction.SortToggleRequested) },
        )
        FilterMenuChip(
            selected = uiState.selectedRadius,
            options = SearchRadius.entries.map { it to it.toLabel() },
            onSelected = { onAction(StationListAction.SearchRadiusSelected(it)) },
        )
        FilterMenuChip(
            selected = uiState.selectedFuelType,
            options = FuelType.entries.map { it to it.toLabel() },
            onSelected = { onAction(StationListAction.FuelTypeSelected(it)) },
        )
        FilterMenuChip(
            selected = uiState.selectedBrandFilter,
            options = BrandFilter.entries.map { it to it.gasStationBrandFilterLabel() },
            onSelected = { onAction(StationListAction.BrandFilterSelected(it)) },
        )
    }
}

@Composable
private fun <T> FilterMenuChip(selected: T, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterActionChip(
            label = options.first { it.first == selected }.second,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterActionChip(label: String, onClick: () -> Unit) {
    Surface(
        color = ColorBlack,
        contentColor = ColorYellow,
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = GasStationTheme.typography.chip,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            maxLines = 1,
        )
    }
}
