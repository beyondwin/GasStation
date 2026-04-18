package com.gasstation.feature.stationlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.component.LegacyChromeCard
import com.gasstation.core.designsystem.component.LegacySectionHeading
import com.gasstation.core.designsystem.component.LegacyStatusBanner
import com.gasstation.core.designsystem.component.LegacyStatusTone
import com.gasstation.core.designsystem.component.LegacyTopBar
import com.gasstation.core.designsystem.component.LegacyYellowBackground
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.domain.station.model.BrandFilter

internal const val STATION_LIST_METRIC_ROW_TAG = "station-list-metric-row"
internal const val STATION_LIST_CARD_TITLE_TAG = "station-list-card-title"

@Composable
fun StationListScreen(
    uiState: StationListUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (StationListAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: (() -> Unit)? = null,
) {
    LegacyYellowBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LegacyTopBar(
                    title = {
                        SortToggleTitle(
                            sortOrder = uiState.selectedSortOrder,
                            onClick = { onAction(StationListAction.SortToggleRequested) },
                        )
                    },
                    actions = {
                        if (onWatchlistClick != null) {
                            TextButton(
                                modifier = Modifier.semantics {
                                    contentDescription = "관심 비교"
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = ColorYellow,
                                ),
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
                    onOpenLocationSettings = onOpenLocationSettings,
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
}

@Composable
private fun SortToggleTitle(
    sortOrder: com.gasstation.domain.station.model.SortOrder,
    onClick: () -> Unit,
) {
    val typography = GasStationTheme.typography

    Text(
        text = sortOrder.toTitleLabel(),
        style = typography.topBarTitle,
        modifier = Modifier
            .semantics {
                stateDescription = sortOrder.toStateDescription()
            }
            .clickable(
                role = Role.Button,
                onClickLabel = sortOrder.toNextSortActionLabel(),
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun StationListContent(
    uiState: StationListUiState,
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val banners = StationListBannerModel.from(uiState)
    val spacing = GasStationTheme.spacing

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space12,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        items(
            items = banners,
            key = { banner -> banner.title + (banner.detail ?: "") },
        ) { banner ->
            LegacyStatusBanner(
                text = banner.title,
                detail = banner.detail,
                tone = banner.tone.toLegacyTone(),
            )
        }
        item {
            FilterSummary(uiState = uiState)
        }
        if (uiState.stations.isEmpty()) {
            item {
                EmptyState(onAction = onAction)
            }
        } else {
            items(uiState.stations, key = StationListItemUiModel::id) { station ->
                StationCard(
                    station = station,
                    fuelTypeLabel = uiState.selectedFuelType.toLabel(),
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
            }
        }
    }
}

@Composable
private fun FilterSummary(
    uiState: StationListUiState,
) {
    val spacing = GasStationTheme.spacing

    LegacyChromeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space16,
        ),
    ) {
        LegacySectionHeading(
            title = "현재 조건",
            subtitle = "반경과 유종 기준으로 정렬합니다.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
            FilterPill(text = uiState.selectedRadius.toLabel())
            FilterPill(text = uiState.selectedFuelType.toLabel())
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
) {
    val spacing = GasStationTheme.spacing
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val typography = GasStationTheme.typography

    Surface(
        color = ColorGray4,
        shape = RoundedCornerShape(corner.small),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .border(
                    width = stroke.default,
                    color = ColorBlack,
                    shape = RoundedCornerShape(corner.small),
                )
                .padding(
                    horizontal = spacing.space12,
                    vertical = spacing.space4,
                ),
            style = typography.chip,
            color = ColorBlack,
        )
    }
}

@Composable
private fun StationCard(
    station: StationListItemUiModel,
    fuelTypeLabel: String,
    onClick: () -> Unit,
    onWatchToggle: () -> Unit,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography

    LegacyChromeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space16,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.space16),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Row(
                    modifier = Modifier.testTag(STATION_LIST_METRIC_ROW_TAG),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space24),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MetricBlock(
                        label = "가격",
                        number = station.priceNumberLabel,
                        unit = station.priceUnitLabel,
                        emphasis = MetricEmphasis.Primary,
                    )
                    MetricBlock(
                        label = "거리",
                        number = station.distanceNumberLabel,
                        unit = station.distanceUnitLabel,
                        emphasis = MetricEmphasis.Secondary,
                    )
                }
                Text(
                    text = station.name,
                    modifier = Modifier.testTag(STATION_LIST_CARD_TITLE_TAG),
                    style = typography.cardTitle,
                    color = ColorBlack,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FuelChip(text = fuelTypeLabel)
                    Text(
                        text = station.brandLabel,
                        style = typography.meta,
                        color = ColorGray2,
                    )
                }
                Text(
                    text = station.priceDeltaLabel,
                    style = typography.body,
                    color = ColorGray2,
                )
            }
            WatchToggleButton(
                watched = station.isWatched,
                onClick = onWatchToggle,
            )
        }
    }
}

@Composable
private fun FuelChip(
    text: String,
) {
    val spacing = GasStationTheme.spacing
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val typography = GasStationTheme.typography

    Surface(
        color = ColorGray4,
        shape = RoundedCornerShape(corner.small),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .border(
                    width = stroke.default,
                    color = ColorBlack,
                    shape = RoundedCornerShape(corner.small),
                )
                .padding(
                    horizontal = spacing.space8,
                    vertical = spacing.space4,
                ),
            style = typography.chip,
            color = ColorBlack,
        )
    }
}

private enum class MetricEmphasis {
    Primary,
    Secondary,
}

@Composable
private fun MetricBlock(
    label: String,
    number: String,
    unit: String,
    emphasis: MetricEmphasis,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val numberStyle = when (emphasis) {
        MetricEmphasis.Primary -> typography.priceHero
        MetricEmphasis.Secondary -> typography.metricValue
    }
    val unitBottomPadding = when (emphasis) {
        MetricEmphasis.Primary -> 4.dp
        MetricEmphasis.Secondary -> 3.dp
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
        Text(
            text = label,
            style = typography.meta,
            color = ColorGray3,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                style = numberStyle,
                color = ColorBlack,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(
                    start = spacing.space4,
                    bottom = unitBottomPadding,
                ),
                style = typography.meta,
                color = ColorGray2,
            )
        }
    }
}

