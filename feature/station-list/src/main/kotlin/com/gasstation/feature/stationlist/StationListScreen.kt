package com.gasstation.feature.stationlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.tween
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.GasStationTheme
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

private enum class StationListBodyState {
    PermissionRequired,
    GpsRequired,
    InitialLoading,
    Results,
}

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
                            IconButton(
                                modifier = Modifier.semantics {
                                    contentDescription = "북마크"
                                },
                                onClick = onWatchlistClick,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                )
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
            AnimatedContent(
                targetState = uiState.toBodyState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = { subtleContentTransform() },
                label = "station-list-body",
            ) { bodyState ->
                when (bodyState) {
                    StationListBodyState.PermissionRequired -> PermissionRequired(
                        modifier = Modifier.fillMaxSize(),
                        onRequestPermissions = onRequestPermissions,
                    )

                    StationListBodyState.GpsRequired -> GpsRequired(
                        modifier = Modifier.fillMaxSize(),
                        onOpenLocationSettings = onOpenLocationSettings,
                    )

                    StationListBodyState.InitialLoading -> LoadingState(
                        modifier = Modifier.fillMaxSize(),
                    )

                    StationListBodyState.Results -> StationListResultsPane(
                        uiState = uiState,
                        onAction = onAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortToggleTitle(
    sortOrder: com.gasstation.domain.station.model.SortOrder,
    onClick: () -> Unit,
) {
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val shape = RoundedCornerShape(corner.small)

    Row(
        modifier = Modifier
            .clip(shape)
            .border(
                width = stroke.default,
                color = ColorYellow,
                shape = shape,
            )
            .semantics {
                stateDescription = sortOrder.toStateDescription()
            }
            .clickable(
                role = Role.Button,
                onClickLabel = sortOrder.toNextSortActionLabel(),
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortToggleSegment(
            label = "거리순",
            selected = sortOrder == com.gasstation.domain.station.model.SortOrder.DISTANCE,
        )
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(stroke.default)
                .background(ColorYellow),
        )
        SortToggleSegment(
            label = "가격순",
            selected = sortOrder == com.gasstation.domain.station.model.SortOrder.PRICE,
        )
    }
}

@Composable
private fun SortToggleSegment(
    label: String,
    selected: Boolean,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography

    Surface(
        color = if (selected) ColorYellow else ColorBlack,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = spacing.space12,
                vertical = spacing.space8,
            ),
            style = typography.chip.copy(fontWeight = FontWeight.Bold),
            color = if (selected) ColorBlack else ColorYellow,
        )
    }
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
                modifier = Modifier.animateContentSize(),
                text = banner.title,
                detail = banner.detail,
                tone = banner.tone.toLegacyTone(),
            )
        }
        item {
            FilterSummary(
                uiState = uiState,
                modifier = Modifier.animateContentSize(),
            )
        }
        if (uiState.stations.isEmpty()) {
            item {
                EmptyState(
                    onAction = onAction,
                    modifier = Modifier.animateContentSize(),
                )
            }
        } else {
            items(uiState.stations, key = StationListItemUiModel::id) { station ->
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
            }
        }
    }
}

@Composable
private fun FilterSummary(
    uiState: StationListUiState,
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing

    LegacyChromeCard(
        modifier = modifier.fillMaxWidth(),
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
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val typography = GasStationTheme.typography
    val shape = RoundedCornerShape(corner.small)

    Surface(
        modifier = modifier,
        color = ColorGray4,
        shape = shape,
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = stroke.default,
                    color = ColorBlack,
                    shape = shape,
                )
                .height(28.dp)
                .padding(horizontal = spacing.space12),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = typography.chip,
                color = ColorBlack,
            )
        }
    }
}

@Composable
private fun StationCard(
    station: StationListItemUiModel,
    fuelTypeLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onWatchToggle: () -> Unit,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography

    LegacyChromeCard(
        modifier = modifier
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
                    modifier = Modifier
                        .testTag(STATION_LIST_METRIC_ROW_TAG)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space24),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MetricBlock(
                        modifier = Modifier.fillMaxHeight(),
                        label = "가격",
                        number = station.priceNumberLabel,
                        unit = station.priceUnitLabel,
                        emphasis = MetricEmphasis.Primary,
                    )
                    MetricBlock(
                        modifier = Modifier.fillMaxHeight(),
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                        color = station.priceDeltaTone.toColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
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
    val iconTint = animateColorAsState(
        targetValue = if (watched) ColorYellow else ColorGray2,
        label = "watch-toggle-icon",
    )

    IconButton(
        modifier = Modifier
            .semantics {
                selected = watched
                stateDescription = if (watched) {
                    "저장됨"
                } else {
                    "저장되지 않음"
                }
            },
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (watched) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = "저장",
            modifier = Modifier.size(22.dp),
            tint = iconTint.value,
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
            body = "GPS 또는 네트워크 위치를 활성화해야 주변 주유소와 북마크를 정확하게 불러올 수 있습니다.",
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
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    LegacyChromeCard(
        modifier = modifier.fillMaxWidth(),
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
private fun StationListResultsPane(
    uiState: StationListUiState,
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        StationListContent(
            uiState = uiState,
            onAction = onAction,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (uiState.isLoading) 0.82f else 1f),
        )

        AnimatedVisibility(
            visible = uiState.isLoading || uiState.isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = GasStationTheme.spacing.space16)
                .padding(top = GasStationTheme.spacing.space12),
            enter = fadeIn(animationSpec = tween(durationMillis = 160)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 180),
                    initialOffsetY = { -it / 2 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 160),
                    targetOffsetY = { -it / 3 },
                ),
            label = "station-list-loading-overlay",
        ) {
            RefreshingOverlayCard()
        }
    }
}

@Composable
private fun RefreshingOverlayCard() {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography

    LegacyChromeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = spacing.space12,
            vertical = spacing.space12,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = ColorBlack,
                    strokeWidth = 2.5.dp,
                )
                Text(
                    text = "주변 주유소를 불러오는 중입니다.",
                    style = typography.body.copy(fontWeight = FontWeight.Bold),
                    color = ColorBlack,
                )
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = ColorBlack,
                trackColor = ColorGray4,
            )
        }
    }
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

private fun StationListUiState.toBodyState(): StationListBodyState = when {
    permissionState == LocationPermissionState.Denied -> StationListBodyState.PermissionRequired
    !isGpsEnabled -> StationListBodyState.GpsRequired
    isLoading && stations.isEmpty() -> StationListBodyState.InitialLoading
    else -> StationListBodyState.Results
}

private fun subtleContentTransform(): ContentTransform = fadeIn(
    animationSpec = tween(durationMillis = 180),
) + slideInVertically(
    animationSpec = tween(durationMillis = 220),
    initialOffsetY = { it / 14 },
) togetherWith fadeOut(
    animationSpec = tween(durationMillis = 140),
) + slideOutVertically(
    animationSpec = tween(durationMillis = 180),
    targetOffsetY = { -it / 18 },
)

internal fun PriceDeltaTone.toColor(): Color = when (this) {
    PriceDeltaTone.Rise -> ColorSupportError
    PriceDeltaTone.Fall -> ColorSupportInfo
    PriceDeltaTone.Neutral -> ColorGray2
}

private fun StationListBannerTone.toLegacyTone(): LegacyStatusTone = when (this) {
    StationListBannerTone.Neutral -> LegacyStatusTone.Neutral
    StationListBannerTone.Info -> LegacyStatusTone.Info
    StationListBannerTone.Warning -> LegacyStatusTone.Warning
    StationListBannerTone.Error -> LegacyStatusTone.Error
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
