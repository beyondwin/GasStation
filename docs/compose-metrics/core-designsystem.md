# Compose Stability Metrics — core:designsystem

> 측정 환경: AGP 9.1.1 / Kotlin 2.3.20 / Compose Compiler 2.3.20
> 생성 명령: `./gradlew :core:designsystem:assembleDebug`
> 측정일: 2026-05-11

## Classes

```
stable class com.gasstation.core.designsystem.GasStationThemeDefaults {
  stable val dynamicColor: Boolean
  stable val legacyYellow: Color
  stable val legacyBlack: Color
  stable val statusBarStyle: GasStationStatusBarStyle
  stable val typography: GasStationTypography
  stable val spacing: GasStationSpacing
  stable val corner: GasStationCorner
  stable val stroke: GasStationStroke
  stable val iconSize: GasStationIconSize
  stable val materialTypography: Typography
  stable val lightColorScheme: ColorScheme
  stable val darkColorScheme: ColorScheme
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.GasStationStatusBarStyle {
  stable val backgroundColor: Color
  stable val useDarkIcons: Boolean
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.GasStationTheme {
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.GasStationTypography {
  stable val topBarTitle: TextStyle
  stable val sectionTitle: TextStyle
  stable val cardTitle: TextStyle
  stable val priceHero: TextStyle
  stable val metricValue: TextStyle
  stable val body: TextStyle
  stable val meta: TextStyle
  stable val chip: TextStyle
  stable val bannerTitle: TextStyle
  stable val bannerBody: TextStyle
}
stable class com.gasstation.core.designsystem.GasStationSpacing {
  stable val space4: Dp
  stable val space8: Dp
  stable val space12: Dp
  stable val space16: Dp
  stable val space24: Dp
}
stable class com.gasstation.core.designsystem.GasStationCorner {
  stable val small: Dp
  stable val medium: Dp
  stable val large: Dp
}
stable class com.gasstation.core.designsystem.GasStationStroke {
  stable val default: Dp
  stable val emphasis: Dp
}
stable class com.gasstation.core.designsystem.GasStationIconSize {
  stable val topBarAction: Dp
  stable val trailingAction: Dp
  stable val status: Dp
}
stable class com.gasstation.core.designsystem.component.TextSlotRole {
  stable val slot: StructuredTextSlot
  stable val role: ChromeTextRole
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.ChromeCardStructure {
  stable val hasHeader: Boolean
  stable val hasPrimaryMetric: Boolean
  stable val hasSupportingInfo: Boolean
  stable val hasActions: Boolean
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.StatusBannerContent {
  stable val title: String
  stable val body: String?
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.StatusBannerToneVisual {
  stable val surfaceColor: Color
  stable val borderColor: Color
  stable val contentColor: Color
  stable val symbolContainerColor: Color
  stable val symbolContentColor: Color
  stable val symbolMark: StatusBannerSymbolMark
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.GuidanceCardContent {
  stable val title: String
  stable val body: String
  stable val actionLabel: String?
  stable val hasLeadingContent: Boolean
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.SupportingInfoSlotRole {
  stable val slot: SupportingInfoSlot
  stable val role: ChromeTextRole
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.SupportingInfoContent {
  stable val label: String
  stable val value: String
  stable val hasTrailingContent: Boolean
  <runtime stability> = Stable
}
stable class com.gasstation.core.designsystem.component.GasStationRowContent {
  stable val title: String
  stable val value: String?
  stable val body: String?
  stable val hasLeadingContent: Boolean
  stable val hasTrailingContent: Boolean
  stable val titleLine: String
  <runtime stability> = Stable
}
unstable class com.gasstation.core.designsystem.string.StringResource.FromId {
  stable val id: Int
  unstable val args: List<Any>
  <runtime stability> = Unstable
}
stable class com.gasstation.core.designsystem.string.StringResource.Raw {
  stable val value: String
  <runtime stability> = Stable
}
```

## Composables