@Composable
private fun WatchToggleButton(
    watched: Boolean,
    onClick: () -> Unit,
) {
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val backgroundColor = if (watched) ColorBlack else ColorGray4
    val contentColor = if (watched) ColorYellow else ColorBlack

    IconButton(
        modifier = Modifier
            .size(42.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(corner.small))
            .border(
                width = stroke.emphasis,
                color = ColorBlack,
                shape = RoundedCornerShape(corner.small),
            )
            .semantics {
                selected = watched
                stateDescription = if (watched) {
                    "관심 주유소에 추가됨"
                } else {
                    "관심 주유소에 추가되지 않음"
                }
            },
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (watched) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = "관심 주유소 토글",
            tint = contentColor,
        )
    }
}

@Composable
private fun PermissionRequired(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit,
) {
    BrandedStateContainer(modifier = modifier) {
        StationListActionStateCard(
            title = "위치 권한이 필요합니다.",
            body = "주변 주유소를 찾고 거리순과 가격순 정렬을 사용하려면 위치 접근을 허용해주세요.",
            buttonLabel = "권한 요청",
            onClick = onRequestPermissions,
        )
    }
}

@Composable
private fun GpsRequired(
    modifier: Modifier = Modifier,
    onOpenLocationSettings: () -> Unit,
) {
    BrandedStateContainer(modifier = modifier) {
        StationListActionStateCard(
            title = "위치 서비스를 켜야 합니다.",
            body = "GPS 또는 네트워크 위치를 활성화해야 주변 주유소와 관심 비교를 정확하게 불러올 수 있습니다.",
            buttonLabel = "위치 설정 열기",
            onClick = onOpenLocationSettings,
        )
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    BrandedStateContainer(modifier = modifier) {
        LegacyChromeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = spacing.space16,
                vertical = spacing.space16,
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = ColorBlack,
                    strokeWidth = 3.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    Text(
                        text = "주변 주유소를 불러오는 중입니다.",
                        style = typography.sectionTitle,
                        color = ColorBlack,
                    )
                    Text(
                        text = "현재 조건을 유지한 채 최신 가격을 확인하고 있습니다.",
                        style = typography.body,
                        color = ColorGray2,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    onAction: (StationListAction) -> Unit,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    LegacyChromeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space16,
        ),
    ) {
        LegacySectionHeading(
            title = "주변 주유소가 없습니다.",
            subtitle = "반경이나 유종 조건을 유지한 채 다시 조회할 수 있습니다.",
        )
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorBlack,
                contentColor = ColorYellow,
            ),
            onClick = { onAction(StationListAction.RetryClicked) },
        ) {
            Text(
                text = "다시 시도",
                style = typography.body,
            )
        }
    }
}

@Composable
private fun BrandedStateContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val spacing = GasStationTheme.spacing
    Box(
        modifier = modifier.padding(spacing.space24),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun StationListActionStateCard(
    title: String,
    body: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    LegacyChromeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space16,
        ),
    ) {
        LegacySectionHeading(title = title, subtitle = body)
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorBlack,
                contentColor = ColorYellow,
            ),
            onClick = onClick,
        ) {
            Text(
                text = buttonLabel,
                style = typography.body,
            )
        }
    }
}

private fun StationListBannerTone.toLegacyTone(): LegacyStatusTone = when (this) {
    StationListBannerTone.Neutral -> LegacyStatusTone.Neutral
    StationListBannerTone.Info -> LegacyStatusTone.Info
    StationListBannerTone.Warning -> LegacyStatusTone.Warning
    StationListBannerTone.Error -> LegacyStatusTone.Error
}

private fun com.gasstation.domain.station.model.SortOrder.toTitleLabel(): String = when (this) {
    com.gasstation.domain.station.model.SortOrder.DISTANCE -> "정렬: 거리순"
    com.gasstation.domain.station.model.SortOrder.PRICE -> "정렬: 가격순"
}

private fun com.gasstation.domain.station.model.SortOrder.toStateDescription(): String = when (this) {
    com.gasstation.domain.station.model.SortOrder.DISTANCE -> "현재 거리순 정렬"
    com.gasstation.domain.station.model.SortOrder.PRICE -> "현재 가격순 정렬"
}

private fun com.gasstation.domain.station.model.SortOrder.toNextSortActionLabel(): String = when (this) {
    com.gasstation.domain.station.model.SortOrder.DISTANCE -> "가격순으로 정렬"
    com.gasstation.domain.station.model.SortOrder.PRICE -> "거리순으로 정렬"
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

private fun BrandFilter.toLabel(): String = when (this) {
    BrandFilter.ALL -> "전체"
    BrandFilter.SKE -> "SK에너지"
    BrandFilter.GSC -> "GS칼텍스"
    BrandFilter.HDO -> "현대오일뱅크"
    BrandFilter.SOL -> "S-OIL"
    BrandFilter.RTO -> "자영알뜰"
    BrandFilter.RTX -> "고속도로알뜰"
    BrandFilter.NHO -> "농협알뜰"
    BrandFilter.ETC -> "자가상표"
    BrandFilter.E1G -> "E1"
    BrandFilter.SKG -> "SK가스"
}
