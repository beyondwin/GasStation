# Brand Icon Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore station brand icons in station list cards, watchlist cards, and the settings brand filter screen.

**Architecture:** Restore the old brand PNGs into `core:designsystem`, add a shared `GasStationBrandIcon` component backed by `Brand`, and pass the domain `Brand` through feature UI models so screens can render icons without duplicating resource mappings. Settings keeps `BrandFilter.ALL` text-only and gives concrete brand rows an optional `Brand`.

**Tech Stack:** Kotlin, Android Compose, Material 3, Gradle Android unit tests, Robolectric Compose tests.

---

## File Structure

- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`
  Owns the shared brand icon resource mapping and compact Compose renderer.
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/BrandIconTest.kt`
  Verifies every `Brand` maps to the restored drawable resource.
- Restore resources:
  `core/designsystem/src/main/res/drawable/ic_ske.png`,
  `core/designsystem/src/main/res/drawable/ic_gsc.png`,
  `core/designsystem/src/main/res/drawable/ic_hdo.png`,
  `core/designsystem/src/main/res/drawable/ic_sol.png`,
  `core/designsystem/src/main/res/drawable/ic_rtx.png`,
  `core/designsystem/src/main/res/drawable/ic_etc.png`,
  `core/designsystem/src/main/res/drawable/ic_e1g.png`,
  `core/designsystem/src/main/res/drawable/ic_skg.png`
- Modify: `core/designsystem/build.gradle.kts`
  Adds `domain:station` dependency for the shared `Brand` enum.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
  Carries the station `Brand` into UI state.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  Renders the icon in station cards.
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
  Verifies list cards expose the brand icon.
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`
  Carries the station `Brand` into UI state.
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
  Renders the icon in watchlist cards.
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
  Verifies watchlist cards expose the brand icon.
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`
  Adds optional brand icon metadata for settings rows.
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
  Populates optional brand metadata for concrete `BrandFilter` options.
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
  Renders the optional brand icon only when present.
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
  Verifies concrete brand filter rows show icons and `전체` remains text-only.

---

