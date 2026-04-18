package com.gasstation.feature.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.component.LegacyChromeCard
import com.gasstation.core.designsystem.component.LegacySectionHeading
import com.gasstation.core.designsystem.component.LegacyTopBar
import com.gasstation.core.designsystem.component.LegacyYellowBackground

@Composable
fun WatchlistScreen(
    uiState: WatchlistUiState,
) {
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.stations, key = WatchlistItemUiModel::id) { station ->
                        LegacyChromeCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(WATCHLIST_CARD_CONTENT_DESCRIPTION),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = station.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ColorBlack,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    MetricBlock(
                                        label = "가격",
                                        number = station.priceNumberLabel,
                                        unit = station.priceUnitLabel,
                                    )
                                    MetricBlock(
                                        label = "거리",
                                        number = station.distanceNumberLabel,
                                        unit = station.distanceUnitLabel,
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SupportingLine(
                                        label = "브랜드",
                                        value = station.brandLabel,
                                    )
                                    SupportingLine(
                                        label = "변동",
                                        value = station.priceDeltaLabel,
                                    )
                                    SupportingLine(
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
    label: String,
    number: String,
    unit: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ColorGray3,
        )
        Row {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = ColorBlack,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = ColorBlack,
            )
        }
    }
}

@Composable
private fun SupportingLine(
    label: String,
    value: String,
) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ColorGray3,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = ColorGray2,
        )
    }
}

@Composable
private fun EmptyWatchlist(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LegacyChromeCard(modifier = Modifier.fillMaxWidth()) {
            LegacySectionHeading(
                title = "비교할 주유소가 없습니다.",
                subtitle = "주유소 목록에서 별표를 눌러 가격과 거리를 한곳에 모아보세요.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SupportingLine(
                    label = "다음 단계",
                    value = "목록 화면에서 별표를 눌러 비교 대상을 추가하세요.",
                )
                SupportingLine(
                    label = "화면 목적",
                    value = "선택한 주유소의 가격과 거리를 한 번에 비교합니다.",
                )
            }
        }
    }
}
