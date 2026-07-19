package com.gasstation.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorLogoTile
import com.gasstation.core.designsystem.ColorNeutralLine
import com.gasstation.core.designsystem.ColorSurface
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.Brand

object UrbanSignalTokens {
    val mainLogoTileSize = 50.dp
    val mainLogoSize = 38.dp
    val mainRowMinHeight = 120.dp
    val compactLogoTileSize = 44.dp
    val compactLogoSize = 34.dp
    val compactRowMinHeight = 108.dp
    val filterMenuLogoTileSize = 32.dp
    val filterMenuLogoSize = 24.dp
    val minimumTouchTarget = 48.dp
}

@Composable
fun GasStationBrandLogoTile(
    brand: Brand,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tileSize: Dp = UrbanSignalTokens.mainLogoTileSize,
    logoSize: Dp = UrbanSignalTokens.mainLogoSize,
) {
    Surface(
        modifier = modifier.size(tileSize),
        color = ColorLogoTile,
        shape = RoundedCornerShape(GasStationTheme.corner.small),
        border = BorderStroke(1.dp, ColorNeutralLine),
    ) {
        Box(contentAlignment = Alignment.Center) {
            GasStationBrandIcon(
                brand = brand,
                contentDescription = contentDescription,
                size = logoSize,
            )
        }
    }
}

@Composable
fun GasStationSummaryStrip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier,
        color = ColorBlack,
        contentColor = ColorSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun GasStationComparisonRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
    leading: @Composable () -> Unit,
    primary: @Composable ColumnScope.() -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = primary,
        )
        trailing()
    }
}

@Composable
fun GasStationNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    NavigationBar(
        modifier = modifier,
        containerColor = ColorBlack,
        contentColor = ColorSurface,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun RowScope.GasStationNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "urban-signal-navigation-selection",
    )
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                },
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        alwaysShowLabel = label != null,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = ColorYellow,
            selectedTextColor = ColorYellow,
            unselectedIconColor = ColorSurface.copy(alpha = 0.72f),
            unselectedTextColor = ColorSurface.copy(alpha = 0.72f),
            disabledIconColor = ColorSurface.copy(alpha = 0.36f),
            disabledTextColor = ColorSurface.copy(alpha = 0.36f),
            indicatorColor = Color.Transparent,
        ),
    )
}
