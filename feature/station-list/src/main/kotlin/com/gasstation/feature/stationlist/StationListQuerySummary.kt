package com.gasstation.feature.stationlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorSurface
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationSummaryStrip
import com.gasstation.core.designsystem.gasStationWonLabel
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

internal const val STATION_LIST_QUERY_CONTEXT_TAG = "station-list-query-context"
internal const val STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG = "station-list-query-context-location-icon"
internal const val STATION_LIST_DECISION_COUNT_TAG = "station-list-decision-count"
internal const val STATION_LIST_DECISION_LOWEST_TAG = "station-list-decision-lowest"
internal const val STATION_LIST_DECISION_AVERAGE_TAG = "station-list-decision-average"
internal const val STATION_LIST_DECISION_SAVINGS_TAG = "station-list-decision-savings"

@Composable
internal fun StationListDecisionSummaryStrip(summary: StationListDecisionSummary, modifier: Modifier = Modifier) {
    GasStationSummaryStrip(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATION_LIST_DECISION_SUMMARY_TAG),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
        ) {
            Text(
                text = stringResource(R.string.station_list_decision_count, summary.count),
                color = ColorSurface,
                modifier = Modifier.testTag(STATION_LIST_DECISION_COUNT_TAG),
            )
            Column(
                modifier = Modifier.testTag(STATION_LIST_DECISION_LOWEST_TAG),
                verticalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space4),
            ) {
                Text(
                    text = stringResource(
                        if (summary.isLowestPriceTied) {
                            R.string.station_list_decision_tied_lowest
                        } else {
                            R.string.station_list_decision_lowest
                        },
                    ),
                    color = ColorYellow,
                )
                Text(summary.lowestPriceWon.gasStationWonLabel(), color = ColorYellow)
            }
            summary.averagePriceWon?.let { average ->
                Text(
                    text = stringResource(
                        R.string.station_list_decision_average,
                        average.gasStationWonLabel(),
                    ),
                    color = ColorSurface,
                    modifier = Modifier.testTag(STATION_LIST_DECISION_AVERAGE_TAG),
                )
            }
            summary.savingsWon?.let { savings ->
                Text(
                    text = stringResource(
                        R.string.station_list_decision_savings,
                        savings.gasStationWonLabel(),
                    ),
                    color = ColorSurface,
                    modifier = Modifier.testTag(STATION_LIST_DECISION_SAVINGS_TAG),
                )
            }
        }
    }
}

@Composable
internal fun QueryContextSummary(uiState: StationListUiState, modifier: Modifier = Modifier) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val iconSize = GasStationTheme.iconSize
    val addressLabel = uiState.currentAddressLabel
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val conditionLabel = stringResource(
        R.string.station_list_query_context_condition,
        uiState.selectedRadius.toLabel(),
        uiState.selectedFuelType.toLabel(),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATION_LIST_QUERY_CONTEXT_TAG)
            .padding(
                horizontal = spacing.space4,
                vertical = spacing.space4,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (addressLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = ColorGray2,
                    modifier = Modifier
                        .size(iconSize.status)
                        .testTag(STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG),
                )
                Text(
                    text = addressLabel,
                    style = typography.body.copy(fontWeight = FontWeight.Bold),
                    color = ColorBlack,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = conditionLabel,
            style = typography.meta,
            color = ColorGray2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun SearchRadius.toLabel(): String = when (this) {
    SearchRadius.KM_3 -> "3km"
    SearchRadius.KM_4 -> "4km"
    SearchRadius.KM_5 -> "5km"
}

@Composable
internal fun FuelType.toLabel(): String = when (this) {
    FuelType.GASOLINE -> stringResource(R.string.station_list_fuel_type_gasoline)
    FuelType.DIESEL -> stringResource(R.string.station_list_fuel_type_diesel)
    FuelType.PREMIUM_GASOLINE -> stringResource(R.string.station_list_fuel_type_premium_gasoline)
    FuelType.KEROSENE -> stringResource(R.string.station_list_fuel_type_kerosene)
    FuelType.LPG -> "LPG"
}
