package com.gasstation.feature.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    uiState: WatchlistUiState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "관심 비교") },
            )
        },
    ) { innerPadding ->
        if (uiState.stations.isEmpty()) {
            EmptyWatchlist(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.stations, key = WatchlistItemUiModel::id) { station ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = station.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = station.brandLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${station.priceLabel} · ${station.distanceLabel}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = station.priceDeltaLabel,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = station.lastSeenLabel,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWatchlist(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "관심 주유소가 없습니다.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "주유소 목록에서 별표를 눌러 비교할 주유소를 추가하세요.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
