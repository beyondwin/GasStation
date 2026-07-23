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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gasstation.core.designsystem.ColorSurface
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationSummaryStrip
import com.gasstation.core.designsystem.gasStationFuelTypeLabel
import com.gasstation.core.designsystem.gasStationSearchRadiusLabel
import com.gasstation.core.designsystem.gasStationWonLabel

internal const val STATION_LIST_QUERY_CONTEXT_TAG = "station-list-query-context"
internal const val STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG = "station-list-query-context-location-icon"
internal const val STATION_LIST_DECISION_COUNT_TAG = "station-list-decision-count"
internal const val STATION_LIST_DECISION_LOWEST_TAG = "station-list-decision-lowest"
internal const val STATION_LIST_DECISION_AVERAGE_TAG = "station-list-decision-average"
internal const val STATION_LIST_DECISION_SAVINGS_TAG = "station-list-decision-savings"

@Composable
internal fun StationListDecisionSummaryStrip(summary: StationListDecisionSummary, modifier: Modifier = Modifier) {
    val numericMetricStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    GasStationSummaryStrip(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATION_LIST_DECISION_SUMMARY_TAG),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
            ) {
                Text(
                    text = stringResource(
                        if (summary.isLowestPriceTied) {
                            R.string.station_list_decision_tied_lowest
                        } else {
                            R.string.station_list_decision_lowest
                        },
                        summary.lowestPriceWon.gasStationWonLabel(),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(STATION_LIST_DECISION_LOWEST_TAG),
                    style = numericMetricStyle,
                    color = ColorYellow,
                )
                Text(
                    text = stringResource(R.string.station_list_decision_count, summary.count),
                    style = numericMetricStyle,
                    color = ColorSurface,
                    modifier = Modifier.testTag(STATION_LIST_DECISION_COUNT_TAG),
                )
            }
            if (summary.averagePriceWon != null && summary.savingsWon != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
                ) {
                    Text(
                        text = stringResource(
                            R.string.station_list_decision_average,
                            summary.averagePriceWon.gasStationWonLabel(),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(STATION_LIST_DECISION_AVERAGE_TAG),
                        style = numericMetricStyle,
                        color = ColorSurface,
                        maxLines = 2,
                    )
                    Text(
                        text = stringResource(
                            R.string.station_list_decision_savings,
                            summary.savingsWon.gasStationWonLabel(),
                        ),
                        modifier = Modifier.testTag(STATION_LIST_DECISION_SAVINGS_TAG),
                        style = numericMetricStyle,
                        color = ColorYellow,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
internal fun QueryContextSummary(uiState: StationListUiState, modifier: Modifier = Modifier) {
    val preferences = requireNotNull(uiState.preferences) {
        "Results require ready user preferences"
    }
    val context = LocalContext.current
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val iconSize = GasStationTheme.iconSize
    val addressLabel = uiState.currentAddressLabel
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val conditionLabel = stringResource(
        R.string.station_list_query_context_condition,
        preferences.searchRadius.gasStationSearchRadiusLabel().resolve(context),
        preferences.fuelType.gasStationFuelTypeLabel().resolve(context),
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(iconSize.status)
                        .testTag(STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG),
                )
                Text(
                    text = addressLabel,
                    style = typography.body.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = conditionLabel,
            style = typography.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
