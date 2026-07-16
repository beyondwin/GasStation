# Full-App Urban Signal UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy card-heavy GasStation UI with the approved Urban Signal system across nearby stations, watchlist, settings, settings detail, navigation, and shared states while preserving price-first decisions, real brand assets, demo/prod behavior, and accessibility contracts.

**Architecture:** `core:designsystem` owns only shared visual tokens and slot-based primitives; each `feature:*` module owns its projection models, copy, actions, and screen state; `app` owns the root scaffold, top-level navigation, coordinate payload, and external handoff. Typed price summaries are calculated in feature-local pure models from `MoneyWon.value`, while settings and watch mutations continue through existing domain use cases.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Hilt, Coroutines/Flow, JUnit4, Robolectric Compose UI tests, Roborazzi, Android connected tests, Macrobenchmark.

## Global Constraints

- Light canvas is `ColorSurface` `#FFFCF2`; `ColorYellow` `#FFDC00` is reserved for selected/decision/progress signals; `ColorBlack` is `#222222`.
- Do not add a font dependency, in-app map, backend/API/cache schema, station domain model, watchlist refresh session, separate location lookup, or snackbar undo behavior.
- Main price is 32sp; watchlist price is 28sp; numeric roles keep `fontFeatureSettings = "tnum"`.
- Main logo tile/logo are 50dp/38dp; watchlist logo tile/logo are 44dp/34dp; every icon-only action is at least 48dp.
- Watchlist rows target 108–116dp at default font/display scale and show five complete rows on an 800dp-class viewport; font scale up to 200% may increase row height and scroll instead of clipping.
- Keep the exact brand mapping: RTO/RTX/NHO use `ic_rtx`, ETC uses `ic_etc`, and the other existing `Brand` values keep their current drawable.
- Keep `demo` and `prod` as production paths, preserve stale cached content, `hasCachedSnapshot` semantics, external map handoff, stable ASCII test tags, and Korean accessibility descriptions.
- No feature may call Room, Retrofit, DataStore, `core:location`, or a repository implementation directly.

## File Structure And Responsibilities

| Area | Files | Responsibility |
| --- | --- | --- |
| Shared visual system | `core/designsystem/.../Color.kt`, `Typo.kt`, `GasStationThemeDefaults.kt`, `component/Chrome.kt`, new `component/UrbanSignal.kt` | Canonical colors, compact price role, ivory background, summary/flat-row/logo/navigation slot primitives |
| Nearby projection | new `feature/station-list/.../StationListDecisionSummary.kt`, `StationListDecisionSummaryTest.kt`, existing `StationListItemUiModel.kt` | Typed count/minimum/average/savings calculation without parsing labels |
| Nearby filters and screen | new `StationListFilterRail.kt`, existing `StationListAction.kt`, `StationListViewModel.kt`, `StationListScreen.kt`, `StationListCards.kt`, strings/tests/snapshots | In-context settings menus, decision strip, borderless price-first rows, shared state visuals |
| Watchlist state | new `WatchlistSummaryUiModel.kt`, existing `WatchlistUiState.kt`, `WatchlistAction.kt`, `WatchlistViewModel.kt`, item model/tests | Typed average/latest-check summary and save removal via existing use case |
| Watchlist UI | `WatchlistScreen.kt`, `WatchlistRoute.kt`, resources, module build file, UI/Roborazzi tests | Five-row dense comparison layout, real logos, 48dp remove action, empty navigation action |
| Settings UI | `SettingsScreen.kt`, `SettingsDetailScreen.kt`, `SettingsRoute.kt`, resources, module build file, UI/Roborazzi tests | Flat grouped overview, flat radio detail, no top-level close button, real brand tiles |
| Root navigation | new `app/.../navigation/GasStationTopLevelNavigation.kt`, existing `GasStationNavHost.kt`, `GasStationDestination.kt`, app strings/tests | Bottom navigation, saved tab state, conditional detail bar, coordinate payload |
| Reproducible evidence | demo seed asset/generator tests, connected flow, benchmark helpers, screenshots, live docs | Brand coverage in demo, selectors after bottom-nav migration, screenshots, current documentation |

---

### Task 1: Establish Urban Signal design-system tokens and primitives

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/ValueFormats.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Chrome.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/UrbanSignal.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeTokensTest.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/ValueFormatsTest.kt`
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/UrbanSignalContractsTest.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/RoborazziDesignSystemTest.kt`
- Update generated baselines: `core/designsystem/src/test/snapshots/*.png`

**Interfaces:**
- Produces: `GasStationTypography.compactPriceHero: TextStyle`
- Produces: `UrbanSignalTokens` with `mainLogoTileSize`, `mainLogoSize`, `mainRowMinHeight`, `compactLogoTileSize`, `compactLogoSize`, `compactRowMinHeight`, and `minimumTouchTarget`
- Produces: `GasStationBrandLogoTile(brand, contentDescription, tileSize, logoSize, modifier)`
- Produces: `GasStationSummaryStrip(modifier, content)`
- Produces: `GasStationComparisonRow(modifier, contentPadding, leading, primary, trailing)`
- Produces: `GasStationNavigationBar(content)` and `RowScope.GasStationNavigationBarItem(...)`
- Produces: `Int.gasStationWonLabel(): String` for already-typed non-negative won values

- [ ] **Step 1: Write failing token and surface tests**

Add assertions that lock the approved values:

```kotlin
@Test
fun `light theme uses ivory canvas and keeps yellow as primary signal`() {
    assertEquals(ColorSurface, GasStationThemeDefaults.lightColorScheme.background)
    assertEquals(ColorSurface, GasStationThemeDefaults.lightColorScheme.surface)
    assertEquals(ColorYellow, GasStationThemeDefaults.lightColorScheme.primary)
}

@Test
fun `urban signal density tokens match approved contract`() {
    assertEquals(50.dp, UrbanSignalTokens.mainLogoTileSize)
    assertEquals(38.dp, UrbanSignalTokens.mainLogoSize)
    assertEquals(120.dp, UrbanSignalTokens.mainRowMinHeight)
    assertEquals(44.dp, UrbanSignalTokens.compactLogoTileSize)
    assertEquals(34.dp, UrbanSignalTokens.compactLogoSize)
    assertEquals(108.dp, UrbanSignalTokens.compactRowMinHeight)
    assertEquals(48.dp, UrbanSignalTokens.minimumTouchTarget)
    assertEquals(28.sp, GasStationThemeDefaults.typography.compactPriceHero.fontSize)
    assertEquals("tnum", GasStationThemeDefaults.typography.compactPriceHero.fontFeatureSettings)
}
```

- [ ] **Step 2: Run the tests and confirm RED**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests '*GasStationThemeDefaultsTest' --tests '*UrbanSignalContractsTest'
```

Expected: compilation fails because `UrbanSignalTokens` and `compactPriceHero` do not exist, and the old light background is yellow.

- [ ] **Step 3: Add the exact tokens and switch the light background**

Add `ColorLogoTile` and the compact price role, then update the scheme:

```kotlin
val ColorLogoTile = Color(0xFFFFFFFF)

@Immutable
data class GasStationTypography(
    val topBarTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val priceHero: TextStyle,
    val compactPriceHero: TextStyle,
    val metricValue: TextStyle,
    val body: TextStyle,
    val meta: TextStyle,
    val chip: TextStyle,
    val bannerTitle: TextStyle,
    val bannerBody: TextStyle,
)

compactPriceHero = TextStyle(
    fontFamily = DefaultFontFamily,
    fontWeight = FontWeight.Black,
    fontSize = 28.sp,
    lineHeight = 30.sp,
    fontFeatureSettings = "tnum",
),
```

Use these light scheme assignments:

```kotlin
background = ColorSurface,
onBackground = ColorBlack,
surface = ColorSurface,
onSurface = ColorBlack,
surfaceVariant = ColorSurfaceRaised,
```

Change `GasStationBackground` to use the active scheme instead of a hard-coded yellow:

```kotlin
@Composable
fun GasStationBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        content = content,
    )
}
```

In `ValueFormats.kt` add the typed formatter, and lock it in `ValueFormatsTest`:

```kotlin
fun Int.gasStationWonLabel(): String = MoneyWon(this).gasStationPriceLabel()

