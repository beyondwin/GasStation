package com.gasstation.core.designsystem.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.ColorSupportSuccess
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.GasStationTheme

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
        require(body == null || body.isNotBlank()) { "Status banner body must not be blank when provided." }
    }

    fun orderedSlots(): List<TextSlotRole> = buildList {
        add(TextSlotRole(StructuredTextSlot.Title, ChromeTextRole.BannerTitle))
        if (!body.isNullOrBlank()) {
            add(TextSlotRole(StructuredTextSlot.Body, ChromeTextRole.BannerBody))
        }
    }
}

internal enum class StatusBannerSymbolMark {
    Bar,
    Dot,
    Check,
    Triangle,
    Cross,
}

internal data class StatusBannerToneVisual(
    val surfaceColor: Color,
    val borderColor: Color,
    val contentColor: Color,
    val symbolContainerColor: Color,
    val symbolContentColor: Color,
    val symbolMark: StatusBannerSymbolMark,
)

@Composable
fun GasStationBackground(
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
fun GasStationTopBar(
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
fun GasStationCard(
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
fun GasStationSectionHeading(
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
fun GasStationStatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: GasStationStatusTone = GasStationStatusTone.Neutral,
) {
    val content = StatusBannerContent(
        title = text,
        body = detail?.takeIf(String::isNotBlank),
    )
    val visual = tone.visual()
    val corner = GasStationTheme.corner
    val spacing = GasStationTheme.spacing
    val stroke = GasStationTheme.stroke

    Surface(
        modifier = modifier,
        color = visual.surfaceColor,
        contentColor = visual.contentColor,
        shape = RoundedCornerShape(corner.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = stroke.default,
                    color = visual.borderColor,
                    shape = RoundedCornerShape(corner.medium),
                )
                .padding(
                    horizontal = spacing.space12,
                    vertical = spacing.space12,
                ),
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corner.small))
                    .background(visual.symbolContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                StatusBannerSymbol(visual = visual)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Text(
                    text = content.title,
                    style = ChromeTextRole.BannerTitle.style(),
                    color = visual.contentColor,
                )
                if (content.body != null) {
                    Text(
                        text = content.body,
                        style = ChromeTextRole.BannerBody.style(),
                        color = visual.contentColor.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBannerSymbol(
    visual: StatusBannerToneVisual,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val markColor = visual.symbolContentColor
        val strokeWidth = size.minDimension * 0.16f

        when (visual.symbolMark) {
            StatusBannerSymbolMark.Bar -> {
                drawLine(
                    color = markColor,
                    start = Offset(size.width * 0.24f, center.y),
                    end = Offset(size.width * 0.76f, center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Square,
                )
            }
            StatusBannerSymbolMark.Dot -> {
                drawCircle(
                    color = markColor,
                    radius = size.minDimension * 0.28f,
                    center = center,
                )
            }
            StatusBannerSymbolMark.Check -> {
                drawLine(
                    color = markColor,
                    start = Offset(size.width * 0.2f, size.height * 0.54f),
                    end = Offset(size.width * 0.42f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = markColor,
                    start = Offset(size.width * 0.42f, size.height * 0.76f),
                    end = Offset(size.width * 0.8f, size.height * 0.26f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Square,
                )
            }
            StatusBannerSymbolMark.Triangle -> {
                val triangle = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.18f)
                    lineTo(size.width * 0.84f, size.height * 0.78f)
                    lineTo(size.width * 0.16f, size.height * 0.78f)
                    close()
                }
                drawPath(path = triangle, color = markColor)
            }
            StatusBannerSymbolMark.Cross -> {
                drawLine(
                    color = markColor,
                    start = Offset(size.width * 0.24f, size.height * 0.24f),
                    end = Offset(size.width * 0.76f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = markColor,
                    start = Offset(size.width * 0.76f, size.height * 0.24f),
                    end = Offset(size.width * 0.24f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Square,
                )
            }
        }
    }
}

enum class GasStationStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
}

internal fun GasStationStatusTone.visual(): StatusBannerToneVisual = when (this) {
    GasStationStatusTone.Neutral -> StatusBannerToneVisual(
        surfaceColor = ColorWhite,
        borderColor = ColorBlack,
        contentColor = ColorBlack,
        symbolContainerColor = ColorGray2,
        symbolContentColor = ColorWhite,
        symbolMark = StatusBannerSymbolMark.Bar,
    )
    GasStationStatusTone.Info -> StatusBannerToneVisual(
        surfaceColor = Color(0xFFE9F2FF),
        borderColor = ColorSupportInfo,
        contentColor = ColorBlack,
        symbolContainerColor = ColorSupportInfo,
        symbolContentColor = ColorWhite,
        symbolMark = StatusBannerSymbolMark.Dot,
    )
    GasStationStatusTone.Success -> StatusBannerToneVisual(
        surfaceColor = Color(0xFFEAF7ED),
        borderColor = ColorSupportSuccess,
        contentColor = ColorBlack,
        symbolContainerColor = ColorSupportSuccess,
        symbolContentColor = ColorWhite,
        symbolMark = StatusBannerSymbolMark.Check,
    )
    GasStationStatusTone.Warning -> StatusBannerToneVisual(
        surfaceColor = Color(0xFFFFF3A3),
        borderColor = ColorBlack,
        contentColor = ColorBlack,
        symbolContainerColor = ColorBlack,
        symbolContentColor = ColorYellow,
        symbolMark = StatusBannerSymbolMark.Triangle,
    )
    GasStationStatusTone.Error -> StatusBannerToneVisual(
        surfaceColor = Color(0xFFFFEBEE),
        borderColor = ColorSupportError,
        contentColor = ColorBlack,
        symbolContainerColor = ColorSupportError,
        symbolContentColor = ColorWhite,
        symbolMark = StatusBannerSymbolMark.Cross,
    )
}

@Composable
internal fun ChromeTextRole.style(): androidx.compose.ui.text.TextStyle = when (this) {
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
