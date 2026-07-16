package com.gasstation.feature.watchlist

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import com.gasstation.core.designsystem.ColorSurface
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationBrandLogoTile
import com.gasstation.core.designsystem.component.GasStationComparisonRow
import com.gasstation.core.designsystem.component.GasStationGuidanceCard
import com.gasstation.core.designsystem.component.GasStationRowDivider
import com.gasstation.core.designsystem.component.GasStationSummaryStrip
import com.gasstation.core.designsystem.component.GasStationTopBar
import com.gasstation.core.designsystem.component.UrbanSignalTokens
import com.gasstation.core.designsystem.gasStationWonLabel

@Composable
fun WatchlistScreen(uiState: WatchlistUiState, onAction: (WatchlistAction) -> Unit, onNavigateNearby: () -> Unit) {
    val spacing = GasStationTheme.spacing

    GasStationBackground(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WATCHLIST_ROOT_TAG)
            .semantics { testTagsAsResourceId = true },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
                    title = { Text(text = stringResource(R.string.watchlist_title)) },
                )
            },
        ) { innerPadding ->
            if (uiState.stations.isEmpty()) {
                EmptyWatchlist(
                    onNavigateNearby = onNavigateNearby,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = spacing.space16, vertical = spacing.space16),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = spacing.space16, vertical = spacing.space8),
                ) {
                    item(key = "watchlist-summary") {
                        WatchlistSummary(
                            summary = uiState.summary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(spacing.space8))
                    }
                    itemsIndexed(
                        items = uiState.stations,
                        key = { _, station -> station.id },
                    ) { index, station ->
                        WatchlistRow(
                            station = station,
                            onRemove = { onAction(WatchlistAction.RemoveClicked(station.id)) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = tween(180),
                                fadeOutSpec = tween(180),
                            ),
                        )
                        if (index < uiState.stations.lastIndex) {
                            GasStationRowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSummary(summary: WatchlistSummaryUiModel, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    GasStationSummaryStrip(modifier = modifier) {
        if (fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space4)) {
                SummaryCount(summary)
                SummaryAverage(summary)
                SummaryLatestSeen(summary)
            }
        } else {
            SummaryCount(summary)
            SummaryAverage(summary)
            SummaryLatestSeen(summary)
        }
    }
}

@Composable
private fun SummaryCount(summary: WatchlistSummaryUiModel) {
    Text(
        text = stringResource(R.string.watchlist_summary_count, summary.count),
        color = ColorSurface,
        style = GasStationTheme.typography.meta,
    )
}

@Composable
private fun SummaryAverage(summary: WatchlistSummaryUiModel) {
    summary.averagePriceWon?.let { average ->
        Text(
            text = stringResource(R.string.watchlist_summary_average, average.gasStationWonLabel()),
            color = ColorYellow,
            style = GasStationTheme.typography.meta,
        )
    }
}

@Composable
private fun SummaryLatestSeen(summary: WatchlistSummaryUiModel) {
    Text(
        text = summary.latestSeenAt?.let { latest ->
            stringResource(
                R.string.watchlist_summary_latest_seen,
                latest.toWatchlistLastSeenLabel(),
            )
        } ?: stringResource(R.string.watchlist_summary_no_seen),
        color = ColorSurface,
        style = GasStationTheme.typography.meta,
    )
}

@Composable
private fun WatchlistRow(station: WatchlistItemUiModel, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val minimumRowHeight = if (LocalDensity.current.fontScale >= 1.5f) {
        UrbanSignalTokens.compactRowMinHeight + UrbanSignalTokens.minimumTouchTarget
    } else {
        UrbanSignalTokens.compactRowMinHeight
    }
    val savedStateDescription = stringResource(R.string.watchlist_saved_state_description)
    GasStationComparisonRow(
        modifier = modifier
            .heightIn(min = minimumRowHeight)
            .testTag(WATCHLIST_ROW_TAG),
        leading = {
            GasStationBrandLogoTile(
                brand = station.brand,
                contentDescription = stringResource(
                    R.string.watchlist_brand_description,
                    station.brandLabel,
                ),
                tileSize = UrbanSignalTokens.compactLogoTileSize,
                logoSize = UrbanSignalTokens.compactLogoSize,
            )
        },
        primary = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = station.priceNumberLabel,
                    style = GasStationTheme.typography.compactPriceHero,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = station.priceUnitLabel,
                    style = GasStationTheme.typography.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = station.name,
                style = GasStationTheme.typography.cardTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.watchlist_row_meta,
                    station.semanticPriceDeltaLabel(),
                    station.lastSeenLabel,
                ),
                style = GasStationTheme.typography.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = station.distanceLabel,
                    style = GasStationTheme.typography.metricValue,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(UrbanSignalTokens.minimumTouchTarget)
                        .testTag(WATCHLIST_REMOVE_TAG_PREFIX + station.id)
                        .semantics {
                            selected = true
                            stateDescription = savedStateDescription
                        },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = stringResource(R.string.watchlist_remove_description),
                        tint = ColorYellow,
                    )
                }
            }
        },
    )
}

@Composable
private fun WatchlistItemUiModel.semanticPriceDeltaLabel(): String = when (priceDeltaTone) {
    WatchlistPriceDeltaTone.Rise -> stringResource(
        R.string.watchlist_price_delta_rise,
        requireNotNull(priceDeltaWon),
    )

    WatchlistPriceDeltaTone.Fall -> stringResource(
        R.string.watchlist_price_delta_fall,
        requireNotNull(priceDeltaWon),
    )

    WatchlistPriceDeltaTone.Neutral -> stringResource(R.string.watchlist_price_delta_neutral)
}

@Composable
private fun EmptyWatchlist(onNavigateNearby: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        GasStationGuidanceCard(
            title = stringResource(R.string.watchlist_empty_title),
            body = stringResource(R.string.watchlist_empty_body),
            actionLabel = stringResource(R.string.watchlist_empty_action),
            onAction = onNavigateNearby,
        )
    }
}