@Test
fun `typed won integer uses canonical formatting`() {
    assertEquals("1,689원", 1689.gasStationWonLabel())
}
```

Do not accept a formatter that parses a previously formatted label.

- [ ] **Step 4: Create the slot-based Urban Signal primitives**

Create `UrbanSignal.kt` with stable sizes and no feature copy:

```kotlin
object UrbanSignalTokens {
    val mainLogoTileSize = 50.dp
    val mainLogoSize = 38.dp
    val mainRowMinHeight = 120.dp
    val compactLogoTileSize = 44.dp
    val compactLogoSize = 34.dp
    val compactRowMinHeight = 108.dp
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
fun GasStationSummaryStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
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
```

Implement the navigation wrappers in the same file:

```kotlin
@Composable
fun GasStationNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
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
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        alwaysShowLabel = true,
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
```

- [ ] **Step 5: Change shared cards from thick black outlines to raised guidance surfaces**

Replace the nested black/white `GasStationCard` surface with one raised surface while preserving its public signature:

```kotlin
Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    shape = RoundedCornerShape(GasStationTheme.corner.medium),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space12),
        content = content,
    )
}
```

- [ ] **Step 6: Add/update visual baselines and verify GREEN**

Add Roborazzi captures for `GasStationSummaryStrip`, the 50/38 brand tile, the 44/34 brand tile, a flat comparison row, and the navigation bar. Record baselines once, inspect them, then verify:

```bash
./gradlew :core:designsystem:recordRoborazziDebug
./gradlew :core:designsystem:verifyRoborazziDebug :core:designsystem:testDebugUnitTest
```

Expected: token/unit tests pass and every new/updated design-system screenshot has zero pixel diff after recording.

- [ ] **Step 7: Commit Task 1**

```bash
git add core/designsystem
git commit -m "feat: establish Urban Signal design system"
```

---

### Task 2: Add typed StationList decision summaries

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListDecisionSummary.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListDecisionSummaryTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListItemUiModelTest.kt`
- Modify fixtures: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`
- Modify fixtures: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

**Interfaces:**
- Produces: `StationListItemUiModel.priceWon: Int`
- Produces: `StationListDecisionSummary.from(items): StationListDecisionSummary?`
- Produces: `count`, `lowestPriceWon`, `averagePriceWon`, `savingsWon`, and `isLowestPriceTied`

- [ ] **Step 1: Write RED tests for empty, singleton, half-up average, and ties**

```kotlin
class StationListDecisionSummaryTest {
    @Test
    fun `empty list has no decision summary`() {
        assertNull(StationListDecisionSummary.from(emptyList()))
    }

    @Test
    fun `single item omits average comparison`() {
        val summary = requireNotNull(StationListDecisionSummary.from(listOf(item(1_712))))
        assertEquals(1, summary.count)
        assertEquals(1_712, summary.lowestPriceWon)
        assertNull(summary.averagePriceWon)
        assertNull(summary.savingsWon)
        assertFalse(summary.isLowestPriceTied)
    }

    @Test
    fun `positive half won average rounds upward`() {
        val summary = requireNotNull(StationListDecisionSummary.from(listOf(item(1_600), item(1_601))))
        assertEquals(1_601, summary.averagePriceWon)
        assertEquals(1, summary.savingsWon)
    }

    @Test
    fun `equal minima are reported as a tie`() {
        val summary = requireNotNull(StationListDecisionSummary.from(listOf(item(1_600), item(1_600), item(1_700))))
        assertTrue(summary.isLowestPriceTied)
        assertEquals(1_633, summary.averagePriceWon)
        assertEquals(33, summary.savingsWon)
    }
}

private fun item(priceWon: Int) = StationListItemUiModel(
    id = "station-$priceWon",
    name = "테스트 주유소",
    brand = Brand.GSC,
    brandLabel = "GS칼텍스",
    priceWon = priceWon,
    priceLabel = "${priceWon}원",
    distanceLabel = "0.3km",
    priceNumberLabel = priceWon.toString(),
    priceUnitLabel = "원",
    distanceNumberLabel = "0.3",
    distanceUnitLabel = "km",
    priceDeltaLabel = "-",
    isWatched = false,
    latitude = 37.49,
    longitude = 127.02,
)
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests '*StationListDecisionSummaryTest'
```

Expected: compilation fails because the summary type and `priceWon` property do not exist.

- [ ] **Step 3: Preserve typed price in the UI model**

Add the property and map it from the domain value:

```kotlin
data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
    val priceWon: Int,
    val priceLabel: String,
    val distanceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: PriceDeltaTone = PriceDeltaTone.Neutral,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
)
```

The domain constructor assigns `priceWon = entry.station.price.value`. Update direct test fixtures with integer values; do not derive the integer from `priceNumberLabel`.

- [ ] **Step 4: Implement the pure decision summary**

```kotlin
data class StationListDecisionSummary(
    val count: Int,
    val lowestPriceWon: Int,
    val averagePriceWon: Int?,
    val savingsWon: Int?,
    val isLowestPriceTied: Boolean,
) {
    companion object {
        fun from(items: List<StationListItemUiModel>): StationListDecisionSummary? {
            if (items.isEmpty()) return null
            val minimum = items.minOf(StationListItemUiModel::priceWon)
            if (items.size == 1) {
                return StationListDecisionSummary(
                    count = 1,
                    lowestPriceWon = minimum,
                    averagePriceWon = null,
                    savingsWon = null,
                    isLowestPriceTied = false,
                )
            }
            val count = items.size
            val sum = items.sumOf { it.priceWon.toLong() }
            val average = ((sum + count / 2L) / count).toInt()
            return StationListDecisionSummary(
                count = count,
                lowestPriceWon = minimum,
                averagePriceWon = average,
                savingsWon = average - minimum,
                isLowestPriceTied = items.count { it.priceWon == minimum } > 1,
            )
        }
    }
}
```

- [ ] **Step 5: Run model and feature tests**

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected: all station-list tests pass after every direct constructor fixture supplies `priceWon`.

- [ ] **Step 6: Commit Task 2**

```bash
git add feature/station-list
git commit -m "feat: derive typed station price summaries"
```

---

### Task 3: Build the StationList filter rail, decision strip, and flat rows

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStates.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/SettingsUseCaseTestFixture.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`
- Update: `feature/station-list/src/test/snapshots/*.png`

**Interfaces:**
- Consumes: `StationListDecisionSummary.from(items)` and Task 1 Urban Signal primitives
- Produces actions: `SearchRadiusSelected`, `FuelTypeSelected`, `BrandFilterSelected`
- Produces: `StationListRoute(..., onCoordinatesAvailable: (Coordinates?) -> Unit, ...)` for Task 7

- [ ] **Step 1: Write failing ViewModel tests for in-context filter writes**

Add one test that dispatches all three actions and verifies the shared settings state:

```kotlin
@Test
fun `filter rail actions update preferences through domain use cases`() = runTest(dispatcher) {
    val repository = FakeStationRepository(emptySearchResult())
    val settings = SettingsUseCaseTestFixture()
    val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

    viewModel.onAction(StationListAction.SearchRadiusSelected(SearchRadius.KM_5))
    viewModel.onAction(StationListAction.FuelTypeSelected(FuelType.DIESEL))
    viewModel.onAction(StationListAction.BrandFilterSelected(BrandFilter.RTO))
    advanceUntilIdle()

    assertEquals(SearchRadius.KM_5, settings.currentPreferences.searchRadius)
    assertEquals(FuelType.DIESEL, settings.currentPreferences.fuelType)
    assertEquals(BrandFilter.RTO, settings.currentPreferences.brandFilter)
}
```

