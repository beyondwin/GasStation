package com.gasstation.feature.stationlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationRowDivider
import com.gasstation.core.designsystem.component.GasStationStatusBanner
import com.gasstation.core.designsystem.component.GasStationStatusTone
import com.gasstation.core.designsystem.component.GasStationTopBar

internal const val STATION_LIST_PULL_REFRESH_TAG = "station-list-pull-refresh"
internal const val STATION_LIST_DECISION_SUMMARY_TAG = "station-list-decision-summary"
internal const val STATION_LIST_REFRESH_RAIL_TAG = "station-list-refresh-rail"

@Composable
fun StationListScreen(
    uiState: StationListUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (StationListAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onFirstContentDrawn: () -> Unit = {},
) {
    val refreshLabel = stringResource(R.string.station_list_action_refresh)
    val currentOnFirstContentDrawn by rememberUpdatedState(onFirstContentDrawn)
    val hasFirstUsableContent = uiState.hasFirstUsableContent()

    LaunchedEffect(hasFirstUsableContent) {
        if (hasFirstUsableContent) {
            withFrameNanos { }
            currentOnFirstContentDrawn()
        }
    }
    GasStationBackground(
        modifier = Modifier
            .fillMaxSize()
            .testTag(STATION_LIST_ROOT_TAG)
            .semantics {
                testTagsAsResourceId = true
            },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
                    title = {
                        Text(text = stringResource(R.string.station_list_title))
                    },
                    actions = {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = refreshLabel },
                            onClick = { onAction(StationListAction.RefreshRequested) },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            AnimatedContent(
                targetState = uiState.toBodyState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = { subtleContentTransform() },
                label = "station-list-body",
            ) { bodyState ->
                val fillModifier = Modifier.fillMaxSize()
                when (bodyState) {
                    StationListBodyState.PermissionRequired -> PermissionRequired(fillModifier, onRequestPermissions)

                    StationListBodyState.GpsRequired -> GpsRequired(fillModifier, onOpenLocationSettings)

                    StationListBodyState.InitialLoading -> LoadingState(modifier = fillModifier)

                    is StationListBodyState.Failure -> FailureState(
                        reason = bodyState.reason,
                        modifier = fillModifier,
                        onAction = onAction,
                    )

                    StationListBodyState.Results -> StationListResultsPane(uiState, onAction, fillModifier)
                }
            }
        }
    }
}

@Composable
private fun StationListContent(uiState: StationListUiState, onAction: (StationListAction) -> Unit, modifier: Modifier = Modifier) {
    val banners = StationListBannerModel.from(uiState)
    val spacing = GasStationTheme.spacing
    val decisionSummary = StationListDecisionSummary.from(uiState.stations)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.space16, vertical = spacing.space12),
    ) {
        items(
            items = banners,
            key = { banner -> "${banner.titleResId}_${banner.detailResId ?: 0}_${banner.detailArg ?: ""}" },
        ) { banner ->
            val bannerDetail = banner.detailResId?.let { resId ->
                if (banner.detailArg != null) stringResource(resId, banner.detailArg) else stringResource(resId)
            }
            GasStationStatusBanner(
                modifier = Modifier
                    .padding(bottom = spacing.space8)
                    .animateContentSize(),
                text = stringResource(banner.titleResId),
                detail = bannerDetail,
                tone = banner.tone.toStatusTone(),
            )
        }
        item {
            QueryContextSummary(uiState = uiState, modifier = Modifier.animateContentSize())
        }
        item {
            StationListFilterRail(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier.padding(vertical = spacing.space8),
            )
        }
        decisionSummary?.let { summary ->
            item {
                StationListDecisionSummaryStrip(
                    summary = summary,
                    modifier = Modifier.padding(bottom = spacing.space8),
                )
            }
        }
        if (uiState.stations.isEmpty()) {
            item {
                EmptyState(onAction = onAction, modifier = Modifier.animateContentSize())
            }
        } else {
            itemsIndexed(
                items = uiState.stations,
                key = { _, station -> station.id },
            ) { index, station ->
                StationCard(
                    station = station,
                    fuelTypeLabel = uiState.selectedFuelType.toLabel(),
                    modifier = Modifier.animateContentSize(),
                    onClick = { onAction(StationListAction.StationClicked(station)) },
                    onWatchToggle = {
                        onAction(
                            StationListAction.WatchToggled(
                                stationId = station.id,
                                watched = !station.isWatched,
                            ),
                        )
                    },
                )
                if (index < uiState.stations.lastIndex) {
                    GasStationRowDivider()
                }
            }
        }
    }
}

