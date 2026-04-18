package com.gasstation.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.ColorSupportSuccess
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow

enum class MaterialTypographySlot {
    TitleLarge,
    TitleMedium,
    TitleSmall,
    BodyMedium,
    LabelLarge,
    LabelMedium,
    LabelSmall,
}

enum class ChromeTextRole(
    val fallbackMaterialSlot: MaterialTypographySlot,
) {
    TopBarTitle(MaterialTypographySlot.TitleLarge),
    SectionTitle(MaterialTypographySlot.TitleMedium),
    CardTitle(MaterialTypographySlot.TitleMedium),
    PriceHero(MaterialTypographySlot.LabelLarge),
    MetricValue(MaterialTypographySlot.LabelMedium),
    Body(MaterialTypographySlot.BodyMedium),
    Meta(MaterialTypographySlot.LabelSmall),
    Chip(MaterialTypographySlot.LabelSmall),
    BannerTitle(MaterialTypographySlot.TitleSmall),
    BannerBody(MaterialTypographySlot.LabelSmall),
    ;

    fun isProminentNumericEmphasis(): Boolean = this == PriceHero || this == MetricValue
}

enum class StructuredTextSlot {
    Title,
    Body,
}

data class TextSlotRole(
    val slot: StructuredTextSlot,
    val role: ChromeTextRole,
)

enum class ChromeCardSection {
    Header,
    PrimaryMetric,
    SupportingInfo,
    Actions,
}

data class ChromeCardStructure(
    val hasHeader: Boolean = false,
    val hasPrimaryMetric: Boolean = false,
    val hasSupportingInfo: Boolean = false,
    val hasActions: Boolean = false,
) {
    fun orderedSections(): List<ChromeCardSection> = buildList {
        if (hasHeader) add(ChromeCardSection.Header)
        if (hasPrimaryMetric) add(ChromeCardSection.PrimaryMetric)
        if (hasSupportingInfo) add(ChromeCardSection.SupportingInfo)
        if (hasActions) add(ChromeCardSection.Actions)
    }
}

data class StatusBannerContent(
    val title: String,
    val body: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Status banner title is required." }
    }

    fun orderedSlots(): List<TextSlotRole> = buildList {
        add(TextSlotRole(StructuredTextSlot.Title, ChromeTextRole.BannerTitle))
        if (!body.isNullOrBlank()) {
            add(TextSlotRole(StructuredTextSlot.Body, ChromeTextRole.BannerBody))
        }
    }
}

@Composable
fun YellowBackground(
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
fun TopBar(
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
            ProvideTextStyle(ChromeTextRole.TopBarTitle.style()) {
                title()
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
fun ChromeCard(
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
fun SectionHeading(
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
            style = ChromeTextRole.SectionTitle.style(),
            color = ColorBlack,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = ChromeTextRole.Body.style(),
                color = ColorGray2,
            )
        }
    }
}

@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: StatusTone = StatusTone.Neutral,
) {
    val content = StatusBannerContent(
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
                    style = ChromeTextRole.BannerTitle.style(),
                    color = colors.content,
                )
                if (content.body != null) {
                    Text(
                        text = content.body,
                        style = ChromeTextRole.BannerBody.style(),
                        color = colors.content.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

enum class StatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
}

private data class StatusColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

private fun StatusTone.colors(): StatusColors = when (this) {
    StatusTone.Neutral -> StatusColors(
        container = ColorWhite,
        content = ColorBlack,
        accent = ColorGray2,
    )
    StatusTone.Info -> StatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportInfo,
    )
    StatusTone.Success -> StatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportSuccess,
    )
    StatusTone.Warning -> StatusColors(
        container = ColorWhite,
        content = ColorBlack,
        accent = ColorYellow,
    )
    StatusTone.Error -> StatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportError,
    )
}

@Composable
private fun ChromeTextRole.style(): androidx.compose.ui.text.TextStyle = when (this) {
    ChromeTextRole.TopBarTitle -> GasStationTheme.typography.topBarTitle
    ChromeTextRole.SectionTitle -> GasStationTheme.typography.sectionTitle
    ChromeTextRole.CardTitle -> GasStationTheme.typography.cardTitle
    ChromeTextRole.PriceHero -> GasStationTheme.typography.priceHero
    ChromeTextRole.MetricValue -> GasStationTheme.typography.metricValue
    ChromeTextRole.Body -> GasStationTheme.typography.body
    ChromeTextRole.Meta -> GasStationTheme.typography.meta
    ChromeTextRole.Chip -> GasStationTheme.typography.chip
    ChromeTextRole.BannerTitle -> GasStationTheme.typography.bannerTitle
    ChromeTextRole.BannerBody -> GasStationTheme.typography.bannerBody
}