### Task 1: Restore Design-System Brand Resources And Mapping

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/BrandIconTest.kt`
- Modify: `core/designsystem/build.gradle.kts`
- Restore: `core/designsystem/src/main/res/drawable/*.png` brand icons listed above

- [x] **Step 1: Restore old PNG resources from the pre-`6bbb36e` tree**

Run:

```bash
mkdir -p core/designsystem/src/main/res/drawable
for icon in ic_ske ic_gsc ic_hdo ic_sol ic_rtx ic_etc ic_e1g ic_skg; do
  git show 6bbb36e^:app/src/main/res/drawable/${icon}.png > core/designsystem/src/main/res/drawable/${icon}.png
done
ls core/designsystem/src/main/res/drawable/ic_*.png
```

Expected: the command lists the eight restored icon files under `core/designsystem/src/main/res/drawable`.

- [x] **Step 2: Write the failing mapping test**

Create `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/BrandIconTest.kt`:

```kotlin
package com.gasstation.core.designsystem.component

import com.gasstation.core.designsystem.R
import com.gasstation.domain.station.model.Brand
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandIconTest {
    @Test
    fun `brand icon resources match legacy gas station type mapping`() {
        assertEquals(R.drawable.ic_ske, Brand.SKE.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_gsc, Brand.GSC.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_hdo, Brand.HDO.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_sol, Brand.SOL.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_rtx, Brand.RTO.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_rtx, Brand.RTX.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_rtx, Brand.NHO.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_etc, Brand.ETC.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_e1g, Brand.E1G.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_skg, Brand.SKG.gasStationBrandIconResource())
    }
}
```

- [x] **Step 3: Run test to verify it fails on missing dependency or missing mapping**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests 'com.gasstation.core.designsystem.component.BrandIconTest'
```

Expected: FAIL because `com.gasstation.domain.station.model.Brand` is not available to `core:designsystem` or `gasStationBrandIconResource()` is not defined.

- [x] **Step 4: Add the station domain dependency**

Modify `core/designsystem/build.gradle.kts` dependencies block:

```kotlin
dependencies {
    implementation(project(":domain:station"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
```

- [x] **Step 5: Implement the shared brand icon mapping and renderer**

Create `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`:

```kotlin
package com.gasstation.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.R
import com.gasstation.domain.station.model.Brand

@DrawableRes
fun Brand.gasStationBrandIconResource(): Int = when (this) {
    Brand.SKE -> R.drawable.ic_ske
    Brand.GSC -> R.drawable.ic_gsc
    Brand.HDO -> R.drawable.ic_hdo
    Brand.SOL -> R.drawable.ic_sol
    Brand.RTO -> R.drawable.ic_rtx
    Brand.RTX -> R.drawable.ic_rtx
    Brand.NHO -> R.drawable.ic_rtx
    Brand.ETC -> R.drawable.ic_etc
    Brand.E1G -> R.drawable.ic_e1g
    Brand.SKG -> R.drawable.ic_skg
}

@Composable
fun GasStationBrandIcon(
    brand: Brand,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Image(
        painter = painterResource(id = brand.gasStationBrandIconResource()),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
```

- [x] **Step 6: Run design-system test to verify it passes**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests 'com.gasstation.core.designsystem.component.BrandIconTest'
```

Expected: PASS.

- [x] **Step 7: Commit design-system icon foundation**

Run:

```bash
git add core/designsystem/build.gradle.kts core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/BrandIconTest.kt core/designsystem/src/main/res/drawable/ic_ske.png core/designsystem/src/main/res/drawable/ic_gsc.png core/designsystem/src/main/res/drawable/ic_hdo.png core/designsystem/src/main/res/drawable/ic_sol.png core/designsystem/src/main/res/drawable/ic_rtx.png core/designsystem/src/main/res/drawable/ic_etc.png core/designsystem/src/main/res/drawable/ic_e1g.png core/designsystem/src/main/res/drawable/ic_skg.png
git commit -m "feat: restore station brand icons"
```

---

### Task 2: Add Brand Icons To Station List Cards

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [x] **Step 1: Write the failing station-list UI test**

In `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`, add `import com.gasstation.domain.station.model.Brand` if missing, then add this test:

```kotlin
@Test
fun `station card renders brand icon beside brand label`() {
    composeRule.setContent {
        StationListScreen(
            uiState = StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                stations = listOf(
                    StationListItemUiModel(
                        id = "station-1",
                        name = "테스트 주유소",
                        brand = Brand.GSC,
                        brandLabel = "GS칼텍스",
                        priceLabel = "1,689원",
                        distanceLabel = "0.3km",
                        priceNumberLabel = "1,689",
                        priceUnitLabel = "원",
                        distanceNumberLabel = "0.3",
                        distanceUnitLabel = "km",
                        priceDeltaLabel = "-",
                        isWatched = false,
                        latitude = 37.498095,
                        longitude = 127.02761,
                    ),
                ),
                selectedFuelType = FuelType.GASOLINE,
            ),
            snackbarHostState = androidx.compose.material3.SnackbarHostState(),
            onAction = {},
            onRequestPermissions = {},
            onOpenLocationSettings = {},
            onSettingsClick = {},
        )
    }

    composeRule.onNodeWithContentDescription("GS칼텍스 브랜드").assertExists()
    composeRule.onNodeWithText("GS칼텍스").assertExists()
}
```

- [x] **Step 2: Run station-list screen test to verify it fails**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests 'com.gasstation.feature.stationlist.StationListScreenTest.station card renders brand icon beside brand label'
```

Expected: FAIL because `StationListItemUiModel` does not yet expose `brand` or the icon is not rendered.

- [x] **Step 3: Carry `Brand` through station-list UI model**

Modify `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`:

```kotlin
data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
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
) {
    constructor(entry: StationListEntry) : this(
        id = entry.station.id,
        name = entry.station.name,
        brand = entry.station.brand,
        brandLabel = entry.station.brand.toLabel(),
        priceLabel = entry.station.price.value.toPriceLabel(),
        distanceLabel = entry.station.distance.toDistanceLabel(),
        priceNumberLabel = entry.station.price.value.toGroupedDigits(),
        priceUnitLabel = "원",
        distanceNumberLabel = entry.station.distance.toDistanceNumberLabel(),
        distanceUnitLabel = "km",
        priceDeltaLabel = entry.priceDelta.toLabel(),
        priceDeltaTone = entry.priceDelta.toTone(),
        isWatched = entry.isWatched,
        latitude = entry.station.coordinates.latitude,
        longitude = entry.station.coordinates.longitude,
    )
}
```

- [x] **Step 4: Render the brand icon in the station card metadata row**

In `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`, add:

```kotlin
import com.gasstation.core.designsystem.component.GasStationBrandIcon
```

Inside the metadata row in `StationCard`, replace the fuel chip and brand text block with:

```kotlin
FuelChip(text = fuelTypeLabel)
GasStationBrandIcon(
    brand = station.brand,
    contentDescription = "${station.brandLabel} 브랜드",
)
Text(
    text = station.brandLabel,
    style = typography.meta,
    color = ColorGray2,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```

- [x] **Step 5: Run station-list targeted tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests 'com.gasstation.feature.stationlist.StationListScreenTest'
```

Expected: PASS. If the existing row alignment test is affected by the icon width, keep the price delta on the right by preserving `Modifier.weight(1f)` on the left metadata row.

- [x] **Step 6: Commit station-list icon rendering**

Run:

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt
git commit -m "feat: show brand icons in station list"
```

---

### Task 3: Add Brand Icons To Watchlist Cards

**Files:**
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`

- [x] **Step 1: Write the failing watchlist UI test**

In `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`, add these imports:

```kotlin
import androidx.compose.ui.test.onNodeWithContentDescription
import com.gasstation.domain.station.model.Brand
```

Add this test:

```kotlin
@Test
fun `watchlist card renders brand icon beside brand label`() {
    composeRule.setContent {
        WatchlistScreen(
            uiState = WatchlistUiState(
                stations = listOf(
                    WatchlistItemUiModel(
                        id = "station-1",
                        name = "테스트 주유소",
                        brand = Brand.GSC,
                        brandLabel = "GS칼텍스",
                        priceLabel = "1,689원",
                        priceNumberLabel = "1,689",
                        priceUnitLabel = "원",
                        distanceLabel = "0.3km",
                        distanceNumberLabel = "0.3",
                        distanceUnitLabel = "km",
                        priceDeltaLabel = "-",
                        lastSeenLabel = "4월 18일 12:00",
                        latitude = 37.498095,
                        longitude = 127.02761,
                    ),
                ),
            ),
        )
    }

    composeRule.onNodeWithContentDescription("GS칼텍스 브랜드").assertExists()
    composeRule.onNodeWithText("GS칼텍스").assertExists()
}
```

- [x] **Step 2: Run watchlist screen test to verify it fails**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests 'com.gasstation.feature.watchlist.WatchlistScreenTest.watchlist card renders brand icon beside brand label'
```

Expected: FAIL because `WatchlistItemUiModel` does not yet expose `brand` or the icon is not rendered.

- [x] **Step 3: Carry `Brand` through watchlist UI model**

Modify `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`:

```kotlin
data class WatchlistItemUiModel(
    val id: String,
    val name: String,
    val brand: Brand = Brand.ETC,
    val brandLabel: String,
    val priceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: WatchlistPriceDeltaTone = WatchlistPriceDeltaTone.Neutral,
    val lastSeenLabel: String,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(priceNumberLabel.isNotBlank()) { "priceNumberLabel must not be blank" }
        require(priceUnitLabel.isNotBlank()) { "priceUnitLabel must not be blank" }
        require(distanceNumberLabel.isNotBlank()) { "distanceNumberLabel must not be blank" }
        require(distanceUnitLabel.isNotBlank()) { "distanceUnitLabel must not be blank" }
    }

    constructor(summary: WatchedStationSummary) : this(
        id = summary.station.id,
        name = summary.station.name,
        brand = summary.station.brand,
        brandLabel = summary.station.brand.toLabel(),
        priceLabel = summary.station.price.value.toPriceLabel(),
        priceNumberLabel = summary.station.price.value.toGroupedDigits(),
        priceUnitLabel = "원",
        distanceLabel = summary.station.distance.toDistanceLabel(),
        distanceNumberLabel = summary.station.distance.toDistanceNumberLabel(),
        distanceUnitLabel = "km",
        priceDeltaLabel = summary.priceDelta.toLabel(),
        priceDeltaTone = summary.priceDelta.toTone(),
        lastSeenLabel = summary.lastSeenAt.toLabel(),
        latitude = summary.station.coordinates.latitude,
        longitude = summary.station.coordinates.longitude,
    )
}
```

- [x] **Step 4: Render the brand icon in watchlist cards**

In `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`, add:

```kotlin
import com.gasstation.core.designsystem.component.GasStationBrandIcon
```

Replace the plain brand `Text` under the station name with:

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    verticalAlignment = Alignment.CenterVertically,
) {
    GasStationBrandIcon(
        brand = station.brand,
        contentDescription = "${station.brandLabel} 브랜드",
    )
    Text(
        text = station.brandLabel,
        style = GasStationTheme.typography.meta,
        color = ColorGray2,
        maxLines = 1,
    )
}
```

- [x] **Step 5: Run watchlist targeted tests**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests 'com.gasstation.feature.watchlist.WatchlistScreenTest'
```

Expected: PASS.

- [x] **Step 6: Commit watchlist icon rendering**

Run:

```bash
git add feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt
git commit -m "feat: show brand icons in watchlist"
```

---

### Task 4: Add Brand Icons To Settings Brand Filter

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`

- [x] **Step 1: Write the failing settings UI test**

In `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`, add these imports:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithContentDescription
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
```

Add this test:

```kotlin
@Test
fun `brand filter detail renders icons for concrete brands only`() {
    composeRule.setContent {
        SettingsDetailScreen(
            section = SettingsSection.BrandFilter,
            options = listOf(
                SettingOptionUiModel(
                    label = "전체",
                    subtitle = "브랜드 제한 없이 가까운 가격을 한 번에 확인합니다.",
                    meta = "현재 선택",
                    action = SettingsAction.BrandFilterSelected(BrandFilter.ALL),
                    isSelected = true,
                    brandIconBrand = null,
                ),
                SettingOptionUiModel(
                    label = "GS칼텍스",
                    subtitle = "GS칼텍스 주유소만 골라 비교합니다.",
                    meta = null,
                    action = SettingsAction.BrandFilterSelected(BrandFilter.GSC),
                    isSelected = false,
                    brandIconBrand = Brand.GSC,
                ),
            ),
            onBackClick = {},
            onOptionClick = {},
        )
    }

    composeRule.onNodeWithContentDescription("GS칼텍스 브랜드").assertExists()
    composeRule.onNodeWithContentDescription("전체 브랜드").assertDoesNotExist()
}
```

- [x] **Step 2: Run settings screen test to verify it fails**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.gasstation.feature.settings.SettingsScreenTest.brand filter detail renders icons for concrete brands only'
```

Expected: FAIL because `SettingOptionUiModel.brandIconBrand` is not defined or the settings row does not render the icon.

- [x] **Step 3: Add optional brand metadata to setting options**

Modify `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`:

```kotlin
package com.gasstation.feature.settings

import com.gasstation.domain.station.model.Brand

data class SettingOptionUiModel(
    val label: String,
    val subtitle: String? = null,
    val meta: String? = null,
    val action: SettingsAction,
    val isSelected: Boolean,
    val brandIconBrand: Brand? = null,
)
```

- [x] **Step 4: Populate brand metadata for concrete brand filters**

In `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`, update the `SettingsSection.BrandFilter` branch:

```kotlin
SettingsSection.BrandFilter -> BrandFilter.entries.map { option ->
    SettingOptionUiModel(
        label = option.toLabel(),
        subtitle = option.toDescription(),
        meta = option.selectedMeta(brandFilter == option),
        action = SettingsAction.BrandFilterSelected(option),
        isSelected = brandFilter == option,
        brandIconBrand = option.brand,
    )
}
```

- [x] **Step 5: Render optional brand icons in settings detail rows**

In `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`, add:

```kotlin
import com.gasstation.core.designsystem.component.GasStationBrandIcon
```

Inside `SettingsDetailOptionRow`, before the `Column(modifier = Modifier.weight(1f), ...)`, add:

```kotlin
option.brandIconBrand?.let { brand ->
    GasStationBrandIcon(
        brand = brand,
        contentDescription = "${option.label} 브랜드",
        modifier = Modifier.padding(end = spacing.space12),
    )
}
```

- [x] **Step 6: Run settings targeted tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.gasstation.feature.settings.SettingsScreenTest'
```

Expected: PASS.

- [x] **Step 7: Commit settings brand filter icons**

Run:

```bash
git add feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt
git commit -m "feat: show brand icons in settings"
```

---

### Task 5: Full Verification

**Files:**
- No new files.
- Verify all changed modules together.

- [ ] **Step 1: Run all affected unit test tasks**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest
```

Expected: PASS for all four Gradle tasks.

- [ ] **Step 2: Check final diff**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: `git status --short` is empty if every task was committed. `git diff --stat HEAD` prints no file changes.

- [ ] **Step 3: Capture implementation summary**

Run:

```bash
git log --oneline -n 5
```

Expected: recent commits include:

```text
feat: show brand icons in settings
feat: show brand icons in watchlist
feat: show brand icons in station list
feat: restore station brand icons
docs: specify brand icon restoration
```

---

## Self-Review

- Spec coverage: covered resource restoration, shared design-system component, station list, watchlist, settings brand filter, `전체` text-only behavior, accessibility descriptions, and tests.
- Placeholder scan: no placeholder tasks remain; every code step names concrete files, snippets, commands, and expected results.
- Type consistency: `Brand.gasStationBrandIconResource()`, `GasStationBrandIcon`, `StationListItemUiModel.brand`, `WatchlistItemUiModel.brand`, and `SettingOptionUiModel.brandIconBrand` use consistent names across all tasks.