- [ ] **Step 2: Confirm RED**

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests '*StationListViewModelTest*filter rail*'
```

Expected: compilation fails because the actions and injected use cases are missing.

- [ ] **Step 3: Add actions and use-case wiring**

Add these action variants:

```kotlin
data class SearchRadiusSelected(val radius: SearchRadius) : StationListAction
data class FuelTypeSelected(val fuelType: FuelType) : StationListAction
data class BrandFilterSelected(val brandFilter: BrandFilter) : StationListAction
```

Inject `UpdateSearchRadiusUseCase`, `UpdateFuelTypeUseCase`, and `UpdateBrandFilterUseCase` into `StationListViewModel`, then dispatch each in `viewModelScope.launch`. Expand `SettingsUseCaseTestFixture` around a fake `SettingsRepository` so it exposes real instances of all four update use cases and one observable state. Update the two station-list test factories to pass them.

Add these constructor properties immediately after `updatePreferredSortOrder`:

```kotlin
private val updateSearchRadius: UpdateSearchRadiusUseCase,
private val updateFuelType: UpdateFuelTypeUseCase,
private val updateBrandFilter: UpdateBrandFilterUseCase,
```

Insert these exact branches into the existing exhaustive `when (action)`; leave every pre-existing branch body unchanged:

```kotlin
is StationListAction.SearchRadiusSelected -> viewModelScope.launch {
    updateSearchRadius(action.radius)
}
is StationListAction.FuelTypeSelected -> viewModelScope.launch {
    updateFuelType(action.fuelType)
}
is StationListAction.BrandFilterSelected -> viewModelScope.launch {
    updateBrandFilter(action.brandFilter)
}
```

Replace `SettingsUseCaseTestFixture` with one shared repository-backed state so every use case observes the same write:

```kotlin
internal class SettingsUseCaseTestFixture(
    initialPreferences: UserPreferences = UserPreferences.default(),
) {
    private val state = MutableStateFlow(initialPreferences)
    private val repository = object : SettingsRepository {
        override fun observeUserPreferences(): Flow<UserPreferences> = state

        override suspend fun updateUserPreferences(
            transform: (UserPreferences) -> UserPreferences,
        ) {
            state.value = transform(state.value)
        }
    }

    val observeUserPreferences = ObserveUserPreferencesUseCase(repository)
    val updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository)
    val updateSearchRadius = UpdateSearchRadiusUseCase(repository)
    val updateFuelType = UpdateFuelTypeUseCase(repository)
    val updateBrandFilter = UpdateBrandFilterUseCase(repository)

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }

    val currentPreferences: UserPreferences get() = state.value
}
```

- [ ] **Step 4: Create a reusable anchored filter-menu chip**

Create `StationListFilterRail.kt` with this public feature-local API:

```kotlin
internal const val STATION_LIST_FILTER_RAIL_TAG = "station-list-filter-rail"

@Composable
internal fun StationListFilterRail(
    uiState: StationListUiState,
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
)
```

Implement a horizontally scrollable `Row` of four chips. The sort chip dispatches `SortToggleRequested`. Radius, fuel, and brand use a generic `FilterMenuChip<T>` with an internal `expanded` boolean, a black/yellow selected surface, `DropdownMenu`, `DropdownMenuItem`, and full option labels. Use `SearchRadius.entries`, `FuelType.entries`, and `BrandFilter.entries`; selected callbacks dispatch the new typed actions and close the menu.

```kotlin
@Composable
internal fun StationListFilterRail(
    uiState: StationListUiState,
    onAction: (StationListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(STATION_LIST_FILTER_RAIL_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterActionChip(
            label = if (uiState.selectedSortOrder == SortOrder.DISTANCE) {
                stringResource(R.string.station_list_sort_distance)
            } else {
                stringResource(R.string.station_list_sort_price)
            },
            onClick = { onAction(StationListAction.SortToggleRequested) },
        )
        FilterMenuChip(
            selected = uiState.selectedRadius,
            options = SearchRadius.entries.map { it to it.toLabel() },
            onSelected = { onAction(StationListAction.SearchRadiusSelected(it)) },
        )
        FilterMenuChip(
            selected = uiState.selectedFuelType,
            options = FuelType.entries.map { it to it.toLabel() },
            onSelected = { onAction(StationListAction.FuelTypeSelected(it)) },
        )
        FilterMenuChip(
            selected = uiState.selectedBrandFilter,
            options = BrandFilter.entries.map { it to it.gasStationBrandFilterLabel() },
            onSelected = { onAction(StationListAction.BrandFilterSelected(it)) },
        )
    }
}

@Composable
private fun <T> FilterMenuChip(
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterActionChip(
            label = options.first { it.first == selected }.second,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterActionChip(label: String, onClick: () -> Unit) {
    Surface(
        color = ColorBlack,
        contentColor = ColorYellow,
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = GasStationTheme.typography.chip,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
```

Add `station_list_sort_distance=거리순/Distance` and `station_list_sort_price=가격순/Price` to the Korean/English resources. Define `internal const val STATION_LIST_DECISION_SUMMARY_TAG = "station-list-decision-summary"` in `StationListScreen.kt` and `internal const val STATION_LIST_ROW_TAG = "station-list-row"` in `StationListCards.kt` before using them in tests or UI.

- [ ] **Step 5: Write RED Compose tests for chrome, summary, rows, and state preservation**

Add assertions for:

```kotlin
composeRule.onNodeWithText("주변 주유소").assertExists()
composeRule.onNodeWithContentDescription("새로고침").assertExists()
composeRule.onAllNodesWithContentDescription("북마크").assertCountEquals(0)
composeRule.onAllNodesWithContentDescription("설정").assertCountEquals(0)
composeRule.onNodeWithTag(STATION_LIST_FILTER_RAIL_TAG).assertExists()
composeRule.onNodeWithTag(STATION_LIST_DECISION_SUMMARY_TAG).assertExists()
composeRule.onAllNodesWithTag(STATION_LIST_ROW_TAG, useUnmergedTree = true).assertCountEquals(3)
composeRule.onNodeWithContentDescription("자영알뜰 브랜드").assertExists()
composeRule.onAllNodesWithTag(STATION_LIST_SKELETON_ROW_TAG, useUnmergedTree = true).assertCountEquals(3)
```

Keep existing permission, GPS, cached-loading, stale, empty, and blocking-failure tests; change only selectors that described the old card shape.

- [ ] **Step 6: Replace the top-bar title and results layout**

Use a fixed title and refresh action:

```kotlin
GasStationTopBar(
    title = { Text(stringResource(R.string.station_list_title)) },
    actions = {
        IconButton(
            modifier = Modifier.semantics { contentDescription = refreshLabel },
            onClick = { onAction(StationListAction.RefreshRequested) },
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
        }
    },
)
```

In `StationListContent`, render this order: status banners, address/query context, filter rail, optional decision summary, empty guidance or flat rows. Remove `Arrangement.spacedBy(12.dp)` between station items and insert `GasStationRowDivider` between rows.

Replace the initial spinner card in `StationListStates.kt` with three price-shaped flat skeleton rows; keep the current 150–180ms refresh-rail transition for cached refreshes:

```kotlin
internal const val STATION_LIST_SKELETON_ROW_TAG = "station-list-skeleton-row"

@Composable
internal fun LoadingState(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.station_list_loading_title)
    LazyColumn(
        modifier = modifier.semantics { contentDescription = loadingLabel },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items(3) { index ->
            GasStationComparisonRow(
                modifier = Modifier.testTag(STATION_LIST_SKELETON_ROW_TAG),
                leading = { LoadingPlaceholder(50.dp, 50.dp) },
                primary = {
                    LoadingPlaceholder(112.dp, 30.dp)
                    LoadingPlaceholder(176.dp, 20.dp)
                    LoadingPlaceholder(132.dp, 16.dp)
                },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        LoadingPlaceholder(58.dp, 20.dp)
                        Spacer(Modifier.height(8.dp))
                        LoadingPlaceholder(48.dp, 48.dp)
                    }
                },
            )
            if (index < 2) GasStationRowDivider()
        }
    }
}

@Composable
private fun LoadingPlaceholder(width: Dp, height: Dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(ColorNeutralLine.copy(alpha = 0.72f)),
    )
}
```

- [ ] **Step 7: Implement the decision summary strip**

Add `StationListDecisionSummaryStrip(summary)` that formats integers with the existing won formatter and renders:

```kotlin
GasStationSummaryStrip(
    modifier = Modifier
        .fillMaxWidth()
        .testTag(STATION_LIST_DECISION_SUMMARY_TAG),
) {
    Text(stringResource(R.string.station_list_decision_count, summary.count), color = ColorSurface)
    Text(
        text = stringResource(
            if (summary.isLowestPriceTied) {
                R.string.station_list_decision_tied_lowest
            } else {
                R.string.station_list_decision_lowest
            },
        ),
        color = ColorYellow,
    )
    Text(summary.lowestPriceWon.gasStationWonLabel(), color = ColorYellow)
    summary.savingsWon?.let { savings ->
        Text(
            stringResource(R.string.station_list_decision_savings, savings.gasStationWonLabel()),
            color = ColorSurface,
        )
    }
}
```

Define exact resources: `station_list_decision_count=%1$d곳/%1$d stations`, `station_list_decision_lowest=최저가/Lowest`, `station_list_decision_tied_lowest=공동 최저가/Tied lowest`, and `station_list_decision_savings=평균보다 %1$s 저렴/%1$s below average`.

- [ ] **Step 8: Replace `StationCard` with the price-first comparison row**

Keep the existing clickable station and bookmark semantics but use Task 1 primitives:

```kotlin
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
        )
        Text(
            text = station.name,
            style = GasStationTheme.typography.cardTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        StationRowMetadata(station = station, fuelTypeLabel = fuelTypeLabel)
    },
    trailing = {
        Column(horizontalAlignment = Alignment.End) {
            Text(station.distanceLabel, style = GasStationTheme.typography.metricValue)
            WatchToggleButton(station.isWatched, onWatchToggle)
        }
    },
)

