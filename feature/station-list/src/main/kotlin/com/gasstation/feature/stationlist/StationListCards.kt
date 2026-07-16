package com.gasstation.feature.stationlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.ColorSupportInfoOnDark
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBrandLogoTile
import com.gasstation.core.designsystem.component.GasStationComparisonRow
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
import com.gasstation.core.designsystem.component.UrbanSignalTokens

internal const val STATION_LIST_ROW_TAG = "station-list-row"
internal const val STATION_LIST_METRIC_ROW_TAG = "station-list-metric-row"
internal const val STATION_LIST_CARD_TITLE_TAG = "station-list-card-title"
internal const val STATION_LIST_PRICE_CHANGE_TAG = "station-list-price-change"
internal const val STATION_LIST_FUEL_CHIP_TAG = "station-list-fuel-chip"

@Composable
internal fun StationCard(
    station: StationListItemUiModel,
    fuelTypeLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onWatchToggle: () -> Unit,
) {
    GasStationComparisonRow(
        modifier = modifier
            .heightIn(min = UrbanSignalTokens.mainRowMinHeight)
            .testTag(STATION_LIST_ROW_TAG)
            .clickable(onClick = onClick),
        leading = {
            GasStationBrandLogoTile(
                brand = station.brand,
                contentDescription = stringResource(
                    R.string.station_list_brand_description,
                    station.brandLabel,
                ),
            )
        },
        primary = {
            GasStationMetricBlock(
                label = stringResource(R.string.station_list_label_price),
                number = station.priceNumberLabel,
                unit = station.priceUnitLabel,
                emphasis = GasStationMetricEmphasis.Primary,
                modifier = Modifier.testTag(STATION_LIST_METRIC_ROW_TAG),
            )
            Text(
                text = station.name,
                modifier = Modifier.testTag(STATION_LIST_CARD_TITLE_TAG),
                style = GasStationTheme.typography.cardTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StationRowMetadata(station = station, fuelTypeLabel = fuelTypeLabel)
        },
        trailing = {
            Column(
                modifier = Modifier.widthIn(max = 76.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = station.distanceLabel,
                    style = GasStationTheme.typography.metricValue,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                WatchToggleButton(station.isWatched, onWatchToggle)
            }
        },
    )
}

@Composable
private fun StationRowMetadata(station: StationListItemUiModel, fuelTypeLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelChip(
            text = fuelTypeLabel,
            modifier = Modifier.weight(1f, fill = false),
        )
        PriceDeltaIndicator(
            label = station.priceDeltaLabel,
            tone = station.priceDeltaTone,
            modifier = Modifier.testTag(STATION_LIST_PRICE_CHANGE_TAG),
        )
    }
}

@Composable
private fun PriceDeltaIndicator(label: String, tone: PriceDeltaTone, modifier: Modifier = Modifier) {
    val typography = GasStationTheme.typography
    val stockColor = tone.toColor()
    val darkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = when (tone) {
        PriceDeltaTone.Rise -> if (darkCanvas) MaterialTheme.colorScheme.onErrorContainer else stockColor

        PriceDeltaTone.Fall -> if (darkCanvas) {
            ColorSupportInfoOnDark
        } else {
            stockColor
        }

        PriceDeltaTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
private fun FuelChip(text: String, modifier: Modifier = Modifier) {
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
private fun WatchToggleButton(watched: Boolean, onClick: () -> Unit) {
    val iconTint = animateColorAsState(
        targetValue = if (watched) ColorYellow else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "watch-toggle-icon",
    )
    val watchSavedLabel = stringResource(R.string.station_list_watch_saved)
    val watchNotSavedLabel = stringResource(R.string.station_list_watch_not_saved)
    val watchActionLabel = stringResource(R.string.station_list_watch_action)

    IconButton(
        modifier = Modifier
            .testTag(STATION_LIST_WATCH_TOGGLE_TAG)
            .semantics {
                contentDescription = watchActionLabel
                selected = watched
                stateDescription = if (watched) {
                    watchSavedLabel
                } else {
                    watchNotSavedLabel
                }
            },
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (watched) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = iconTint.value,
        )
    }
}

internal fun PriceDeltaTone.toColor(): Color = when (this) {
    PriceDeltaTone.Rise -> ColorSupportError
    PriceDeltaTone.Fall -> ColorSupportInfo
    PriceDeltaTone.Neutral -> ColorGray2
}
