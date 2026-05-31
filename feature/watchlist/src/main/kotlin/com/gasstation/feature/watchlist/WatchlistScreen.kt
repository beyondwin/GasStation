package com.gasstation.feature.watchlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationBrandIcon
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
import com.gasstation.core.designsystem.component.GasStationSectionHeading
import com.gasstation.core.designsystem.component.GasStationSupportingInfo
import com.gasstation.core.designsystem.component.GasStationTopBar

internal const val WATCHLIST_CHANGE_VALUE_TAG = "watchlist-change-value"
internal const val WATCHLIST_DELTA_INDICATOR_TAG = "watchlist-delta-indicator"

@Composable
fun WatchlistScreen(uiState: WatchlistUiState, onCloseClick: () -> Unit) {
    val spacing = GasStationTheme.spacing

    GasStationBackground(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WATCHLIST_ROOT_TAG)
            .semantics {
                testTagsAsResourceId = true
            },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GasStationTopBar(
                    title = { Text(text = stringResource(R.string.watchlist_title)) },
                    actions = {
                        WatchlistTopBarAction(
                            contentDescription = stringResource(R.string.watchlist_close),
                            onClick = onCloseClick,
                        ) {
                            WatchlistCloseIcon()
                        }
                    },
                )
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = uiState.stations.isEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 180)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 220),
                            initialOffsetY = { it / 16 },
                        ) togetherWith fadeOut(animationSpec = tween(durationMillis = 140)) +
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 180),
                            targetOffsetY = { -it / 20 },
                        )
                },
                label = "watchlist-body",
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyWatchlist(
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = spacing.space16,
                            vertical = spacing.space12,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.space12),
                    ) {
                        items(uiState.stations, key = WatchlistItemUiModel::id) { station ->
                            val cardContentDescription = stringResource(R.string.watchlist_card_content_description)
                            val brandContentDescription =
                                stringResource(R.string.watchlist_brand_icon_content_description, station.brandLabel)
                            GasStationCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .testTag(WATCHLIST_CARD_TEST_TAG)
                                    .semantics {
                                        contentDescription = cardContentDescription
                                    },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                                    ) {
                                        GasStationMetricBlock(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.watchlist_label_price),
                                            number = station.priceNumberLabel,
                                            unit = station.priceUnitLabel,
                                            emphasis = GasStationMetricEmphasis.Primary,
                                        )
                                        GasStationMetricBlock(
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag(WATCHLIST_DISTANCE_METRIC_TAG),
                                            label = stringResource(R.string.watchlist_label_distance),
                                            number = station.distanceNumberLabel,
                                            unit = station.distanceUnitLabel,
                                            emphasis = GasStationMetricEmphasis.Secondary,
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                                        Text(
                                            text = station.name,
                                            style = GasStationTheme.typography.cardTitle,
                                            color = ColorBlack,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GasStationBrandIcon(
                                                brand = station.brand,
                                                contentDescription = brandContentDescription,
                                            )
                                            Text(
                                                text = station.brandLabel,
                                                style = GasStationTheme.typography.meta,
                                                color = ColorGray2,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.space16),
                                    ) {
                                        GasStationSupportingInfo(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.watchlist_label_change),
                                            value = station.priceLabel,
                                            valueModifier = Modifier.testTag(WATCHLIST_CHANGE_VALUE_TAG),
                                            trailingContent = {
                                                WatchlistDeltaIndicator(
                                                    label = station.priceDeltaLabel,
                                                    tone = station.priceDeltaTone,
                                                    modifier = Modifier.testTag(WATCHLIST_DELTA_INDICATOR_TAG),
                                                )
                                            },
                                        )
                                        GasStationSupportingInfo(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.watchlist_label_checked),
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
}

@Composable
private fun WatchlistTopBarAction(contentDescription: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            icon()
        }
    }
}

@Composable
private fun WatchlistCloseIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = size.minDimension * 0.16f
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.2f, y = size.height * 0.2f),
            end = center.copy(x = size.width * 0.8f, y = size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ColorYellow,
            start = center.copy(x = size.width * 0.8f, y = size.height * 0.2f),
            end = center.copy(x = size.width * 0.2f, y = size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WatchlistDeltaIndicator(label: String, tone: WatchlistPriceDeltaTone, modifier: Modifier = Modifier) {
    val typography = GasStationTheme.typography
    val color = tone.toColor()

    if (tone == WatchlistPriceDeltaTone.Neutral) {
        Text(
            text = "-",
            modifier = modifier,
            style = typography.body,
            color = color,
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (tone == WatchlistPriceDeltaTone.Rise) {
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
        )
    }
}

@Composable
private fun EmptyWatchlist(modifier: Modifier = Modifier) {
    val spacing = GasStationTheme.spacing
    Column(
        modifier = modifier
            .padding(horizontal = spacing.space16, vertical = spacing.space24)
            .animateContentSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        GasStationCard(modifier = Modifier.fillMaxWidth()) {
            GasStationSectionHeading(
                title = stringResource(R.string.watchlist_empty_title),
                subtitle = stringResource(R.string.watchlist_empty_subtitle),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                GasStationSupportingInfo(
                    label = stringResource(R.string.watchlist_empty_next_step_label),
                    value = stringResource(R.string.watchlist_empty_next_step_value),
                )
                GasStationSupportingInfo(
                    label = stringResource(R.string.watchlist_empty_purpose_label),
                    value = stringResource(R.string.watchlist_empty_purpose_value),
                )
            }
        }
    }
}
