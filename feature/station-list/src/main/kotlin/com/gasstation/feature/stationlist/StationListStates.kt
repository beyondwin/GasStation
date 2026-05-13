package com.gasstation.feature.stationlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationGuidanceCard

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
    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = stringResource(R.string.station_list_loading_title),
            body = stringResource(R.string.station_list_loading_body),
            leadingContent = {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = ColorBlack,
                    strokeWidth = 3.dp,
                )
            },
        )
    }
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
