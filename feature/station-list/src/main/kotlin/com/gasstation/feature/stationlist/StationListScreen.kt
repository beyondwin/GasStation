package com.gasstation.feature.stationlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.location.LocationPermissionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListScreen(
    uiState: StationListUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (StationListAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.selectedSortOrder.toTitleLabel(),
                        modifier = Modifier.clickable { onAction(StationListAction.SortToggleRequested) },
                    )
                },
                actions = {
                    if (onWatchlistClick != null) {
                        TextButton(
                            modifier = Modifier.semantics {
                                contentDescription = "관심 비교"
                            },
                            onClick = onWatchlistClick,
                        ) {
                            Text(text = "관심 비교")
                        }
                    }
                    IconButton(onClick = { onAction(StationListAction.RefreshRequested) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.permissionState == LocationPermissionState.Denied -> PermissionRequired(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onRequestPermissions = onRequestPermissions,
            )

            !uiState.isGpsEnabled -> GpsRequired(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onAction = onAction,
            )

            uiState.isLoading -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> StationListContent(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun StationListContent(
    uiState: StationListUiState,
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FilterSummary(uiState = uiState)
        }
        if (uiState.permissionState == LocationPermissionState.ApproximateGranted) {
            item {
                InfoCard(text = "대략적인 위치 기준으로 주변 주유소를 찾고 있습니다.")
            }
        }
        if (uiState.isStale) {
            item {
                InfoCard(
                    text = buildString {
                        append("오래된 결과를 표시 중입니다.")
                        uiState.lastUpdatedAt?.let {
                            append(" 마지막 갱신 ${it.toDisplayLabel()}")
                        }
                    },
                )
            }
        }
        if (uiState.isRefreshing) {
            item {
                InfoCard(text = "새로고침 중입니다.")
            }
        }
        if (uiState.stations.isEmpty()) {
            item {
                EmptyState(onAction = onAction)
            }
        } else {
            items(uiState.stations, key = StationListItemUiModel::id) { station ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(StationListAction.StationClicked(station)) },
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
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
                            }
                            IconButton(
                                modifier = Modifier.size(40.dp),
                                onClick = {
                                    onAction(
                                        StationListAction.WatchToggled(
                                            stationId = station.id,
                                            watched = !station.isWatched,
                                        ),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = if (station.isWatched) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                    contentDescription = "관심 주유소 토글",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSummary(
    uiState: StationListUiState,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "현재 조건", style = MaterialTheme.typography.titleMedium)
            AssistChip(onClick = {}, label = { Text(uiState.selectedRadius.toLabel()) })
            AssistChip(onClick = {}, label = { Text(uiState.selectedFuelType.toLabel()) })
        }
    }
}

@Composable
private fun PermissionRequired(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "주변 주유소 검색을 위해 위치 권한이 필요합니다.")
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = onRequestPermissions,
        ) {
            Text(text = "권한 요청")
        }
    }
}

@Composable
private fun GpsRequired(
    modifier: Modifier = Modifier,
    onAction: (StationListAction) -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "위치 서비스를 켜야 주변 주유소를 찾을 수 있습니다.")
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = { onAction(StationListAction.RefreshRequested) },
        ) {
            Text(text = "위치 설정 열기")
        }
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    onAction: (StationListAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "주변 주유소 결과가 없습니다.")
            Button(onClick = { onAction(StationListAction.RetryClicked) }) {
                Text(text = "다시 시도")
            }
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun com.gasstation.domain.station.model.SortOrder.toTitleLabel(): String = when (this) {
    com.gasstation.domain.station.model.SortOrder.DISTANCE -> "거리순 보기"
    com.gasstation.domain.station.model.SortOrder.PRICE -> "가격순 보기"
}

private fun com.gasstation.domain.station.model.SearchRadius.toLabel(): String = when (this) {
    com.gasstation.domain.station.model.SearchRadius.KM_3 -> "3km"
    com.gasstation.domain.station.model.SearchRadius.KM_4 -> "4km"
    com.gasstation.domain.station.model.SearchRadius.KM_5 -> "5km"
}

private fun com.gasstation.domain.station.model.FuelType.toLabel(): String = when (this) {
    com.gasstation.domain.station.model.FuelType.GASOLINE -> "휘발유"
    com.gasstation.domain.station.model.FuelType.DIESEL -> "경유"
    com.gasstation.domain.station.model.FuelType.PREMIUM_GASOLINE -> "고급휘발유"
    com.gasstation.domain.station.model.FuelType.KEROSENE -> "등유"
    com.gasstation.domain.station.model.FuelType.LPG -> "LPG"
}

private fun Instant.toDisplayLabel(): String =
    DateTimeFormatter.ofPattern("MM.dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)
