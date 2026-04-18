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
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.ColorSupportSuccess
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow

enum class LegacyMaterialTypographySlot {
    TitleLarge,
    TitleMedium,
    TitleSmall,
    BodyMedium,
    LabelLarge,
    LabelMedium,
    LabelSmall,
}

enum class LegacyChromeTextRole(
    val fallbackMaterialSlot: LegacyMaterialTypographySlot,
) {
    TopBarTitle(LegacyMaterialTypographySlot.TitleLarge),
    SectionTitle(LegacyMaterialTypographySlot.TitleMedium),
    CardTitle(LegacyMaterialTypographySlot.TitleMedium),
    PriceHero(LegacyMaterialTypographySlot.LabelLarge),
    MetricValue(LegacyMaterialTypographySlot.LabelMedium),
    Body(LegacyMaterialTypographySlot.BodyMedium),
    Meta(LegacyMaterialTypographySlot.LabelSmall),
    Chip(LegacyMaterialTypographySlot.LabelSmall),
    BannerTitle(LegacyMaterialTypographySlot.TitleSmall),
    BannerBody(LegacyMaterialTypographySlot.LabelSmall),
    ;

    fun isProminentNumericEmphasis(): Boolean = this == PriceHero || this == MetricValue
}

enum class LegacyStructuredTextSlot {
    Overline,
    Title,
    Subtitle,
    Body,
    Meta,
}

data class LegacyTextSlotRole(
    val slot: LegacyStructuredTextSlot,
    val role: LegacyChromeTextRole,
)

enum class LegacyChromeCardSection {
    Header,
    PrimaryMetric,
    SupportingInfo,
    Actions,
}

data class LegacyChromeCardStructure(
    val hasHeader: Boolean = false,
    val hasPrimaryMetric: Boolean = false,
    val hasSupportingInfo: Boolean = false,
    val hasActions: Boolean = false,
) {
    fun orderedSections(): List<LegacyChromeCardSection> = buildList {
        if (hasHeader) add(LegacyChromeCardSection.Header)
        if (hasPrimaryMetric) add(LegacyChromeCardSection.PrimaryMetric)
        if (hasSupportingInfo) add(LegacyChromeCardSection.SupportingInfo)
        if (hasActions) add(LegacyChromeCardSection.Actions)
    }
}

data class LegacyStatusBannerContent(
    val title: String,
    val body: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Status banner title is required." }
    }

    fun orderedSlots(): List<LegacyTextSlotRole> = buildList {
        add(LegacyTextSlotRole(LegacyStructuredTextSlot.Title, LegacyChromeTextRole.BannerTitle))
        if (!body.isNullOrBlank()) {
            add(LegacyTextSlotRole(LegacyStructuredTextSlot.Body, LegacyChromeTextRole.BannerBody))
        }
    }
}