@Composable
private fun StationRowMetadata(
    station: StationListItemUiModel,
    fuelTypeLabel: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelChip(text = fuelTypeLabel)
        PriceDeltaIndicator(
            label = station.priceDeltaLabel,
            tone = station.priceDeltaTone,
        )
    }
}
```

Define `station_list_brand_description` as `%1$s 브랜드` / `%1$s brand`. Do not render a duplicate visible brand label.

- [ ] **Step 9: Record and inspect StationList state screenshots**

Add a populated capture containing at least SKE, RTX, and ETC. Re-record populated/stale/empty/permission/GPS/failure baselines at `ko-rKR-w360dp-h800dp-xhdpi`, then run:

```bash
./gradlew :feature:station-list:recordRoborazziDebug
./gradlew :feature:station-list:verifyRoborazziDebug :feature:station-list:testDebugUnitTest
```

Expected: all state tests pass; cached content remains visible during refresh/stale states; the screenshot has ivory canvas, black summary, and flat rows.

- [ ] **Step 10: Commit Task 3**

```bash
git add feature/station-list
git commit -m "feat: redesign nearby station comparison"
```

---

### Task 4: Add Watchlist summary state and remove actions

**Files:**
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSummaryUiModel.kt`
- Create: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistSummaryUiModelTest.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistUiState.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistAction.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModelTest.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt`

**Interfaces:**
- Produces: `WatchlistItemUiModel.priceWon: Int` and `lastSeenAt: Instant?`
- Produces: `WatchlistSummaryUiModel.from(items)` with count, rounded average, and latest timestamp
- Produces: `Instant?.toWatchlistLastSeenLabel(zoneId: ZoneId): String`
- Produces: `WatchlistAction.RemoveClicked(stationId: String)`
- Produces: `WatchlistScreen(uiState, onAction, onNavigateNearby)` for Task 5/7

- [ ] **Step 1: Write failing summary and remove-action tests**

```kotlin
@Test
fun `watchlist summary rounds average and keeps latest check`() {
    val first = item(priceWon = 1_600, lastSeenAt = Instant.parse("2026-07-17T01:00:00Z"))
    val second = item(priceWon = 1_601, lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"))

    val summary = WatchlistSummaryUiModel.from(listOf(first, second))

    assertEquals(2, summary.count)
    assertEquals(1_601, summary.averagePriceWon)
    assertEquals(Instant.parse("2026-07-17T02:00:00Z"), summary.latestSeenAt)
}

private fun item(priceWon: Int, lastSeenAt: Instant?) = WatchlistItemUiModel(
    id = "station-$priceWon",
    name = "테스트 주유소",
    brand = Brand.GSC,
    brandLabel = "GS칼텍스",
    priceWon = priceWon,
    priceLabel = "${priceWon}원",
    priceNumberLabel = priceWon.toString(),
    priceUnitLabel = "원",
    distanceLabel = "0.3km",
    distanceNumberLabel = "0.3",
    distanceUnitLabel = "km",
    priceDeltaLabel = "-",
    lastSeenAt = lastSeenAt,
    lastSeenLabel = lastSeenAt.toWatchlistLastSeenLabel(ZoneId.of("Asia/Seoul")),
    latitude = 37.49,
    longitude = 127.02,
)

@Test
fun `remove action writes false through update watch state use case`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    val station = Station(
        id = "station-1",
        name = "Gangnam First",
        brand = Brand.GSC,
        price = MoneyWon(1_680),
        distance = DistanceMeters(300),
        coordinates = Coordinates(37.498095, 127.027610),
    )
    val repository = MutableWatchlistRepository(
        listOf(WatchedStationSummary(station, StationPriceDelta.Unchanged, null)),
    )
    val viewModel = WatchlistViewModel(
        observeWatchlist = ObserveWatchlistUseCase(repository),
        updateWatchState = UpdateWatchStateUseCase(repository),
        savedStateHandle = SavedStateHandle(
            mapOf("latitude" to "37.498095", "longitude" to "127.027610"),
        ),
        stationEventLogger = RecordingStationEventLogger(),
    )
    val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collectLatest { }
    }
    advanceUntilIdle()

    viewModel.onAction(WatchlistAction.RemoveClicked("station-1"))
    advanceUntilIdle()

    assertEquals(listOf("station-1" to false), repository.watchUpdates)
    assertEquals(0, viewModel.uiState.value.summary.count)
    collectionJob.cancel()
}
```

- [ ] **Step 2: Confirm RED**

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests '*WatchlistSummaryUiModelTest' --tests '*WatchlistViewModelTest*remove action*'
```

Expected: compilation fails because raw fields, summary type, action, and ViewModel handler are absent.

- [ ] **Step 3: Add typed fields and summary projection**

Map `priceWon = summary.station.price.value` and `lastSeenAt = summary.lastSeenAt` in `WatchlistItemUiModel`. Add:

```kotlin
data class WatchlistSummaryUiModel(
    val count: Int = 0,
    val averagePriceWon: Int? = null,
    val latestSeenAt: Instant? = null,
) {
    companion object {
        fun from(items: List<WatchlistItemUiModel>): WatchlistSummaryUiModel {
            if (items.isEmpty()) return WatchlistSummaryUiModel()
            val count = items.size
            val sum = items.sumOf { it.priceWon.toLong() }
            return WatchlistSummaryUiModel(
                count = count,
                averagePriceWon = ((sum + count / 2L) / count).toInt(),
                latestSeenAt = items.mapNotNull(WatchlistItemUiModel::lastSeenAt).maxOrNull(),
            )
        }
    }
}

data class WatchlistUiState(
    val stations: List<WatchlistItemUiModel> = emptyList(),
    val summary: WatchlistSummaryUiModel = WatchlistSummaryUiModel.from(stations),
)

internal fun Instant?.toWatchlistLastSeenLabel(
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (this == null) return "-"
    return DateTimeFormatter.ofPattern("M월 d일 HH:mm")
        .withZone(zoneId)
        .format(this)
}
```

Replace the old private `Instant?.toLabel()` call in `WatchlistItemUiModel` with `toWatchlistLastSeenLabel()`. Keep the `Instant?` field typed in both the row and summary models; labels are a final UI projection only.

- [ ] **Step 4: Wire removal through the existing use case**

Change `WatchlistAction` and ViewModel:

```kotlin
sealed interface WatchlistAction {
    data class RemoveClicked(val stationId: String) : WatchlistAction
}
```

Inject `UpdateWatchStateUseCase`. Keep a private `Map<String, Station>` refreshed from each observed summary list. On `RemoveClicked`, find the station, call `updateWatchState(station, false)`, and log `StationEvent.WatchToggled(stationId, watched = false)` with `logSafely`. If the ID is no longer present, return without a write.

```kotlin
class WatchlistViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    private val updateWatchState: UpdateWatchStateUseCase,
    savedStateHandle: SavedStateHandle,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private var stationsById: Map<String, Station> = emptyMap()

    val uiState = observeWatchlist(origin)
        .map { summaries ->
            stationsById = summaries.associate { it.station.id to it.station }
            if (!hasLoggedCompareViewed) {
                hasLoggedCompareViewed = true
                stationEventLogger.logSafely(StationEvent.CompareViewed(count = summaries.size))
            }
            val stations = summaries.map(::WatchlistItemUiModel)
            WatchlistUiState(
                stations = stations,
                summary = WatchlistSummaryUiModel.from(stations),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchlistUiState(),
        )

    fun onAction(action: WatchlistAction) {
        when (action) {
            is WatchlistAction.RemoveClicked -> {
                val station = stationsById[action.stationId] ?: return
                viewModelScope.launch {
                    updateWatchState(station, false)
                    stationEventLogger.logSafely(
                        StationEvent.WatchToggled(station.id, watched = false),
                    )
                }
            }
        }
    }
}
```

Retain the existing `origin`, `hasLoggedCompareViewed`, and `SavedStateHandle.requiredCoordinate()` members around this changed body exactly once.

