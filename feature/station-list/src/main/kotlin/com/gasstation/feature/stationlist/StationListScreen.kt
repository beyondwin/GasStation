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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationBrandIcon
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationGuidanceCard
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
import com.gasstation.core.designsystem.component.GasStationStatusBanner
import com.gasstation.core.designsystem.component.GasStationStatusTone
import com.gasstation.core.designsystem.component.GasStationTopBar
import com.gasstation.core.designsystem.gasStationBrandFilterLabel
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.core.model.BrandFilter

internal const val STATION_LIST_METRIC_ROW_TAG = "station-list-metric-row"
internal const val STATION_LIST_CARD_TITLE_TAG = "station-list-card-title"
internal const val STATION_LIST_PRICE_CHANGE_TAG = "station-list-price-change"
internal const val STATION_LIST_PULL_REFRESH_TAG = "station-list-pull-refresh"
internal const val STATION_LIST_QUERY_CONTEXT_TAG = "station-list-query-context"
internal const val STATION_LIST_FUEL_CHIP_TAG = "station-list-fuel-chip"

private sealed interface StationListBodyState {
    data object PermissionRequired : StationListBodyState

    data object GpsRequired : StationListBodyState

    data object InitialLoading : StationListBodyState

    data class Failure(val reason: StationListFailureReason) : StationListBodyState

    data object Results : StationListBodyState
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
    GasStationBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
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

