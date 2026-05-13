package com.gasstation.feature.stationlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

internal const val STATION_LIST_QUERY_CONTEXT_TAG = "station-list-query-context"

@Composable
internal fun QueryContextSummary(uiState: StationListUiState, modifier: Modifier = Modifier) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
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
            Text(
                text = addressLabel,
                style = typography.body.copy(fontWeight = FontWeight.Bold),
                color = ColorBlack,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