data class LegacyListRowContent(
    val title: String,
    val overline: String? = null,
    val subtitle: String? = null,
    val meta: String? = null,
) {
    init {
        require(title.isNotBlank()) { "List row title is required." }
    }

    fun orderedSlots(): List<LegacyTextSlotRole> = buildList {
        if (!overline.isNullOrBlank()) {
            add(LegacyTextSlotRole(LegacyStructuredTextSlot.Overline, LegacyChromeTextRole.Meta))
        }
        add(LegacyTextSlotRole(LegacyStructuredTextSlot.Title, LegacyChromeTextRole.CardTitle))
        if (!subtitle.isNullOrBlank()) {
            add(LegacyTextSlotRole(LegacyStructuredTextSlot.Subtitle, LegacyChromeTextRole.Body))
        }
        if (!meta.isNullOrBlank()) {
            add(LegacyTextSlotRole(LegacyStructuredTextSlot.Meta, LegacyChromeTextRole.Meta))
        }
    }
}

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
    val corner = GasStationTheme.corner
    TopAppBar(
        modifier = modifier.clip(RoundedCornerShape(bottomStart = corner.large, bottomEnd = corner.large)),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ColorBlack,
            scrolledContainerColor = ColorBlack,
            navigationIconContentColor = ColorYellow,
            titleContentColor = ColorYellow,
            actionIconContentColor = ColorYellow,
        ),
        title = {
            ProvideTextStyle(LegacyChromeTextRole.TopBarTitle.style()) {
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
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val spacing = GasStationTheme.spacing

    Surface(
        modifier = modifier,
        color = ColorBlack,
        shape = RoundedCornerShape(corner.large),
    ) {
        Surface(
            modifier = Modifier.padding(stroke.default),
            color = ColorWhite,
            shape = RoundedCornerShape(corner.medium),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
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
    val spacing = GasStationTheme.spacing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text = title,
            style = LegacyChromeTextRole.SectionTitle.style(),
            color = ColorBlack,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = LegacyChromeTextRole.Body.style(),
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
    val content = LegacyStatusBannerContent(
        title = text,
        body = detail,
    )
    val colors = tone.colors()
    val corner = GasStationTheme.corner
    val spacing = GasStationTheme.spacing
    val stroke = GasStationTheme.stroke

    Surface(
        modifier = modifier,
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(corner.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = stroke.default,
                    color = ColorBlack,
                    shape = RoundedCornerShape(corner.medium),
                )
                .padding(
                    horizontal = spacing.space12,
                    vertical = spacing.space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent),
            )
            Spacer(modifier = Modifier.width(spacing.space12))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                Text(
                    text = content.title,
                    style = LegacyChromeTextRole.BannerTitle.style(),
                    color = colors.content,
                )
                if (content.body != null) {
                    Text(
                        text = content.body,
                        style = LegacyChromeTextRole.BannerBody.style(),
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
    val content = LegacyListRowContent(
        title = title,
        overline = overline,
        subtitle = subtitle,
        meta = meta,
    )
    val spacing = GasStationTheme.spacing
    val interactiveModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    LegacyChromeCard(
        modifier = modifier.then(interactiveModifier),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space12,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(spacing.space12))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                if (content.overline != null) {
                    Text(
                        text = content.overline,
                        style = LegacyChromeTextRole.Meta.style(),
                        color = ColorGray3,
                    )
                }
                Text(
                    text = content.title,
                    style = LegacyChromeTextRole.CardTitle.style(),
                    color = ColorBlack,
                )
                if (content.subtitle != null) {
                    Text(
                        text = content.subtitle,
                        style = LegacyChromeTextRole.Body.style(),
                        color = ColorGray2,
                    )
                }
                if (content.meta != null) {
                    Text(
                        text = content.meta,
                        style = LegacyChromeTextRole.Meta.style(),
                        color = ColorGray3,
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(spacing.space16))
                Column(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(spacing.space8),
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

@Composable
private fun LegacyChromeTextRole.style(): androidx.compose.ui.text.TextStyle = when (this) {
    LegacyChromeTextRole.TopBarTitle -> GasStationTheme.typography.topBarTitle
    LegacyChromeTextRole.SectionTitle -> GasStationTheme.typography.sectionTitle
    LegacyChromeTextRole.CardTitle -> GasStationTheme.typography.cardTitle
    LegacyChromeTextRole.PriceHero -> GasStationTheme.typography.priceHero
    LegacyChromeTextRole.MetricValue -> GasStationTheme.typography.metricValue
    LegacyChromeTextRole.Body -> GasStationTheme.typography.body
    LegacyChromeTextRole.Meta -> GasStationTheme.typography.meta
    LegacyChromeTextRole.Chip -> GasStationTheme.typography.chip
    LegacyChromeTextRole.BannerTitle -> GasStationTheme.typography.bannerTitle
    LegacyChromeTextRole.BannerBody -> GasStationTheme.typography.bannerBody
}