```
readonly fun com.gasstation.core.designsystem.GasStationTheme.<get-typography>(
  unused stable <this>: GasStationTheme
): GasStationTypography
readonly fun com.gasstation.core.designsystem.GasStationTheme.<get-spacing>(
  unused stable <this>: GasStationTheme
): GasStationSpacing
readonly fun com.gasstation.core.designsystem.GasStationTheme.<get-corner>(
  unused stable <this>: GasStationTheme
): GasStationCorner
readonly fun com.gasstation.core.designsystem.GasStationTheme.<get-stroke>(
  unused stable <this>: GasStationTheme
): GasStationStroke
readonly fun com.gasstation.core.designsystem.GasStationTheme.<get-iconSize>(
  unused stable <this>: GasStationTheme
): GasStationIconSize
restartable skippable scheme("[0, [0]]") fun com.gasstation.core.designsystem.GasStationTheme(
  stable darkTheme: Boolean = @dynamic <expression>
  stable dynamicColor: Boolean = @static <expression>
  stable content: Function2<Composer, Int, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.core.designsystem.component.GasStationBrandIcon(
  stable brand: Brand
  stable contentDescription: String?
  stable modifier: Modifier? = @static <expression>
  stable size: Dp = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.core.designsystem.component.GasStationBackground(
  stable modifier: Modifier? = @static <expression>
  stable content: @[ExtensionFunctionType] Function3<BoxScope, Composer, Int, Unit>
)
restartable skippable scheme("[0, [0], [0], [0]]") fun com.gasstation.core.designsystem.component.GasStationTopBar(
  stable title: Function2<Composer, Int, Unit>
  stable modifier: Modifier? = @static <expression>
  stable navigationIcon: Function2<Composer, Int, Unit>? = @static <expression>
  stable actions: @[ExtensionFunctionType] Function3<RowScope, Composer, Int, Unit>? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.core.designsystem.component.GasStationCard(
  stable modifier: Modifier? = @static <expression>
  stable contentPadding: PaddingValues? = @static <expression>
  stable content: @[ExtensionFunctionType] Function3<ColumnScope, Composer, Int, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.core.designsystem.component.GasStationSectionHeading(
  stable title: String
  stable modifier: Modifier? = @static <expression>
  stable subtitle: String? = @static null
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.core.designsystem.component.GasStationStatusBanner(
  stable text: String
  stable modifier: Modifier? = @static <expression>
  stable detail: String? = @static null
  stable tone: GasStationStatusTone? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.core.designsystem.component.StatusBannerSymbol(
  stable visual: StatusBannerToneVisual
  stable modifier: Modifier? = @static <expression>
)
fun com.gasstation.core.designsystem.component.style(
  stable <this>: ChromeTextRole
): TextStyle
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.core.designsystem.component.GasStationGuidanceCard(
  stable title: String
  stable body: String
  stable modifier: Modifier? = @static <expression>
  stable actionLabel: String? = @static null
  stable onAction: Function0<Unit>? = @static null
  stable leadingContent: @[ExtensionFunctionType] Function3<RowScope, Composer, Int, Unit>? = @static null
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.core.designsystem.component.GasStationMetricBlock(
  stable label: String
  stable number: String
  stable unit: String
  stable emphasis: GasStationMetricEmphasis
  stable modifier: Modifier? = @static <expression>
  stable labelColor: Color = @static <expression>
  stable numberColor: Color = @static <expression>
  stable unitColor: Color = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.core.designsystem.component.GasStationSupportingInfo(
  stable label: String
  stable value: String
  stable modifier: Modifier? = @static <expression>
  stable valueModifier: Modifier? = @static <expression>
  stable labelColor: Color = @static <expression>
  stable valueColor: Color = @static <expression>
  stable trailingContent: @[ExtensionFunctionType] Function3<RowScope, Composer, Int, Unit>? = @static null
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable], [androidx.compose.ui.UiComposable]]") fun com.gasstation.core.designsystem.component.GasStationRow(
  stable title: String
  stable modifier: Modifier? = @static <expression>
  stable value: String? = @static null
  stable body: String? = @static null
  stable titleColor: Color = @static <expression>
  stable bodyColor: Color = @static <expression>
  stable leadingContent: @[ExtensionFunctionType] Function3<RowScope, Composer, Int, Unit>? = @static null
  stable trailingContent: @[ExtensionFunctionType] Function3<RowScope, Composer, Int, Unit>? = @static null
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.core.designsystem.component.GasStationRowDivider(
  stable modifier: Modifier? = @static <expression>
  stable color: Color = @static <expression>
)
```

## Unstable 분류 대응

`StringResource.FromId`는 `args: List<Any>` 필드 때문에 unstable로 분류됩니다.
`List<Any>`는 타입 파라미터가 `Any`로 선언되어 Compose 컴파일러가 안정성을 추론할 수 없습니다.
이 타입은 현재 `@Stable`/`@Immutable` 없이 kept as-is로 유지합니다.
런타임에서 `StringResource`는 sealed interface이고 UI에서 소비되는 시점에 `Raw`(stable) 또는 `resolve(context)`를 통해 `String`으로 변환되므로 실질적 recomposition 비용은 없습니다.
