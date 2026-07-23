package com.gasstation.feature.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
internal fun WatchlistLoadingState(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.watchlist_loading)
    LazyColumn(
        modifier = modifier.semantics { contentDescription = loadingLabel },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Text(
                text = loadingLabel,
                style = GasStationTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        items(3) { index ->
            GasStationComparisonRow(
                leading = { WatchlistLoadingPlaceholder(50.dp, 50.dp) },
                primary = {
                    WatchlistLoadingPlaceholder(112.dp, 30.dp)
                    WatchlistLoadingPlaceholder(176.dp, 20.dp)
                    WatchlistLoadingPlaceholder(132.dp, 16.dp)
                },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        WatchlistLoadingPlaceholder(58.dp, 20.dp)
                        Spacer(Modifier.height(8.dp))
                        WatchlistLoadingPlaceholder(48.dp, 48.dp)
                    }
                },
            )
            if (index < 2) GasStationRowDivider()
        }
    }
}

@Composable
internal fun WatchlistLoadFailureState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GasStationGuidanceCard(
            title = stringResource(R.string.watchlist_load_failed_title),
            body = stringResource(R.string.watchlist_load_failed_body),
            actionLabel = stringResource(R.string.watchlist_retry),
            onAction = onRetry,
        )
    }
}

@Composable
internal fun EmptyWatchlist(onNavigateNearby: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        GasStationGuidanceCard(
            title = stringResource(R.string.watchlist_empty_title),
            body = stringResource(R.string.watchlist_empty_body),
            actionLabel = stringResource(R.string.watchlist_empty_action),
            onAction = onNavigateNearby,
        )
    }
}

@Composable
private fun WatchlistLoadingPlaceholder(width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(ColorNeutralLine.copy(alpha = 0.72f)),
    )
}