- [ ] **Step 5: Make the repository fake reactive and verify GREEN**

Use this reactive fake; after a false update it removes the item from the observed flow:

```kotlin
private class MutableWatchlistRepository(
    initial: List<WatchedStationSummary>,
) : StationRepository {
    private val summaries = MutableStateFlow(initial)
    val watchUpdates = mutableListOf<Pair<String, Boolean>>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flowOf(
        StationSearchResult(
            stations = emptyList(),
            freshness = StationFreshness.Stale,
            fetchedAt = null,
            hasCachedSnapshot = false,
        ),
    )

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> = summaries

    override suspend fun refreshNearbyStations(query: StationQuery) {
        error("refreshNearbyStations is not used in watchlist tests")
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) {
        watchUpdates += station.id to watched
        if (!watched) summaries.value = summaries.value.filterNot { it.station.id == station.id }
    }
}
```

The test above asserts that `uiState.stations` and `uiState.summary.count` both become zero.

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest
```

Expected: mapping, summary, one-time compare event, analytics-failure isolation, and removal tests all pass.

- [ ] **Step 6: Commit Task 4**

```bash
git add feature/watchlist
git commit -m "feat: add watchlist summary and removal state"
```

---

### Task 5: Render the five-row dense Watchlist and its visual tests

**Files:**
- Modify: `feature/watchlist/build.gradle.kts`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt`
- Modify: `feature/watchlist/src/main/res/values/strings.xml`
- Modify: `feature/watchlist/src/main/res/values-en/strings.xml`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
- Create: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/RoborazziWatchlistScreenTest.kt`
- Create generated baselines: `feature/watchlist/src/test/snapshots/watchlist-five-rows.png`
- Create generated baselines: `feature/watchlist/src/test/snapshots/watchlist-empty.png`

**Interfaces:**
- Consumes: Task 1 compact logo/row/summary primitives and Task 4 state/action
- Produces: stable `WATCHLIST_ROW_TAG` value while retaining the existing `watchlist-card` resource-id string as a compatibility alias through this migration

- [ ] **Step 1: Enable Roborazzi and write RED density/accessibility tests**

Apply `id("gasstation.roborazzi")` to the module. In `WatchlistSemantics.kt`, replace the old card constant name while preserving its value, and add the remove prefix. Add tests that render five rows in a 360dp × 800dp root and assert:

```kotlin
const val WATCHLIST_ROW_TAG = "watchlist-card"
const val WATCHLIST_REMOVE_TAG_PREFIX = "watchlist-remove-"

composeRule.onAllNodesWithTag(WATCHLIST_ROW_TAG, useUnmergedTree = true).assertCountEquals(5)
composeRule.onNodeWithContentDescription("자영알뜰 브랜드").assertExists()
composeRule.onNodeWithContentDescription("자가상표 브랜드").assertExists()
composeRule.onAllNodesWithText("자가상표").assertCountEquals(0)
composeRule.onNodeWithTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-1").assertHeightIsAtLeast(48.dp)
composeRule.onNodeWithContentDescription("닫기").assertDoesNotExist()
```

Use bounds to assert every default-scale row is at least 108dp and no more than 116dp. Add a second `fontScale = 2f` test that asserts the price, station name, and remove action still exist without asserting the upper height bound.

```kotlin
val rowHeights = composeRule
    .onAllNodesWithTag(WATCHLIST_ROW_TAG, useUnmergedTree = true)
    .fetchSemanticsNodes()
    .map { node -> with(composeRule.density) { node.boundsInRoot.height.toDp() } }
assertTrue(rowHeights.all { it >= 108.dp && it <= 116.dp })

composeRule.setContent {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density,
            fontScale = 2f,
        ),
    ) {
        GasStationTheme { WatchlistScreen(fiveRowState(), onAction = {}, onNavigateNearby = {}) }
    }
}
composeRule.onNodeWithText("1,680").assertExists()
composeRule.onNodeWithText("강남 제일 주유소").assertExists()
composeRule.onNodeWithTag(WATCHLIST_REMOVE_TAG_PREFIX + "station-1").assertExists()

private fun fiveRowState(): WatchlistUiState {
    val items = listOf(Brand.SKE, Brand.GSC, Brand.SOL, Brand.RTO, Brand.ETC)
        .mapIndexed { index, brand ->
            val price = 1_680 + index * 10
            WatchlistItemUiModel(
                id = "station-${index + 1}",
                name = if (index == 0) "강남 제일 주유소" else "비교 주유소 ${index + 1}",
                brand = brand,
                brandLabel = brand.gasStationBrandLabel(),
                priceWon = price,
                priceLabel = MoneyWon(price).gasStationPriceLabel(),
                priceNumberLabel = MoneyWon(price).gasStationPriceDigits(),
                priceUnitLabel = "원",
                distanceLabel = "${index + 1}.0km",
                distanceNumberLabel = "${index + 1}.0",
                distanceUnitLabel = "km",
                priceDeltaLabel = "-",
                lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"),
                lastSeenLabel = "7월 17일 11:00",
                latitude = 37.49 + index * 0.001,
                longitude = 127.02 + index * 0.001,
            )
        }
    return WatchlistUiState(
        stations = items,
        summary = WatchlistSummaryUiModel.from(items),
    )
}
```

- [ ] **Step 2: Confirm RED**

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests '*WatchlistScreenTest'
```

Expected: the old card layout, close callback, visible brand label, and missing remove action fail the new assertions.

- [ ] **Step 3: Replace the screen contract and header**

Use these signatures:

```kotlin
@Composable
fun WatchlistRoute(
    onNavigateNearby: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WatchlistScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateNearby = onNavigateNearby,
    )
}

@Composable
fun WatchlistScreen(
    uiState: WatchlistUiState,
    onAction: (WatchlistAction) -> Unit,
    onNavigateNearby: () -> Unit,
)
```

The top bar contains only this title; set the Korean title to `관심 주유소` and English to `Saved stations`. Remove the close canvas and callback.

```kotlin
GasStationTopBar(
    title = { Text(stringResource(R.string.watchlist_title)) },
)
```

- [ ] **Step 4: Add the compact summary and dense rows**

Render summary and items without inter-card spacing:

```kotlin
GasStationSummaryStrip(Modifier.fillMaxWidth()) {
    Text(
        stringResource(R.string.watchlist_summary_count, uiState.summary.count),
        color = ColorSurface,
    )
    uiState.summary.averagePriceWon?.let { average ->
        Text(
            stringResource(R.string.watchlist_summary_average, average.gasStationWonLabel()),
            color = ColorYellow,
        )
    }
    Text(
        text = uiState.summary.latestSeenAt?.let { latest ->
            stringResource(
                R.string.watchlist_summary_latest_seen,
                latest.toWatchlistLastSeenLabel(),
            )
        } ?: stringResource(R.string.watchlist_summary_no_seen),
        color = ColorSurface,
    )
}
```

Define `watchlist_summary_count` as `저장한 %1$d곳` / `%1$d saved`, `watchlist_summary_average` as `평균 %1$s` / `Average %1$s`, `watchlist_summary_latest_seen` as `최근 확인 %1$s` / `Last checked %1$s`, and `watchlist_summary_no_seen` as `최근 확인 기록 없음` / `No check history`.

Each item uses `GasStationComparisonRow` with `Modifier.heightIn(min = 108.dp)`, compact 44/34 logo tile, `compactPriceHero`, a single-line name, distance, delta, last-seen label, and a 48dp bookmark icon button dispatching `RemoveClicked(id)`. Insert `GasStationRowDivider` between items. Do not render a decorative chevron or visible brand label.

```kotlin
@Composable
private fun WatchlistRow(
    station: WatchlistItemUiModel,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GasStationComparisonRow(
        modifier = modifier
            .heightIn(min = UrbanSignalTokens.compactRowMinHeight)
            .testTag(WATCHLIST_ROW_TAG),
        leading = {
            GasStationBrandLogoTile(
                brand = station.brand,
                contentDescription = stringResource(
                    R.string.watchlist_brand_description,
                    station.brandLabel,
                ),
                tileSize = UrbanSignalTokens.compactLogoTileSize,
                logoSize = UrbanSignalTokens.compactLogoSize,
            )
        },
        primary = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(station.priceNumberLabel, style = GasStationTheme.typography.compactPriceHero)
                Text(station.priceUnitLabel, style = GasStationTheme.typography.meta)
            }
            Text(
                text = station.name,
                style = GasStationTheme.typography.cardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.watchlist_row_meta,
                    station.priceDeltaLabel,
                    station.lastSeenLabel,
                ),
                style = GasStationTheme.typography.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(station.distanceLabel, style = GasStationTheme.typography.metricValue)
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(UrbanSignalTokens.minimumTouchTarget)
                        .testTag(WATCHLIST_REMOVE_TAG_PREFIX + station.id),
                ) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = stringResource(R.string.watchlist_remove_description),
                    )
                }
            }
        },
    )
}
```

