package com.gasstation.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.ColorSupportSuccess
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow

private val LegacyOuterShape = RoundedCornerShape(20.dp)
private val LegacyInnerShape = RoundedCornerShape(18.dp)

@Composable
fun LegacyYellowBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(ColorYellow),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ColorBlack,
            scrolledContainerColor = ColorBlack,
            navigationIconContentColor = ColorYellow,
            titleContentColor = ColorYellow,
            actionIconContentColor = ColorYellow,
        ),
        title = {
            ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                title()
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
fun LegacyChromeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = ColorBlack,
        shape = LegacyOuterShape,
    ) {
        Surface(
            modifier = Modifier.padding(2.dp),
            color = ColorWhite,
            shape = LegacyInnerShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
fun LegacySectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ColorBlack,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = ColorGray2,
            )
        }
    }
}

@Composable
fun LegacyStatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: LegacyStatusTone = LegacyStatusTone.Neutral,
) {
    val colors = tone.colors()

    Surface(
        modifier = modifier,
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 2.dp, color = ColorBlack, shape = RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.content,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.content.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun LegacyListRow(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable (() -> Unit))? = null,
    trailingContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val interactiveModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    LegacyChromeCard(
        modifier = modifier.then(interactiveModifier),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (overline != null) {
                    Text(
                        text = overline,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorGray3,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorBlack,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorGray2,
                    )
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.titleSmall,
                        color = ColorGray3,
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    trailingContent.invoke(this)
                }
            }
        }
    }
}

enum class LegacyStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
}

private data class LegacyStatusColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

private fun LegacyStatusTone.colors(): LegacyStatusColors = when (this) {
    LegacyStatusTone.Neutral -> LegacyStatusColors(
        container = ColorWhite,
        content = ColorBlack,
        accent = ColorGray2,
    )
    LegacyStatusTone.Info -> LegacyStatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportInfo,
    )
    LegacyStatusTone.Success -> LegacyStatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportSuccess,
    )
    LegacyStatusTone.Warning -> LegacyStatusColors(
        container = ColorWhite,
        content = ColorBlack,
        accent = ColorYellow,
    )
    LegacyStatusTone.Error -> LegacyStatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportError,
    )
}