                    is StationListBodyState.Failure -> FailureState(
                        reason = bodyState.reason,
                        modifier = Modifier.fillMaxSize(),
                        onAction = onAction,
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
    sortOrder: com.gasstation.core.model.SortOrder,
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
            selected = sortOrder == com.gasstation.core.model.SortOrder.DISTANCE,
        )
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(stroke.default)
                .background(ColorYellow),
        )
        SortToggleSegment(
            label = "가격순",
            selected = sortOrder == com.gasstation.core.model.SortOrder.PRICE,
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
            GasStationStatusBanner(
                modifier = Modifier.animateContentSize(),
                text = banner.title,
                detail = banner.detail,
                tone = banner.tone.toStatusTone(),
            )
        }
        item {
            QueryContextSummary(
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
private fun QueryContextSummary(
    uiState: StationListUiState,
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val addressLabel = uiState.currentAddressLabel
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.toDongLevelAddressLabel()
    val conditionLabel = "${uiState.selectedRadius.toLabel()} · ${uiState.selectedFuelType.toLabel()} 기준"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATION_LIST_QUERY_CONTEXT_TAG)
            .padding(
                horizontal = spacing.space4,
                vertical = spacing.space4,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (addressLabel != null) {
            Text(
                text = addressLabel,
                style = typography.body.copy(fontWeight = FontWeight.Bold),
                color = ColorBlack,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = conditionLabel,
            style = typography.meta,
            color = ColorGray2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

    GasStationCard(
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
                        .fillMaxWidth()
                        .testTag(STATION_LIST_METRIC_ROW_TAG)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space16),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    GasStationMetricBlock(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                        label = "가격",
                        number = station.priceNumberLabel,
                        unit = station.priceUnitLabel,
                        emphasis = GasStationMetricEmphasis.Primary,
                    )
                    GasStationMetricBlock(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "거리",
                        number = station.distanceNumberLabel,
                        unit = station.distanceUnitLabel,
                        emphasis = GasStationMetricEmphasis.Secondary,
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
                        FuelChip(
                            text = fuelTypeLabel,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        GasStationBrandIcon(
                            brand = station.brand,
                            contentDescription = "${station.brandLabel} 브랜드",
                        )
                    }
                    PriceDeltaIndicator(
                        modifier = Modifier.testTag(STATION_LIST_PRICE_CHANGE_TAG),
                        label = station.priceDeltaLabel,
                        tone = station.priceDeltaTone,
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
private fun PriceDeltaIndicator(
    label: String,
    tone: PriceDeltaTone,
    modifier: Modifier = Modifier,
) {
    val typography = GasStationTheme.typography
    val color = tone.toColor()

    if (tone == PriceDeltaTone.Neutral) {
        Text(
            text = "-",
            modifier = modifier,
            style = typography.body,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (tone == PriceDeltaTone.Rise) {
                Icons.Filled.ArrowDropUp
            } else {
                Icons.Filled.ArrowDropDown
            },
            contentDescription = null,
            tint = color,
        )
        Text(
            text = label,
            style = typography.body,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FuelChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = GasStationTheme.spacing
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val typography = GasStationTheme.typography

    Surface(
        modifier = modifier.testTag(STATION_LIST_FUEL_CHIP_TAG),
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            modifier = Modifier.size(26.dp),
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
        GasStationGuidanceCard(
            title = "위치 권한이 필요합니다.",
            body = "주변 주유소를 찾고 거리순과 가격순 정렬을 사용하려면 위치 접근을 허용해주세요.",
            actionLabel = "권한 요청",
            onAction = onRequestPermissions,
        )
    }
}

@Composable
private fun GpsRequired(
    modifier: Modifier = Modifier,
    onOpenLocationSettings: () -> Unit,
) {
    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = "위치 서비스를 켜야 합니다.",
            body = "GPS 또는 네트워크 위치를 활성화해야 주변 주유소와 북마크를 정확하게 불러올 수 있습니다.",
            actionLabel = "위치 설정 열기",
            onAction = onOpenLocationSettings,
        )
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier,
) {
    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = "주변 주유소를 불러오는 중입니다.",
            body = "현재 조건으로 최신 가격을 확인하고 있습니다.",
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
private fun FailureState(
    reason: StationListFailureReason,
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = reason.toFailureCardContent()

    BrandedStateContainer(modifier = modifier) {
        GasStationGuidanceCard(
            title = content.title,
            body = content.body,
            actionLabel = "다시 시도",
            onAction = { onAction(StationListAction.RetryClicked) },
        )
    }
}

@Composable
private fun EmptyState(
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GasStationGuidanceCard(
        modifier = modifier,
        title = "조건에 맞는 주변 주유소가 없습니다.",
        body = "반경, 유종, 브랜드 조건을 조정하거나 다시 조회해보세요.",
        actionLabel = "다시 시도",
        onAction = { onAction(StationListAction.RetryClicked) },
    )
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
    val pullToRefreshState = rememberPullToRefreshState()
    val showTopLoadingRail = uiState.isRefreshing || uiState.isLoading
    val refreshRailInset = if (showTopLoadingRail) 58.dp else 0.dp

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
        Box(modifier = Modifier.fillMaxSize()) {
            StationListContent(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = refreshRailInset)
                    .alpha(if (uiState.isLoading) 0.82f else 1f),
            )

            AnimatedVisibility(
                visible = showTopLoadingRail,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = GasStationTheme.spacing.space16)
                    .padding(top = GasStationTheme.spacing.space12),
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 180),
                        initialOffsetY = { -it / 2 },
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                label = "station-list-refresh-rail",
            ) {
                RefreshingStatusRail()
            }
        }
    }
}

@Composable
private fun RefreshingStatusRail(
    modifier: Modifier = Modifier,
) {
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
                .padding(
                    horizontal = spacing.space12,
                    vertical = spacing.space8,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ColorYellow),
                )
                Text(
                    text = "가격 갱신 중",
                    style = typography.chip.copy(fontWeight = FontWeight.Bold),
                    color = ColorYellow,
                )
            }
            Text(
                text = "현재 조건으로 최신 가격을 확인하고 있습니다.",
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

private fun StationListUiState.toBodyState(): StationListBodyState = when {
    permissionState == LocationPermissionState.Denied &&
        !(hasDeniedLocationAccess && currentCoordinates != null) -> StationListBodyState.PermissionRequired
    !isGpsEnabled -> StationListBodyState.GpsRequired
    isLoading && stations.isEmpty() -> StationListBodyState.InitialLoading
    blockingFailure != null && stations.isEmpty() -> StationListBodyState.Failure(blockingFailure)
    else -> StationListBodyState.Results
}

private fun String.toDongLevelAddressLabel(): String {
    return toAddressTokens().toAdministrativeDongLabel() ?: this
}

private fun String.isAdministrativeDongPart(): Boolean {
    val normalized = trim('(', ')', '[', ']', ',', '.')
    return normalized.endsWith("동") && normalized.dropLast(1).any { it in '가'..'힣' }
}

private fun String.toAddressTokens(): List<String> =
    split(Regex("\\s+"))
        .asSequence()
        .map { it.trim('(', ')', '[', ']', ',', '.') }
        .filter(String::isNotBlank)
        .filterNot { it == "대한민국" || it.equals("KR", ignoreCase = true) }
        .toList()
        .joinSplitAdministrativeTokens()

private fun List<String>.joinSplitAdministrativeTokens(): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < size) {
        val current = this[index]
        val next = getOrNull(index + 1)
        if (next in setOf("특별시", "광역시", "특별자치시", "특별자치도")) {
            result += current + next
            index += 2
        } else {
            result += current
            index += 1
        }
    }
    return result
}

private fun List<String>.toAdministrativeDongLabel(): String? {
    val dongIndex = indexOfLast(String::isAdministrativeDongPart)
    if (dongIndex < 0) return null

    val districtIndex = findLastAdminIndexBefore(dongIndex, suffixes = listOf("구", "군"))
    val regionIndex = if (districtIndex >= 0) {
        findLastAdminIndexBefore(districtIndex, suffixes = listOf("시", "도"))
            .takeIf { it >= 0 } ?: findFallbackRegionIndexBefore(districtIndex)
    } else {
        findLastAdminIndexBefore(dongIndex, suffixes = listOf("시", "도"))
    }

    return listOf(regionIndex, districtIndex, dongIndex)
        .filter { it >= 0 }
        .distinct()
        .map(::get)
        .joinToString(separator = " ")
        .takeIf(String::isNotBlank)
}

private fun List<String>.findLastAdminIndexBefore(
    endExclusive: Int,
    suffixes: List<String>,
): Int = asSequence()
    .take(endExclusive)
    .withIndex()
    .filter { (_, token) -> suffixes.any(token::endsWith) && token.dropLast(1).any { it in '가'..'힣' } }
    .lastOrNull()
    ?.index ?: -1

private fun List<String>.findFallbackRegionIndexBefore(endExclusive: Int): Int =
    asSequence()
        .take(endExclusive)
        .withIndex()
        .filter { (_, token) -> token in setOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종") }
        .lastOrNull()
        ?.index ?: -1

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

private fun StationListBannerTone.toStatusTone(): GasStationStatusTone = when (this) {
    StationListBannerTone.Neutral -> GasStationStatusTone.Neutral
    StationListBannerTone.Info -> GasStationStatusTone.Info
    StationListBannerTone.Warning -> GasStationStatusTone.Warning
    StationListBannerTone.Error -> GasStationStatusTone.Error
}

private fun com.gasstation.core.model.SortOrder.toStateDescription(): String = when (this) {
    com.gasstation.core.model.SortOrder.DISTANCE -> "현재 거리순 정렬"
    com.gasstation.core.model.SortOrder.PRICE -> "현재 가격순 정렬"
}

private fun com.gasstation.core.model.SortOrder.toNextSortActionLabel(): String = when (this) {
    com.gasstation.core.model.SortOrder.DISTANCE -> "가격순으로 정렬"
    com.gasstation.core.model.SortOrder.PRICE -> "거리순으로 정렬"
}

private fun com.gasstation.core.model.SearchRadius.toLabel(): String = when (this) {
    com.gasstation.core.model.SearchRadius.KM_3 -> "3km"
    com.gasstation.core.model.SearchRadius.KM_4 -> "4km"
    com.gasstation.core.model.SearchRadius.KM_5 -> "5km"
}

private fun com.gasstation.core.model.FuelType.toLabel(): String = when (this) {
    com.gasstation.core.model.FuelType.GASOLINE -> "휘발유"
    com.gasstation.core.model.FuelType.DIESEL -> "경유"
    com.gasstation.core.model.FuelType.PREMIUM_GASOLINE -> "고급휘발유"
    com.gasstation.core.model.FuelType.KEROSENE -> "등유"
    com.gasstation.core.model.FuelType.LPG -> "LPG"
}

private fun BrandFilter.toLabel(): String = gasStationBrandFilterLabel()

private data class StationListFailureCardContent(
    val title: String,
    val body: String,
)

private fun StationListFailureReason.toFailureCardContent(): StationListFailureCardContent = when (this) {
    StationListFailureReason.LocationTimedOut -> StationListFailureCardContent(
        title = "위치를 확인하는 데 시간이 오래 걸리고 있습니다.",
        body = "GPS 신호와 네트워크 상태를 확인한 뒤 다시 시도해주세요.",
    )

    StationListFailureReason.LocationFailed -> StationListFailureCardContent(
        title = "현재 위치를 확인하지 못했습니다.",
        body = "위치 권한과 위치 서비스 상태를 확인한 뒤 다시 시도해주세요.",
    )

    StationListFailureReason.RefreshTimedOut -> StationListFailureCardContent(
        title = "주변 주유소를 불러오지 못했습니다.",
        body = "서버 응답이 늦습니다. 잠시 후 같은 조건으로 다시 시도해주세요.",
    )

    StationListFailureReason.RefreshFailed -> StationListFailureCardContent(
        title = "주변 주유소를 불러오지 못했습니다.",
        body = "네트워크 또는 서버 상태를 확인한 뒤 다시 시도해주세요.",
    )
}