@Composable
private fun StationListResultsPane(uiState: StationListUiState, onAction: (StationListAction) -> Unit, modifier: Modifier = Modifier) {
    val pullToRefreshState = rememberPullToRefreshState()
    val showTopLoadingRail = uiState.isRefreshing || uiState.isLoading

    PullToRefreshBox(
        isRefreshing = showTopLoadingRail,
        onRefresh = { onAction(StationListAction.RefreshRequested) },
        state = pullToRefreshState,
        modifier = modifier.testTag(STATION_LIST_PULL_REFRESH_TAG),
        indicator = {
            if (!showTopLoadingRail && pullToRefreshState.distanceFraction > 0f) {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
                    isRefreshing = false,
                    containerColor = ColorBlack,
                    color = ColorYellow,
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showTopLoadingRail,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GasStationTheme.spacing.space16)
                    .padding(top = GasStationTheme.spacing.space12),
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                    slideInVertically(animationSpec = tween(durationMillis = 180), initialOffsetY = { -it / 2 }),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                    slideOutVertically(animationSpec = tween(durationMillis = 180), targetOffsetY = { -it / 2 }),
                label = "station-list-refresh-rail",
            ) {
                RefreshingStatusRail(modifier = Modifier.testTag(STATION_LIST_REFRESH_RAIL_TAG))
            }
            StationListContent(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .alpha(if (uiState.isLoading) 0.82f else 1f),
            )
        }
    }
}

@Composable
private fun RefreshingStatusRail(modifier: Modifier = Modifier) {
    val spacing = GasStationTheme.spacing
    val corner = GasStationTheme.corner
    val typography = GasStationTheme.typography

    Surface(
        modifier = modifier,
        color = ColorBlack,
        shape = RoundedCornerShape(corner.small),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space12, vertical = spacing.space8),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.space8),
        ) {
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ColorYellow),
                )
                Text(
                    text = stringResource(R.string.station_list_refresh_rail_title),
                    style = typography.chip.copy(fontWeight = FontWeight.Bold),
                    color = ColorYellow,
                )
            }
            Text(
                text = stringResource(R.string.station_list_refresh_rail_body),
                style = typography.meta,
                color = ColorYellow.copy(alpha = 0.78f),
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = ColorYellow,
                trackColor = ColorYellow.copy(alpha = 0.22f),
            )
        }
    }
}

private fun subtleContentTransform(): ContentTransform = fadeIn(animationSpec = tween(durationMillis = 180)) +
    slideInVertically(animationSpec = tween(durationMillis = 220), initialOffsetY = { it / 14 }) togetherWith
    fadeOut(animationSpec = tween(durationMillis = 140)) +
    slideOutVertically(animationSpec = tween(durationMillis = 180), targetOffsetY = { -it / 18 })

private fun StationListBannerTone.toStatusTone(): GasStationStatusTone = when (this) {
    StationListBannerTone.Neutral -> GasStationStatusTone.Neutral
    StationListBannerTone.Info -> GasStationStatusTone.Info
    StationListBannerTone.Warning -> GasStationStatusTone.Warning
    StationListBannerTone.Error -> GasStationStatusTone.Error
}