Define `watchlist_brand_description` as `%1$s 브랜드` / `%1$s brand`, `watchlist_row_meta` as `%1$s · %2$s`, and `watchlist_remove_description` as `관심 주유소에서 제거` / `Remove from saved stations`.

Render the rows with stable keys and a 180ms removal/replacement transition so adjacent rows move without introducing undo policy:

```kotlin
itemsIndexed(
    items = uiState.stations,
    key = { _, station -> station.id },
) { index, station ->
    WatchlistRow(
        station = station,
        onRemove = { onAction(WatchlistAction.RemoveClicked(station.id)) },
        modifier = Modifier.animateItem(
            fadeInSpec = tween(180),
            placementSpec = tween(180),
            fadeOutSpec = tween(180),
        ),
    )
    if (index < uiState.stations.lastIndex) GasStationRowDivider()
}
```

- [ ] **Step 5: Replace the empty card with a compact guidance action**

Use `GasStationGuidanceCard` with the existing explanation and an action label `주변 주유소 보기`; call `onNavigateNearby`. Keep the bottom navigation outside this feature in Task 7.

```kotlin
GasStationGuidanceCard(
    title = stringResource(R.string.watchlist_empty_title),
    body = stringResource(R.string.watchlist_empty_body),
    actionLabel = stringResource(R.string.watchlist_empty_action),
    onAction = onNavigateNearby,
)
```

Set `watchlist_empty_action` to `주변 주유소 보기` / `View nearby stations`; keep the existing empty title and body translations unchanged.

- [ ] **Step 6: Record and inspect five-row and empty screenshots**

The five-row fixture must use SKE, GSC, SOL, RTO, and ETC in that order. Run:

```bash
./gradlew :feature:watchlist:recordRoborazziDebug
./gradlew :feature:watchlist:verifyRoborazziDebug :feature:watchlist:testDebugUnitTest
```

Expected: five complete rows fit at default scale; alttul and ETC assets are visibly correct; 200% font scale tests scroll rather than clip.

- [ ] **Step 7: Commit Task 5**

```bash
git add feature/watchlist
git commit -m "feat: render dense watchlist comparison rows"
```

---

### Task 6: Flatten Settings overview and detail screens

**Files:**
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`
- Modify: `feature/settings/src/main/res/values-en/strings.xml`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- Create: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/RoborazziSettingsScreenTest.kt`
- Create generated baselines: `feature/settings/src/test/snapshots/settings-overview.png`
- Create generated baselines: `feature/settings/src/test/snapshots/settings-brand-detail.png`

**Interfaces:**
- Produces: `SettingsRoute(onSectionClick, viewModel)` with no close callback
- Retains: `SettingsDetailRoute(section, onBackClick, viewModelStoreOwner, viewModel)` and existing domain actions

- [ ] **Step 1: Enable Roborazzi and write RED hierarchy tests**

Apply `id("gasstation.roborazzi")`. Update tests to assert:

```kotlin
composeRule.onNodeWithText("설정").assertExists()
composeRule.onNodeWithContentDescription("닫기").assertDoesNotExist()
composeRule.onNodeWithTag("settings-group-Explore").assertExists()
composeRule.onNodeWithText("찾기 범위").assertExists()
composeRule.onNodeWithText("3km").assertExists()
composeRule.onAllNodesWithText("찾기 범위 : 3km").assertCountEquals(0)
```

For BrandFilter detail, assert `전체` has no brand image; RTO, RTX, and NHO show the alttul asset with visible labels; ETC shows the ETC asset; every option remains `Role.RadioButton` and selected state is exposed.

- [ ] **Step 2: Confirm RED**

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsScreenTest'
```

Expected: the legacy close action, title/value concatenation, group cards, and old detail card fail the new assertions.

- [ ] **Step 3: Remove the top-level close contract**

Use:

```kotlin
@Composable
fun SettingsRoute(
    onSectionClick: (SettingsSection) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(uiState = uiState, onSectionClick = onSectionClick)
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSectionClick: (SettingsSection) -> Unit,
)
```

Set `settings_title` to `설정`; the top bar has no action slot.

- [ ] **Step 4: Render overview groups as headings plus flat rows**

Remove `GasStationCard` from `SettingsSectionGroupBlock`. Keep the group test tag on the outer `Column`; render `SettingsGroupHeader`, then rows separated by `GasStationRowDivider`. Change each row from `title : value` to separate title/current value/body slots so large font scale can stack them.

The row content order is:

```kotlin
Column(Modifier.weight(1f)) {
    Text(stringResource(section.titleResId), style = GasStationTheme.typography.cardTitle)
    Text(selectedLabel.resolve(context), style = GasStationTheme.typography.body, color = ColorBlack)
    Text(stringResource(section.subtitleResId), style = GasStationTheme.typography.meta, color = ColorGray3)
}
LegacyChevronIcon()
```

- [ ] **Step 5: Render detail description and options without a group card**

Keep the black back top bar. Render `Text(stringResource(section.subtitleResId))` once above a single option list. Each option row uses Task 1 comparison/flat-row primitives, 48dp minimum height, `Role.RadioButton`, `selected`, and trailing check. For concrete brand options use `GasStationBrandLogoTile` with 44/34 sizes and `contentDescription = null` because the visible label is adjacent; `BrandFilter.ALL` has no tile.

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize().padding(innerPadding),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
) {
    item {
        Text(
            text = stringResource(section.subtitleResId),
            style = GasStationTheme.typography.body,
            color = ColorGray2,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    itemsIndexed(options, key = { _, option -> option.action.toString() }) { index, option ->
        SettingsDetailOptionRow(
            section = section,
            option = option,
            onClick = { onOptionClick(option) },
        )
        if (index < options.lastIndex) GasStationRowDivider()
    }
}

@Composable
private fun SettingsDetailOptionRow(
    section: SettingsSection,
    option: SettingOptionUiModel,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val leadingContent: (@Composable RowScope.() -> Unit)? =
        if (section == SettingsSection.BrandFilter) {
            option.brandIconBrand?.let { brand ->
                {
                    GasStationBrandLogoTile(
                        brand = brand,
                        contentDescription = null,
                        tileSize = UrbanSignalTokens.compactLogoTileSize,
                        logoSize = UrbanSignalTokens.compactLogoSize,
                    )
                }
            }
        } else {
            null
        }
    GasStationRow(
        title = option.label.resolve(context),
        body = option.subtitle?.resolve(context),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = option.isSelected },
        bodyColor = ColorGray2,
        leadingContent = leadingContent,
        trailingContent = if (option.isSelected) {
            { SelectedCheckIcon() }
        } else {
            null
        },
    )
}
```

Use `option.action.toString()` only as the existing model-stable key if `SettingOptionUiModel` has no dedicated id; do not derive keys from localized labels.

- [ ] **Step 6: Record settings screenshots and verify GREEN**

Capture overview and BrandFilter detail at 360dp × 800dp, including RTO/RTX/NHO/ETC rows through scrolling captures or a tall viewport. Run:

```bash
./gradlew :feature:settings:recordRoborazziDebug
./gradlew :feature:settings:verifyRoborazziDebug :feature:settings:testDebugUnitTest
```

Expected: hierarchy tests, selected semantics, long-value tests, and both baselines pass.

- [ ] **Step 7: Commit Task 6**

```bash
git add feature/settings
git commit -m "feat: flatten settings hierarchy"
```

---

### Task 7: Add root bottom navigation and preserve tab state

**Files:**
- Create: `app/src/main/java/com/gasstation/navigation/GasStationTopLevelNavigation.kt`
- Create: `app/src/test/java/com/gasstation/navigation/GasStationTopLevelNavigationTest.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

**Interfaces:**
- Consumes: Task 1 navigation primitive, Task 5 `WatchlistRoute(onNavigateNearby)`, Task 6 `SettingsRoute(onSectionClick)`
- Produces stable tags: `bottom-nav-nearby`, `bottom-nav-watchlist`, `bottom-nav-settings`
- Produces: `TopLevelDestination` and pure bottom-bar visibility/selection policy

- [ ] **Step 1: Write RED navigation policy tests**

```kotlin
@Test
fun `bottom bar is visible only on top level routes`() {
    assertTrue(shouldShowBottomBar(GasStationDestination.StationList.route))
    assertTrue(shouldShowBottomBar(GasStationDestination.Settings.route))
    assertTrue(shouldShowBottomBar(GasStationDestination.Watchlist.route))
    assertFalse(shouldShowBottomBar(GasStationDestination.SettingsDetail.route))
}

