package com.gasstation.feature.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.LegacyChromeCard
import com.gasstation.core.designsystem.component.LegacySectionHeading
import com.gasstation.core.designsystem.component.LegacyTopBar
import com.gasstation.core.designsystem.component.LegacyYellowBackground

@Composable
fun WatchlistScreen(
    uiState: WatchlistUiState,
) {
    val spacing = GasStationTheme.spacing

    LegacyYellowBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LegacyTopBar(
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
                    contentPadding = PaddingValues(
                        horizontal = spacing.space16,
                        vertical = spacing.space12,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.space12),
                ) {
                    items(uiState.stations, key = WatchlistItemUiModel::id) { station ->
                        LegacyChromeCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(WATCHLIST_CARD_CONTENT_DESCRIPTION),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                                ) {
                                    MetricBlock(
                                        modifier = Modifier.weight(1f),
                                        label = "가격",
                                        number = station.priceNumberLabel,
                                        unit = station.priceUnitLabel,
                                        prominent = true,
                                    )
                                    MetricBlock(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag(WATCHLIST_DISTANCE_METRIC_TAG),
                                        label = "거리",
                                        number = station.distanceNumberLabel,
                                        unit = station.distanceUnitLabel,
                                        prominent = false,
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                                    Text(
                                        text = station.name,
                                        style = GasStationTheme.typography.cardTitle,
                                        color = ColorBlack,
                                    )
                                    Text(
                                        text = station.brandLabel,
                                        style = GasStationTheme.typography.meta,
                                        color = ColorGray3,
                                    )
                                }
                                Text(
                                    text = station.priceDeltaLabel,
                                    style = GasStationTheme.typography.body,
                                    color = ColorGray2,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space16),
                                ) {
                                    SupportingBlock(
                                        modifier = Modifier.weight(1f),
                                        label = "변동",
                                        value = station.priceLabel,
                                    )
                                    SupportingBlock(
                                        modifier = Modifier.weight(1f),
                                        label = "확인",
                                        value = station.lastSeenLabel,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(
    modifier: Modifier = Modifier,
    label: String,
    number: String,
    unit: String,
    prominent: Boolean,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text = label,
            style = typography.meta,
            color = ColorGray3,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                style = if (prominent) typography.priceHero else typography.metricValue,
                color = ColorBlack,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(start = spacing.space4, bottom = 3.dp),
                style = typography.meta,
                color = ColorGray2,
            )
        }
    }
}

@Composable
private fun SupportingBlock(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text = label,
            style = typography.meta,
            color = ColorGray3,
        )
        Text(
            text = value,
            style = typography.body,
            color = ColorGray2,
        )
    }
}

@Composable
private fun EmptyWatchlist(
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    Column(
        modifier = modifier.padding(horizontal = spacing.space16, vertical = spacing.space24),
        verticalArrangement = Arrangement.Center,
    ) {
        LegacyChromeCard(modifier = Modifier.fillMaxWidth()) {
            LegacySectionHeading(
                title = "비교할 주유소가 없습니다.",
                subtitle = "주유소 목록에서 별표를 눌러 가격과 거리를 한곳에 모아보세요.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                SupportingBlock(
                    label = "다음 단계",
                    value = "목록 화면에서 별표를 눌러 비교 대상을 추가하세요.",
                )
                SupportingBlock(
                    label = "화면 목적",
                    value = "선택한 주유소의 가격과 거리를 한 번에 비교합니다.",
                )
            }
        }
    }
}
