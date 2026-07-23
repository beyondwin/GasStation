package com.gasstation.feature.stationlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.gasStationBrandFilterIconBrand
import com.gasstation.core.designsystem.gasStationBrandFilterLabel
import com.gasstation.core.designsystem.gasStationFuelTypeLabel
import com.gasstation.core.designsystem.gasStationSearchRadiusLabel
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder

internal const val STATION_LIST_FILTER_RAIL_TAG = "station-list-filter-rail"
internal const val STATION_LIST_FILTER_CHEVRON_TAG_PREFIX = "station-list-filter-chevron-"

@Composable
internal fun StationListFilterRail(uiState: StationListUiState, onAction: (StationListAction) -> Unit, modifier: Modifier = Modifier) {
    val preferences = requireNotNull(uiState.preferences) {
        "Results require ready user preferences"
    }
    val context = LocalContext.current
    var expandedMenuName by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedMenu = expandedMenuName?.let(StationListFilterMenuKind::valueOf)
    val dismissMenu = { expandedMenuName = null }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(STATION_LIST_FILTER_RAIL_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterActionChip(
            label = if (preferences.sortOrder == SortOrder.DISTANCE) {
                stringResource(R.string.station_list_sort_distance)
            } else {
                stringResource(R.string.station_list_sort_price)
            },
            onClick = { onAction(StationListAction.SortToggleRequested) },
            enabled = !uiState.pendingPreferenceWrite,
        )
        FilterMenuChip(
            menuKind = StationListFilterMenuKind.Radius,
            label = preferences.searchRadius.gasStationSearchRadiusLabel().resolve(context),
            testTag = STATION_LIST_RADIUS_FILTER_TAG,
            expanded = expandedMenu == StationListFilterMenuKind.Radius,
            onClick = { expandedMenuName = StationListFilterMenuKind.Radius.name },
            enabled = !uiState.pendingPreferenceWrite,
        ) {
            StationListFilterMenu(
                expanded = true,
                title = stringResource(R.string.station_list_filter_radius_title),
                options = SearchRadius.entries.map { radius ->
                    StationListFilterOption(
                        radius,
                        radius.gasStationSearchRadiusLabel().resolve(context),
                        radius.name,
                    )
                },
                selected = preferences.searchRadius,
                onDismissRequest = dismissMenu,
                onSelected = { radius ->
                    dismissMenu()
                    onAction(StationListAction.SearchRadiusSelected(radius))
                },
            )
        }
        FilterMenuChip(
            menuKind = StationListFilterMenuKind.Fuel,
            label = preferences.fuelType.gasStationFuelTypeLabel().resolve(context),
            testTag = STATION_LIST_FUEL_FILTER_TAG,
            expanded = expandedMenu == StationListFilterMenuKind.Fuel,
            onClick = { expandedMenuName = StationListFilterMenuKind.Fuel.name },
            enabled = !uiState.pendingPreferenceWrite,
        ) {
            StationListFilterMenu(
                expanded = true,
                title = stringResource(R.string.station_list_filter_fuel_title),
                options = FuelType.entries.map { fuelType ->
                    StationListFilterOption(
                        fuelType,
                        fuelType.gasStationFuelTypeLabel().resolve(context),
                        fuelType.name,
                    )
                },
                selected = preferences.fuelType,
                onDismissRequest = dismissMenu,
                onSelected = { fuelType ->
                    dismissMenu()
                    onAction(StationListAction.FuelTypeSelected(fuelType))
                },
            )
        }
        FilterMenuChip(
            menuKind = StationListFilterMenuKind.Brand,
            label = preferences.brandFilter.gasStationBrandFilterLabel(),
            testTag = STATION_LIST_BRAND_FILTER_TAG,
            expanded = expandedMenu == StationListFilterMenuKind.Brand,
            onClick = { expandedMenuName = StationListFilterMenuKind.Brand.name },
            enabled = !uiState.pendingPreferenceWrite,
        ) {
            StationListFilterMenu(
                expanded = true,
                title = stringResource(R.string.station_list_filter_brand_title),
                options = BrandFilter.entries.map { brandFilter ->
                    StationListFilterOption(
                        value = brandFilter,
                        label = brandFilter.gasStationBrandFilterLabel(),
                        testKey = brandFilter.name,
                        brand = brandFilter.gasStationBrandFilterIconBrand(),
                    )
                },
                selected = preferences.brandFilter,
                onDismissRequest = dismissMenu,
                onSelected = { brandFilter ->
                    dismissMenu()
                    onAction(StationListAction.BrandFilterSelected(brandFilter))
                },
            )
        }
    }
}

@Composable
private fun FilterMenuChip(
    menuKind: StationListFilterMenuKind,
    label: String,
    testTag: String,
    expanded: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    menu: @Composable () -> Unit,
) {
    Box {
        FilterActionChip(
            label = label,
            onClick = onClick,
            modifier = Modifier.testTag(testTag),
            expanded = expanded,
            menuKind = menuKind,
            enabled = enabled,
        )
        if (expanded) {
            menu()
        }
    }
}

@Composable
private fun FilterActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    menuKind: StationListFilterMenuKind? = null,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        color = ColorBlack,
        contentColor = ColorYellow,
        shape = RoundedCornerShape(50),
        border = if (expanded) BorderStroke(2.dp, ColorYellow) else null,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = GasStationTheme.typography.chip,
                maxLines = 1,
            )
            menuKind?.let { kind ->
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.station_list_filter_collapse_menu else R.string.station_list_filter_expand_menu,
                    ),
                    modifier = Modifier.testTag("$STATION_LIST_FILTER_CHEVRON_TAG_PREFIX${kind.name}"),
                )
            }
        }
    }
}