@Test
fun `watchlist destination requires coordinates`() {
    assertFalse(TopLevelNavigationState(origin = null).watchlistEnabled)
    assertTrue(TopLevelNavigationState(Coordinates(37.49, 127.02)).watchlistEnabled)
}

@Test
fun `settings detail selects no top level item`() {
    assertNull(selectedTopLevelDestination(GasStationDestination.SettingsDetail.route))
}
```

- [ ] **Step 2: Confirm RED**

```bash
./gradlew :app:testDemoDebugUnitTest --tests '*GasStationTopLevelNavigationTest'
```

Expected: compilation fails because the policy types/functions do not exist.

- [ ] **Step 3: Implement pure navigation state**

Create:

```kotlin
internal enum class TopLevelDestination {
    Nearby,
    Watchlist,
    Settings,
}

internal data class TopLevelNavigationState(val origin: Coordinates?) {
    val watchlistEnabled: Boolean get() = origin != null
}

internal fun shouldShowBottomBar(route: String?): Boolean = route in setOf(
    GasStationDestination.StationList.route,
    GasStationDestination.Watchlist.route,
    GasStationDestination.Settings.route,
)

internal fun selectedTopLevelDestination(route: String?): TopLevelDestination? = when (route) {
    GasStationDestination.StationList.route -> TopLevelDestination.Nearby
    GasStationDestination.Watchlist.route -> TopLevelDestination.Watchlist
    GasStationDestination.Settings.route -> TopLevelDestination.Settings
    else -> null
}
```

The `Watchlist.route` comparison uses the declared route pattern, which is what `NavDestination.route` returns.

- [ ] **Step 4: Expose coordinate availability from StationListRoute**

Replace `onSettingsClick` and `onWatchlistClick` with:

```kotlin
@Composable
fun StationListRoute(
    onCoordinatesAvailable: (Coordinates?) -> Unit,
    onOpenExternalMap: (StationListEffect.OpenExternalMap) -> Unit,
    onFirstContentDrawn: () -> Unit = {},
    viewModel: StationListViewModel = hiltViewModel(),
)
```

Add:

```kotlin
LaunchedEffect(uiState.currentCoordinates, uiState.permissionState, uiState.isGpsEnabled) {
    onCoordinatesAvailable(uiState.watchlistCoordinatesOrNull())
}
```

Remove top-bar navigation callbacks from `StationListScreen`. Keep `watchlistCoordinatesOrNull()` and its tests as the single policy for enabling the Watchlist tab.

- [ ] **Step 5: Wrap NavHost in the root scaffold**

In `GasStationNavHost`, remember latitude/longitude with `rememberSaveable`, derive `Coordinates?`, observe `currentBackStackEntryAsState`, and render:

```kotlin
var originLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
var originLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
val origin = originLatitude?.let { latitude ->
    originLongitude?.let { longitude -> Coordinates(latitude, longitude) }
}
val navigationState = TopLevelNavigationState(origin)
val currentBackStackEntry by navController.currentBackStackEntryAsState()
val currentRoute = currentBackStackEntry?.destination?.route

Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
        if (shouldShowBottomBar(currentRoute)) {
            GasStationBottomNavigation(
                selected = selectedTopLevelDestination(currentRoute),
                watchlistEnabled = navigationState.watchlistEnabled,
                onNearby = { navController.navigateTopLevel(GasStationDestination.StationList.route) },
                onWatchlist = {
                    navigationState.origin?.let { origin ->
                        navController.navigateTopLevel(GasStationDestination.Watchlist.createRoute(origin))
                    }
                },
                onSettings = { navController.navigateTopLevel(GasStationDestination.Settings.route) },
            )
        }
    },
) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = GasStationDestination.StationList.route,
        modifier = Modifier.padding(innerPadding),
    ) {
        gasStationDestinations(
            navController = navController,
            externalMapLauncher = externalMapLauncher,
            onCoordinatesAvailable = { coordinates ->
                originLatitude = coordinates?.latitude
                originLongitude = coordinates?.longitude
            },
            onStationListFirstContentDrawn = onStationListFirstContentDrawn,
        )
    }
}
```

Define the referenced graph builder and top-level navigation helper in the same file; keep the existing transition functions on every destination:

```kotlin
private fun NavGraphBuilder.gasStationDestinations(
    navController: NavHostController,
    externalMapLauncher: ExternalMapLauncher,
    onCoordinatesAvailable: (Coordinates?) -> Unit,
    onStationListFirstContentDrawn: () -> Unit,
) {
    composable(
        route = GasStationDestination.StationList.route,
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) {
        StationListRoute(
            onCoordinatesAvailable = onCoordinatesAvailable,
            onOpenExternalMap = { effect ->
                externalMapLauncher.open(
                    provider = effect.provider,
                    stationName = effect.stationName,
                    originLatitude = effect.originLatitude,
                    originLongitude = effect.originLongitude,
                    latitude = effect.latitude,
                    longitude = effect.longitude,
                )
            },
            onFirstContentDrawn = onStationListFirstContentDrawn,
        )
    }
    composable(
        route = GasStationDestination.Settings.route,
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) {
        SettingsRoute(onSectionClick = { section ->
            navController.navigate(GasStationDestination.SettingsDetail.createRoute(section))
        })
    }
    composable(
        route = GasStationDestination.SettingsDetail.route,
        arguments = listOf(navArgument(GasStationDestination.SettingsDetail.SECTION_ARG) {
            type = NavType.StringType
        }),
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) { backStackEntry ->
        val routeSegment = requireNotNull(
            backStackEntry.arguments?.getString(GasStationDestination.SettingsDetail.SECTION_ARG),
        )
        val settingsBackStackEntry = remember(backStackEntry) {
            navController.getBackStackEntry(GasStationDestination.Settings.route)
        }
        SettingsDetailRoute(
            section = SettingsSection.requireFromRouteSegment(routeSegment),
            onBackClick = navController::popBackStack,
            viewModelStoreOwner = settingsBackStackEntry,
        )
    }
    composable(
        route = GasStationDestination.Watchlist.route,
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) {
        WatchlistRoute(
            onNavigateNearby = {
                navController.navigateTopLevel(GasStationDestination.StationList.route)
            },
        )
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
```

- [ ] **Step 6: Implement bottom items and detail visibility**

Add the exact labels `nav_nearby=주변/Nearby`, `nav_watchlist=관심/Saved`, `nav_settings=설정/Settings`, and `nav_watchlist_disabled=현재 위치 확인 후 사용 가능/Available after locating you`. Implement:

```kotlin
@Composable
private fun GasStationBottomNavigation(
    selected: TopLevelDestination?,
    watchlistEnabled: Boolean,
    onNearby: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit,
) {
    GasStationNavigationBar {
        GasStationNavigationBarItem(
            selected = selected == TopLevelDestination.Nearby,
            onClick = onNearby,
            icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_nearby)) },
            modifier = Modifier.testTag("bottom-nav-nearby"),
        )
        GasStationNavigationBarItem(
            selected = selected == TopLevelDestination.Watchlist,
            onClick = onWatchlist,
            icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_watchlist)) },
            enabled = watchlistEnabled,
            modifier = Modifier
                .testTag("bottom-nav-watchlist")
                .semantics {
                    if (!watchlistEnabled) {
                        stateDescription = watchlistDisabledDescription
                    }
                },
        )
        GasStationNavigationBarItem(
            selected = selected == TopLevelDestination.Settings,
            onClick = onSettings,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
            modifier = Modifier.testTag("bottom-nav-settings"),
        )
    }
}
```

Resolve `watchlistDisabledDescription = stringResource(R.string.nav_watchlist_disabled)` before the semantics lambda. `shouldShowBottomBar()` from Step 3 is the only visibility policy, so SettingsDetail keeps its existing back action and receives no bottom bar.

- [ ] **Step 7: Run navigation and feature regression tests**

```bash
./gradlew \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyModuleBoundaries
```

Expected: pure navigation policy passes; no feature screen expects its removed close/top-bar navigation callbacks; module boundaries remain valid.

- [ ] **Step 8: Commit Task 7**

```bash
git add app feature/station-list feature/watchlist feature/settings
git commit -m "feat: add persistent bottom navigation"
```

---

### Task 8: Update demo brand coverage, device/benchmark selectors, screenshots, and live docs

**Files:**
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoPortfolioStations.kt`
- Modify: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`
- Modify: `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt`
- Modify: `app/src/demo/assets/demo-station-seed.json`
- Modify: `app/src/testDemo/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt`
- Modify: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `benchmark/src/main/kotlin/com/gasstation/benchmark/GasStationBenchmarkActions.kt`
- Modify: `benchmark/src/main/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- Verify unchanged: `benchmark/src/main/kotlin/com/gasstation/benchmark/StationListBenchmark.kt` (it calls the migrated `openWatchlistWithSavedStation()` helper and contains no direct old selector)
- Replace screenshots: `docs/readme-assets/playstore_11.png`, `playstore_22.png`, `playstore_33.png`
- Modify: `README.md`
- Modify: `.impeccable.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/agent-workflow.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`
- Modify: `docs/performance.md`

