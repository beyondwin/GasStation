package com.gasstation.feature.stationlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorNeutralLine
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationComparisonRow
import com.gasstation.core.designsystem.component.GasStationGuidanceCard
import com.gasstation.core.designsystem.component.GasStationRowDivider

internal const val STATION_LIST_SKELETON_ROW_TAG = "station-list-skeleton-row"

@Composable
internal fun PermissionRequired(modifier: Modifier = Modifier, onRequestPermissions: () -> Unit) {
    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = stringResource(R.string.station_list_permission_required_title),
            body = stringResource(R.string.station_list_permission_required_body),
            actionLabel = stringResource(R.string.station_list_permission_action),
            onAction = onRequestPermissions,
        )
    }
}

@Composable
internal fun GpsRequired(modifier: Modifier = Modifier, onOpenLocationSettings: () -> Unit) {
    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = stringResource(R.string.station_list_gps_required_title),
            body = stringResource(R.string.station_list_gps_required_body),
            actionLabel = stringResource(R.string.station_list_gps_settings_action),
            onAction = onOpenLocationSettings,
        )
    }
}

@Composable
internal fun LoadingState(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.station_list_loading_title)
    LazyColumn(
        modifier = modifier.semantics { contentDescription = loadingLabel },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items(3) { index ->
            GasStationComparisonRow(
                modifier = Modifier.testTag(STATION_LIST_SKELETON_ROW_TAG),
                leading = { LoadingPlaceholder(50.dp, 50.dp) },
                primary = {
                    LoadingPlaceholder(112.dp, 30.dp)
                    LoadingPlaceholder(176.dp, 20.dp)
                    LoadingPlaceholder(132.dp, 16.dp)
                },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        LoadingPlaceholder(58.dp, 20.dp)
                        Spacer(Modifier.height(8.dp))
                        LoadingPlaceholder(48.dp, 48.dp)
                    }
                },
            )
            if (index < 2) {
                GasStationRowDivider()
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder(width: Dp, height: Dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(ColorNeutralLine.copy(alpha = 0.72f)),
    )
}

@Composable
internal fun FailureState(reason: StationListFailureReason, onAction: (StationListAction) -> Unit, modifier: Modifier = Modifier) {
    val content = reason.toFailureCardContent()

    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = content.title,
            body = content.body,
            actionLabel = stringResource(R.string.station_list_retry_action),
            onAction = { onAction(StationListAction.RetryClicked) },
        )
    }
}

@Composable
internal fun EmptyState(onAction: (StationListAction) -> Unit, modifier: Modifier = Modifier) {
    GasStationGuidanceCard(
        modifier = modifier,
        title = stringResource(R.string.station_list_empty_title),
        body = stringResource(R.string.station_list_empty_body),
        actionLabel = stringResource(R.string.station_list_retry_action),
        onAction = { onAction(StationListAction.RetryClicked) },
    )
}

@Composable
private fun BrandedStateContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val spacing = GasStationTheme.spacing
    Box(
        modifier = modifier.padding(spacing.space24),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private data class StationListFailureCardContent(val title: String, val body: String)

@Composable
private fun StationListFailureReason.toFailureCardContent(): StationListFailureCardContent = when (this) {
    StationListFailureReason.LocationTimedOut -> StationListFailureCardContent(
        title = stringResource(R.string.station_list_location_timeout_title),
        body = stringResource(R.string.station_list_location_timeout_body),
    )

    StationListFailureReason.LocationFailed -> StationListFailureCardContent(
        title = stringResource(R.string.station_list_location_failed_title),
        body = stringResource(R.string.station_list_location_failed_body),
    )

    StationListFailureReason.RefreshTimedOut -> StationListFailureCardContent(
        title = stringResource(R.string.station_list_refresh_timeout_title),
        body = stringResource(R.string.station_list_refresh_timeout_body),
    )

    StationListFailureReason.RefreshFailed -> StationListFailureCardContent(
        title = stringResource(R.string.station_list_refresh_failed_title),
        body = stringResource(R.string.station_list_refresh_failed_body),
    )
}
