# GasStation Legacy UI Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리뉴얼 이후 generic해진 세 화면을 예전 GasStation의 `노랑 + 검정` 브랜드 톤, 촘촘한 정보 위계, 설정 2단계 탐색 구조로 복원한다.

**Architecture:** `core:designsystem`에서 브랜드 토큰과 공통 크롬을 먼저 고정한 뒤, `feature:station-list`, `feature:settings`, `feature:watchlist`를 순서대로 복원한다. 기능 계약과 상태 모델은 유지하고, UI 레이어와 내비게이션만 원형에 가깝게 재구성한다.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Hilt, JUnit4

---

### Task 1: Lock the legacy brand tokens and reusable chrome in `core:designsystem`

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/LegacyChrome.kt`
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt`

- [ ] **Step 1: Write the failing token/defaults test**

Create `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt`:

```kotlin
package com.gasstation.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GasStationThemeDefaultsTest {
    @Test
    fun `dynamic color is disabled by default for legacy brand restoration`() {
        assertFalse(GasStationThemeDefaults.useDynamicColor)
    }

    @Test
    fun `legacy palette keeps the restored yellow and black anchors`() {
        assertEquals(0xFFFFDC00, ColorPrimary.value.toLong())
        assertEquals(0xFF222222, ColorBlack.value.toLong())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*GasStationThemeDefaultsTest"
```

Expected: `BUILD FAILED` because `GasStationThemeDefaults` does not exist yet.

- [ ] **Step 3: Add explicit theme defaults and restore the extended palette**

Create `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`:

```kotlin
package com.gasstation.core.designsystem

object GasStationThemeDefaults {
    const val useDynamicColor: Boolean = false
}
```

Update `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt` to restore and standardize the old palette names:

```kotlin
package com.gasstation.core.designsystem

import androidx.compose.ui.graphics.Color

val ColorPrimary = Color(0xFFFFDC00)
val ColorPrimaryDark = Color(0xFF222222)
val ColorYellow = Color(0xFFFFDC00)
val ColorBlack = Color(0xFF222222)
val ColorWhite = Color(0xFFFFFFFF)
val ColorGray = Color(0xFFCCCCCC)
val ColorGray2 = Color(0xFF888888)
val ColorGray3 = Color(0xFF9A9A9A)
val ColorGray4 = Color(0xFFF2F2F2)
val ColorDivider = Color(0xFFE6E6E6)
val ColorWarningSurface = Color(0xFFFFF6BF)
```

- [ ] **Step 4: Turn off dynamic color and define brand-aware color scheme**

Update `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`:

```kotlin
package com.gasstation.core.designsystem

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ColorYellow,
    onPrimary = ColorBlack,
    background = ColorBlack,
    onBackground = ColorWhite,
    surface = ColorBlack,
    onSurface = ColorWhite,
)

private val LightColorScheme = lightColorScheme(
    primary = ColorYellow,
    onPrimary = ColorBlack,
    background = ColorYellow,
    onBackground = ColorBlack,
    surface = ColorWhite,
    onSurface = ColorBlack,
)

@Composable
fun GasStationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = GasStationThemeDefaults.useDynamicColor,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ColorBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 5: Restore the typography hierarchy for price-heavy cards**

Update `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt` so the large numeric hierarchy matches the old app:

```kotlin
package com.gasstation.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
)
```

- [ ] **Step 6: Add reusable legacy chrome primitives for the three screens**

Create `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/LegacyChrome.kt`:

```kotlin
package com.gasstation.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorDivider
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow

@Composable
fun LegacyScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorYellow),
    ) {
        content()
    }
}

@Composable
fun LegacyTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = { actions() },
        colors = TopAppBarColors(
            containerColor = ColorBlack,
            titleContentColor = ColorYellow,
            actionIconContentColor = ColorYellow,
            navigationIconContentColor = ColorYellow,
            scrolledContainerColor = ColorBlack,
        ),
    )
}

@Composable
fun LegacyOuterCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = ColorBlack,
            contentColor = ColorWhite,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorWhite)
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
fun LegacySectionHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = ColorBlack,
    )
}

@Composable
fun LegacyStatusBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ColorGray4,
            contentColor = ColorBlack,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 7: Run focused verification**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*GasStationThemeDefaultsTest"
./gradlew :core:designsystem:testDebugUnitTest --tests "*GasStationThemeSurfaceTest"
```

Expected: both commands `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/LegacyChrome.kt \
  core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt
git commit -m "feat: restore legacy gasstation design tokens"
```

### Task 2: Rebuild `feature:station-list` with the legacy shell, card hierarchy, and state banners

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListBannerModelTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`

- [ ] **Step 1: Write a failing test for banner priority and message mapping**

Create `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListBannerModelTest.kt`:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.location.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Test

class StationListBannerModelTest {
    @Test
    fun `stale state is rendered as a branded banner message`() {
        val uiState = StationListUiState(
            permissionState = LocationPermissionState.PreciseGranted,
            isStale = true,
        )

        assertEquals(
            listOf("오래된 결과를 표시 중입니다."),
            StationListBannerModel.from(uiState).map(StationListBannerModel::message),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListBannerModelTest"
```

Expected: `BUILD FAILED` because `StationListBannerModel` does not exist yet.

- [ ] **Step 3: Implement the pure banner model used by the restored screen**

Create `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.location.LocationPermissionState

data class StationListBannerModel(
    val message: String,
) {
    companion object {
        fun from(uiState: StationListUiState): List<StationListBannerModel> = buildList {
            if (uiState.permissionState == LocationPermissionState.ApproximateGranted) {
                add(StationListBannerModel("대략적인 위치 기준으로 주변 주유소를 찾고 있습니다."))
            }
            if (uiState.isStale) {
                add(
                    StationListBannerModel(
                        buildString {
                            append("오래된 결과를 표시 중입니다.")
                            uiState.lastUpdatedAt?.let { append(" 마지막 갱신 ${it.toDisplayLabel()}") }
                        },
                    ),
                )
            }
            if (uiState.isRefreshing) {
                add(StationListBannerModel("새로고침 중입니다."))
            }
        }
    }
}
```

- [ ] **Step 4: Restore the top shell and legacy card layout in `StationListScreen`**

Replace the `Scaffold` chrome in `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt` with the legacy shell:

```kotlin
@Composable
fun StationListScreen(
    uiState: StationListUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (StationListAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: (() -> Unit)? = null,
) {
    LegacyScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LegacyTopBar(
                    title = {
                        Text(
                            text = uiState.selectedSortOrder.toTitleLabel(),
                            modifier = Modifier.clickable { onAction(StationListAction.SortToggleRequested) },
                        )
                    },
                    actions = {
                        if (onWatchlistClick != null) {
                            TextButton(onClick = onWatchlistClick) {
                                Text(text = "관심 비교")
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
            when {
                uiState.permissionState == LocationPermissionState.Denied -> PermissionRequired(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onRequestPermissions = onRequestPermissions,
                )
                !uiState.isGpsEnabled -> GpsRequired(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onAction = onAction,
                )
                uiState.isLoading -> LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                else -> StationListContent(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Replace generic cards with the restored black-shell/white-core item layout**

Update the list item block in `StationListScreen.kt`:

```kotlin
items(uiState.stations, key = StationListItemUiModel::id) { station ->
    LegacyOuterCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onAction(StationListAction.StationClicked(station)) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = uiState.selectedFuelType.toChipLabel()) },
                    )
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorGray2,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = station.priceNumberLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = ColorBlack,
                    )
                    Text(
                        text = "원",
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorBlack,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = station.distanceNumberLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = ColorBlack,
                    )
                    Text(
                        text = station.distanceUnitLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorBlack,
                    )
                }
                Text(
                    text = station.priceDeltaLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorGray2,
                )
            }
            IconButton(
                onClick = {
                    onAction(
                        StationListAction.WatchToggled(
                            stationId = station.id,
                            watched = !station.isWatched,
                        ),
                    )
                },
            ) {
                Icon(
                    imageVector = if (station.isWatched) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "관심 주유소 토글",
                    tint = ColorYellow,
                )
            }
        }
    }
}
```

- [ ] **Step 6: Add numeric split fields to the list item UI model**

Update `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt` so the restored card can render large numeric blocks without string parsing in the Composable:

```kotlin
data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceWon: Int,
    val priceLabel: String,
    val priceNumberLabel: String,
    val distanceMeters: Int,
    val distanceLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
)
```

Populate the new properties in the mapper constructor:

```kotlin
priceWon = entry.station.price.value,
priceNumberLabel = entry.station.price.value.toString(),
distanceMeters = entry.station.distance.value,
distanceNumberLabel = entry.station.distance.value.toLegacyDistanceNumber(),
distanceUnitLabel = entry.station.distance.value.toLegacyDistanceUnit(),
```

Add the display helpers in the same file:

```kotlin
private fun Int.toLegacyDistanceNumber(): String = when {
    this >= 1000 -> String.format("%.1f", this / 1000.0)
    else -> toString()
}

private fun Int.toLegacyDistanceUnit(): String = if (this >= 1000) "km" else "m"
```

- [ ] **Step 7: Render banners and filter summary in the restored visual grammar**

Replace the top content portion of `StationListContent`:

```kotlin
item { LegacySectionHeading(title = "현재 조건") }
item {
    LegacyOuterCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = uiState.selectedRadius.toLabel(), style = MaterialTheme.typography.labelMedium)
            Text(text = uiState.selectedFuelType.toLabel(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

items(StationListBannerModel.from(uiState), key = StationListBannerModel::message) { banner ->
    LegacyStatusBanner(
        text = banner.message,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
```

Add a local chip label helper in `StationListScreen.kt`:

```kotlin
private fun FuelType.toChipLabel(): String = when (this) {
    FuelType.GASOLINE -> "휘발유"
    FuelType.DIESEL -> "경유"
    FuelType.PREMIUM_GASOLINE -> "고급휘발유"
    FuelType.KEROSENE -> "등유"
    FuelType.LPG -> "LPG"
}
```

- [ ] **Step 8: Update empty / permission / GPS states to branded cards instead of generic full-screen text**

Use `LegacyOuterCard` in `PermissionRequired`, `GpsRequired`, and `EmptyState` so they match the restored screen language:

```kotlin
LegacyOuterCard(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "주변 주유소 검색을 위해 위치 권한이 필요합니다.")
        Button(onClick = onRequestPermissions) {
            Text(text = "권한 요청")
        }
    }
}
```

- [ ] **Step 9: Run focused verification**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListBannerModelTest"
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected:

- first command: `BUILD SUCCESSFUL`
- second command: `BUILD SUCCESSFUL`
- third command: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListBannerModelTest.kt
git commit -m "feat: restore legacy station list chrome"
```

### Task 3: Restore `feature:settings` to the legacy two-step navigation flow

**Files:**
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsSection.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Create: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsSectionTest.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`

- [ ] **Step 1: Write the failing section mapping test**

Create `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsSectionTest.kt`:

```kotlin
package com.gasstation.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionTest {
    @Test
    fun `route segment resolves to search radius section`() {
        assertEquals(
            SettingsSection.SearchRadius,
            SettingsSection.fromRouteSegment("search-radius"),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsSectionTest"
```

Expected: `BUILD FAILED` because `SettingsSection` does not exist yet.

- [ ] **Step 3: Introduce an explicit settings section model used by navigation**

Create `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsSection.kt`:

```kotlin
package com.gasstation.feature.settings

enum class SettingsSection(
    val routeSegment: String,
    val title: String,
) {
    SearchRadius("search-radius", "찾기 범위"),
    FuelType("fuel-type", "오일 타입"),
    BrandFilter("brand-filter", "주유소 브랜드"),
    SortOrder("sort-order", "정렬 기준"),
    MapProvider("map-provider", "연동지도 서비스");

    companion object {
        fun fromRouteSegment(routeSegment: String): SettingsSection =
            entries.first { it.routeSegment == routeSegment }
    }
}
```

- [ ] **Step 4: Add a dedicated settings detail destination to the app nav graph**

Update `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`:

```kotlin
data object SettingsDetail : GasStationDestination {
    override val route: String = "settings/detail/{section}"

    fun createRoute(section: String): String = "settings/detail/$section"
}
```

Update `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`:

```kotlin
composable(GasStationDestination.Settings.route) {
    SettingsRoute(
        onClose = { navController.popBackStack() },
        onSectionClick = { section ->
            navController.navigate(GasStationDestination.SettingsDetail.createRoute(section.routeSegment))
        },
    )
}
composable(GasStationDestination.SettingsDetail.route) { backStackEntry ->
    SettingsDetailRoute(
        sectionRouteSegment = checkNotNull(backStackEntry.arguments?.getString("section")),
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 5: Split the feature into legacy list and legacy detail screens**

Update `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`:

```kotlin
@Composable
fun SettingsRoute(
    onClose: () -> Unit,
    onSectionClick: (SettingsSection) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onClose = onClose,
        onSectionClick = onSectionClick,
    )
}
```

Create `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt`:

```kotlin
package com.gasstation.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsDetailRoute(
    sectionRouteSegment: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val section = SettingsSection.fromRouteSegment(sectionRouteSegment)

    SettingsDetailScreen(
        section = section,
        uiState = uiState,
        onBack = onBack,
        onAction = viewModel::onAction,
    )
}
```

- [ ] **Step 6: Add explicit option and summary helpers to support the two-step flow**

Create `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`:

```kotlin
package com.gasstation.feature.settings

data class SettingOptionUiModel(
    val id: String,
    val section: SettingsSection,
    val label: String,
    val action: SettingsAction,
)
```

Update `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`:

```kotlin
fun SettingsUiState.summaryFor(section: SettingsSection): String = when (section) {
    SettingsSection.SearchRadius -> searchRadius.toLabel()
    SettingsSection.FuelType -> fuelType.toLabel()
    SettingsSection.BrandFilter -> brandFilter.toLabel()
    SettingsSection.SortOrder -> sortOrder.toLabel()
    SettingsSection.MapProvider -> mapProvider.toLabel()
}

fun SettingsUiState.selectedLabelFor(section: SettingsSection): String = summaryFor(section)

fun SettingsUiState.optionsFor(section: SettingsSection): List<SettingOptionUiModel> = when (section) {
    SettingsSection.SearchRadius -> SearchRadius.entries.map {
        SettingOptionUiModel(it.name, section, it.toLabel(), SettingsAction.SearchRadiusSelected(it))
    }
    SettingsSection.FuelType -> FuelType.entries.map {
        SettingOptionUiModel(it.name, section, it.toLabel(), SettingsAction.FuelTypeSelected(it))
    }
    SettingsSection.BrandFilter -> BrandFilter.entries.map {
        SettingOptionUiModel(it.name, section, it.toLabel(), SettingsAction.BrandFilterSelected(it))
    }
    SettingsSection.SortOrder -> SortOrder.entries.map {
        SettingOptionUiModel(it.name, section, it.toLabel(), SettingsAction.SortOrderSelected(it))
    }
    SettingsSection.MapProvider -> MapProvider.entries.map {
        SettingOptionUiModel(it.name, section, it.toLabel(), SettingsAction.MapProviderSelected(it))
    }
}
```

Keep the existing label helpers in `SettingsScreen.kt`, or move them to a shared `SettingsLabels.kt` if duplication appears.

- [ ] **Step 7: Rebuild the settings list screen to match the old menu-style rows**

Replace `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt` with a legacy list layout:

```kotlin
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onClose: () -> Unit,
    onSectionClick: (SettingsSection) -> Unit,
) {
    LegacyScreenBackground {
        Column {
            LegacyTopBar(
                title = {},
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                },
            )
            LegacySectionHeading(title = "찾기 설정")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ColorGray4),
            ) {
                items(SettingsSection.entries, key = SettingsSection::routeSegment) { section ->
                    SettingsRow(
                        title = section.title,
                        summary = uiState.summaryFor(section),
                        onClick = { onSectionClick(section) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 8: Implement the detail screen with immediate-save behavior**

Create `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`:

```kotlin
package com.gasstation.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray4
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow
import com.gasstation.core.designsystem.component.LegacyScreenBackground
import com.gasstation.core.designsystem.component.LegacyTopBar

@Composable
fun SettingsDetailScreen(
    section: SettingsSection,
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val options = uiState.optionsFor(section)
    val selected = uiState.selectedLabelFor(section)

    LegacyScreenBackground {
        Column {
            LegacyTopBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ColorGray4),
            ) {
                items(options, key = SettingOptionUiModel::id) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(ColorWhite)
                            .clickable {
                                onAction(option.action)
                                onBack()
                            }
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorBlack,
                        )
                        if (option.label == selected) {
                            Icon(Icons.Default.Check, contentDescription = "선택됨", tint = ColorYellow)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 9: Keep `SettingsAction` unchanged and remove ad-hoc conversion logic**

Do not add a second action layer. Reuse the existing sealed actions from `SettingsAction.kt` through `SettingOptionUiModel.action` so `SettingsViewModel` remains unchanged.

```kotlin
sealed interface SettingsAction {
    data class SortOrderSelected(val sortOrder: SortOrder) : SettingsAction
    data class FuelTypeSelected(val fuelType: FuelType) : SettingsAction
    data class SearchRadiusSelected(val radius: SearchRadius) : SettingsAction
    data class BrandFilterSelected(val brandFilter: BrandFilter) : SettingsAction
    data class MapProviderSelected(val mapProvider: MapProvider) : SettingsAction
}
```

- [ ] **Step 10: Run focused verification**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsSectionTest"
./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsViewModelTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected: all commands `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add \
  app/src/main/java/com/gasstation/navigation/GasStationDestination.kt \
  app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsSection.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt \
  feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsSectionTest.kt
git commit -m "feat: restore legacy settings flow"
```

### Task 4: Restyle `feature:watchlist` as a legacy extension of the list UI

**Files:**
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`

- [ ] **Step 1: Write the watchlist card layout change as a pure model addition**

Update `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt` so the screen can render large number blocks without UI parsing:

```kotlin
data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceWon: Int,
    val priceLabel: String,
    val distanceMeters: Int,
    val distanceLabel: String,
    val priceDeltaLabel: String,
    val lastSeenLabel: String,
)
```

Populate the new fields in the constructor:

```kotlin
priceWon = summary.station.price.value,
distanceMeters = summary.station.distance.value,
```

- [ ] **Step 2: Rebuild the watchlist screen using the same legacy shell**

Replace `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`:

```kotlin
@Composable
fun WatchlistScreen(
    uiState: WatchlistUiState,
) {
    LegacyScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LegacyTopBar(
                    title = { Text(text = "관심 비교") },
                )
            },
        ) { innerPadding ->
            if (uiState.stations.isEmpty()) {
                EmptyWatchlist(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.stations, key = WatchlistItemUiModel::id) { station ->
                        LegacyOuterCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(text = station.name, style = MaterialTheme.typography.labelSmall, color = ColorGray2)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = station.priceWon.toString(), style = MaterialTheme.typography.labelLarge, color = ColorBlack)
                                    Text(text = "원", style = MaterialTheme.typography.labelMedium, color = ColorBlack)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = station.distanceMeters.toLegacyDistanceNumber(), style = MaterialTheme.typography.labelLarge, color = ColorBlack)
                                    Text(text = station.distanceMeters.toLegacyDistanceUnit(), style = MaterialTheme.typography.labelMedium, color = ColorBlack)
                                }
                                Text(text = station.brandLabel, style = MaterialTheme.typography.bodyMedium, color = ColorGray2)
                                Text(text = station.priceDeltaLabel, style = MaterialTheme.typography.bodyMedium, color = ColorGray2)
                                Text(text = station.lastSeenLabel, style = MaterialTheme.typography.bodyMedium, color = ColorGray3)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Add the same helper pair in the watchlist file:

```kotlin
private fun Int.toLegacyDistanceNumber(): String = when {
    this >= 1000 -> String.format("%.1f", this / 1000.0)
    else -> toString()
}

private fun Int.toLegacyDistanceUnit(): String = if (this >= 1000) "km" else "m"
```

- [ ] **Step 3: Restyle the empty state to match the restored language**

Replace `EmptyWatchlist`:

```kotlin
@Composable
private fun EmptyWatchlist(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LegacyOuterCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "관심 주유소가 없습니다.",
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorBlack,
                )
                Text(
                    text = "주유소 목록에서 별표를 눌러 비교할 주유소를 추가하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorGray2,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests "*WatchlistViewModelTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected: both commands `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt
git commit -m "feat: restore legacy watchlist styling"
```

### Task 5: Final verification and demo-flow QA

**Files:**
- Modify: none expected

- [ ] **Step 1: Run the restored module-level test matrix**

Run:

```bash
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run compile and assemble verification for the reviewer path**

Run:

```bash
./gradlew :app:assembleDemoDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run connected UI verification if an emulator/device is available**

Run:

```bash
./gradlew :app:connectedDemoDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

If no emulator/device is available, note that this step was not run and do not claim it passed.

- [ ] **Step 4: Manual visual QA checklist in the demo build**

Check all items:

- [ ] 앱 첫 진입 시 검정 앱바와 노랑 배경이 즉시 보인다
- [ ] 목록 카드가 검정 외곽/흰 내부 구조로 보인다
- [ ] 가격/거리 숫자가 리스트에서 가장 먼저 읽힌다
- [ ] 관심 비교 버튼이 앱바에 자연스럽게 배치된다
- [ ] stale / refreshing / approximate 위치 상태가 배너로 읽힌다
- [ ] 설정 화면이 행 목록으로 보인다
- [ ] 설정 상세 화면에서 선택 즉시 저장 후 복귀한다
- [ ] 관심 비교 화면이 목록 스타일의 확장처럼 보인다

- [ ] **Step 5: Final commit if verification required follow-up polish**

If verification causes no extra changes, skip this commit. If you make polish edits during QA, then run:

```bash
git add core/designsystem feature/station-list feature/settings feature/watchlist app/src/main/java/com/gasstation/navigation
git commit -m "fix: polish restored legacy ui flow"
```