**Interfaces:**
- Consumes: stable bottom-nav and watchlist-row tags from Tasks 5 and 7
- Produces: deterministic demo RTO and ETC stations without changing prod/network behavior
- Produces: current README screenshots and live contract descriptions

- [ ] **Step 1: Write RED demo coverage tests**

Add assertions that the default 3km gasoline snapshot contains both missing visual brands:

```kotlin
val snapshot = document.queries.single {
    it.radiusMeters == SearchRadius.KM_3.meters && it.fuelType == FuelType.GASOLINE.name
}
assertTrue(snapshot.stations.any { it.brandCode == Brand.RTO.name })
assertTrue(snapshot.stations.any { it.brandCode == Brand.ETC.name })
```

Add the same expectation to `DemoSeedGeneratorTest` so future regeneration cannot drop them.

- [ ] **Step 2: Confirm RED**

```bash
./gradlew :tools:demo-seed:test :app:testDemoDebugUnitTest --tests '*DemoSeedAssetLoaderTest*'
```

Expected: RTO and ETC assertions fail against the current seed.

- [ ] **Step 3: Add deterministic portfolio stations to seed generation**

Create exact demo-only rows:

```kotlin
internal object DemoPortfolioStations {
    fun forQuery(radius: SearchRadius, fuelType: FuelType): List<DemoSeedRemoteStation> {
        if (radius != SearchRadius.KM_3 || fuelType != FuelType.GASOLINE) return emptyList()
        return listOf(
            DemoSeedRemoteStation(
                stationId = "DEMO-RTO-001",
                name = "행복드림 알뜰주유소",
                brandCode = "RTO",
                priceWon = 1_968,
                coordinates = Coordinates(37.4935, 127.0258),
            ),
            DemoSeedRemoteStation(
                stationId = "DEMO-ETC-001",
                name = "우리동네 주유소",
                brandCode = "ETC",
                priceWon = 1_987,
                coordinates = Coordinates(37.5004, 127.0321),
            ),
        )
    }
}
```

In `DemoSeedGenerator.createDocument`, replace the direct fetch mapping with:

```kotlin
val fetchedStations = fetcher.fetchStations(
    origin = origin,
    radius = query.radius,
    fuelType = query.fuelType,
)
val stations = (fetchedStations + DemoPortfolioStations.forQuery(query.radius, query.fuelType))
    .distinctBy(DemoSeedRemoteStation::stationId)

DemoSeedSnapshot(
    radiusMeters = query.radius.meters,
    fuelType = query.fuelType.name,
    stations = stations.map { station ->
        DemoSeedStation(
            stationId = station.stationId,
            brandCode = station.brandCode,
            name = station.name,
            priceWon = station.priceWon,
            latitude = station.coordinates.latitude,
            longitude = station.coordinates.longitude,
        )
    },
)
```

Regenerate the committed seed through the existing `:tools:demo-seed:run` workflow so the 3km/GASOLINE snapshot and generated history entries match. This code is used only by deterministic demo generation; prod data remains untouched.

- [ ] **Step 4: Migrate connected and benchmark selectors to bottom navigation**

In `StationPortfolioFlowTest`, replace the top-bar `북마크` click with:

```kotlin
rule.onNodeWithTag("bottom-nav-watchlist", useUnmergedTree = true).performClick()
rule.waitUntil(timeoutMillis = 10_000) {
    rule.onAllNodesWithTag("watchlist-card", useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
}
```

In `GasStationBenchmarkActions`, replace `station-list-watchlist-action` with `bottom-nav-watchlist`; keep the saved-station action and watchlist-row compatibility tag. Update comments in baseline profile and performance docs to describe bottom navigation.

- [ ] **Step 5: Run demo, connected, and benchmark compile checks**

```bash
./gradlew \
  :tools:demo-seed:test \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
./gradlew :app:connectedDemoDebugAndroidTest
```

Expected: unit/assemble tasks pass; the connected flow saves a station, opens the Watchlist bottom tab, and finds a dense row. If no device is attached, record that as an environment limitation and run the connected command when a device becomes available before merge.

- [ ] **Step 6: Capture current README screenshots from the deterministic demo**

With a 360dp-class emulator on default font scale, capture Nearby populated, Watchlist with five saved items, and Settings/BrandFilter detail. Crop only system/device framing consistently; do not alter app pixels. Replace the three existing `docs/readme-assets/playstore_*.png` files and verify the README renders them at equal width.

- [ ] **Step 7: Synchronize live docs**

Update every listed document with these exact facts:

- ivory canvas, black chrome, yellow decision signals, borderless station rows
- bottom navigation labels and SettingsDetail exception
- Watchlist 108–116dp default density and accessibility expansion rule
- real brand icon mapping including RTO/RTX/NHO/ETC
- Watchlist no longer repeats a visible brand label
- coordinate payload remains navigation state; Watchlist still owns no location/refresh session
- connected and benchmark selectors now use `bottom-nav-watchlist`
- Roborazzi coverage now includes Watchlist and Settings

Add a CHANGELOG entry under the current unreleased/version section; do not invent a version or release date.

- [ ] **Step 8: Run the complete acceptance gate**

```bash
git diff --check
./gradlew \
  spotlessCheck \
  lint \
  :tools:demo-seed:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyModuleBoundaries \
  verifyNoDeprecatedComposeTestApis \
  verifyRoborazziDebug \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :app:assembleProdRelease \
  :benchmark:assemble
```

Expected: all tasks pass. Inspect Roborazzi output rather than updating baselines merely to silence a diff. Also verify `git status --short` contains only intended implementation, snapshot, asset, and documentation files.

- [ ] **Step 9: Commit Task 8**

```bash
git add \
  tools/demo-seed \
  app/src/demo/assets/demo-station-seed.json \
  app/src/testDemo \
  app/src/androidTest \
  benchmark \
  docs/readme-assets \
  README.md \
  .impeccable.md \
  CHANGELOG.md \
  docs/agent-workflow.md \
  docs/architecture.md \
  docs/state-model.md \
  docs/test-strategy.md \
  docs/verification-matrix.md \
  docs/performance.md
git commit -m "docs: publish Urban Signal UI evidence"
```

---

## Final Review Checklist

- [ ] `git status --short` is clean after the final commit.
- [ ] `git log --oneline` shows one focused commit per task and no unrelated user changes.
- [ ] Nearby, Watchlist, and Settings preserve scroll/tab state when switching through bottom navigation.
- [ ] Watchlist is disabled with an explanation before coordinates exist and enabled after StationList supplies valid coordinates.
- [ ] SettingsDetail hides bottom navigation and returns to Settings.
- [ ] Nearby summary handles zero, singleton, half-up average, and tied minima without parsing formatted text.
- [ ] Watchlist summary and remove action use typed data and the existing `UpdateWatchStateUseCase`.
- [ ] Default 360dp × 800dp Watchlist shows five complete rows; 200% font scale scrolls without clipping.
- [ ] SKE/GSC/HDO/SOL/RTO/RTX/NHO/ETC/E1G/SKG mapping remains exhaustive; RTO/RTX/NHO use alttul and ETC uses the ETC asset.
- [ ] Permission, GPS, cached loading, stale, empty, and blocking failure behaviors still match existing state/cache contracts.
- [ ] `demo`, `prod`, screenshots, connected flow, benchmark helpers, and live docs describe the same navigation and UI.
