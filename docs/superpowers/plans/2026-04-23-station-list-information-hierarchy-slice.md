# Station List Information Hierarchy Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first production-safe information hierarchy slice by extracting a shared metric primitive from `StationListScreen` into `core:designsystem` and migrating the station-list card to it without changing app behavior.

**Architecture:** This plan covers the first independent slice of the broader information hierarchy redesign. It keeps navigation, ViewModel, domain, data, cache, location, and external map contracts unchanged. The only production code changes are a focused design-system metric primitive and a station-list card refactor that preserves existing UI behavior and tests.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Robolectric Compose UI tests, JUnit4, Gradle Android modules.

---

## Scope Check

The broader plan in `docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-redesign.md` covers design-system tokens, station list, watchlist, settings, visual QA, and README screenshot updates. That is too broad for one safe implementation pass.

This detailed implementation plan covers one testable subsystem:

- Add shared metric hierarchy contracts to `core:designsystem`.
- Add `GasStationMetricBlock` as the shared `label + number + unit` primitive.
- Migrate only `feature:station-list` station cards from the local `MetricBlock` to `GasStationMetricBlock`.
- Preserve current station-list behavior and tests.

Deferred to follow-up plans:

- watchlist metric/supporting block migration
- settings row primitive extraction
- status banner redesign
- token color/font restyle
- README screenshot refresh

---

## File Structure

- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt`
  - Owns the shared metric primitive and the metric emphasis contract.
  - Keeps metric hierarchy close to other design-system components.
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`
  - Adds non-Compose contract tests for metric emphasis role mapping and unit padding.
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  - Imports and uses `GasStationMetricBlock`.
  - Removes the private station-list-only `MetricEmphasis` enum and `MetricBlock` composable.
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
  - Existing tests remain the behavioral safety net for price-first layout, distance alignment, brand icon visibility, delta placement, loading behavior, failure behavior, and refresh behavior.

---

### Task 1: Lock Metric Emphasis Contracts In Design-System Tests

**Files:**
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`

- [x] **Step 1: Add imports for metric padding assertions**

Update the import block in `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt` to include `dp` and `assertTrue`:

```kotlin
package com.gasstation.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
```

- [x] **Step 2: Write failing metric emphasis contract tests**

Add these tests inside `ChromeContractsTest` after `prominent numeric emphasis is reserved for price hero and metric value`:

```kotlin
    @Test
    fun `metric emphasis maps to approved numeric text roles`() {
        assertEquals(
            ChromeTextRole.PriceHero,
            GasStationMetricEmphasis.Primary.numberRole,
        )
        assertEquals(
            ChromeTextRole.MetricValue,
            GasStationMetricEmphasis.Secondary.numberRole,
        )
    }

    @Test
    fun `metric unit padding keeps primary numbers optically dominant`() {
        assertEquals(4.dp, GasStationMetricEmphasis.Primary.unitBottomPadding)
        assertEquals(3.dp, GasStationMetricEmphasis.Secondary.unitBottomPadding)
        assertTrue(
            "Primary metric unit should sit slightly lower than secondary unit.",
            GasStationMetricEmphasis.Primary.unitBottomPadding >
                GasStationMetricEmphasis.Secondary.unitBottomPadding,
        )
    }
```

- [x] **Step 3: Run the focused design-system contract test and verify it fails**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests com.gasstation.core.designsystem.component.ChromeContractsTest
```

Expected: `BUILD FAILED` with unresolved reference errors for `GasStationMetricEmphasis`.

- [x] **Step 4: Commit the failing-test checkpoint**

Run:

```bash
git add core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt
git commit -m "test: lock gas station metric hierarchy contract"
```

Expected: Commit succeeds with only `ChromeContractsTest.kt` staged.

---

### Task 2: Add Shared Metric Primitive To `core:designsystem`

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt`
- Test: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`

- [x] **Step 1: Create the metric primitive implementation**

Create `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt` with this complete content:

```kotlin
package com.gasstation.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorGray2
import com.gasstation.core.designsystem.ColorGray3
import com.gasstation.core.designsystem.GasStationTheme

enum class GasStationMetricEmphasis(
    val numberRole: ChromeTextRole,
    val unitBottomPadding: Dp,
) {
    Primary(
        numberRole = ChromeTextRole.PriceHero,
        unitBottomPadding = 4.dp,
    ),
    Secondary(
        numberRole = ChromeTextRole.MetricValue,
        unitBottomPadding = 3.dp,
    ),
}

@Composable
fun GasStationMetricBlock(
    label: String,
    number: String,
    unit: String,
    emphasis: GasStationMetricEmphasis,
    modifier: Modifier = Modifier,
    labelColor: Color = ColorGray3,
    numberColor: Color = ColorBlack,
    unitColor: Color = ColorGray2,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val numberStyle = when (emphasis) {
        GasStationMetricEmphasis.Primary -> typography.priceHero
        GasStationMetricEmphasis.Secondary -> typography.metricValue
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.meta,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                style = numberStyle,
                color = numberColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(
                    start = spacing.space4,
                    bottom = emphasis.unitBottomPadding,
                ),
                style = typography.meta,
                color = unitColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [x] **Step 2: Run the focused design-system contract test and verify it passes**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests com.gasstation.core.designsystem.component.ChromeContractsTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run the full design-system unit test suite**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit the shared metric primitive**

Run:

```bash
git add core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt \
  core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt
git commit -m "feat: add shared metric hierarchy primitive"
```

Expected: Commit succeeds with `Metric.kt` and `ChromeContractsTest.kt` staged.

---

### Task 3: Establish Station-List Behavioral Baseline Before Refactor

**Files:**
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [x] **Step 1: Run the station-list card hierarchy tests before refactoring**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Confirm the existing tests cover the contracts this refactor must preserve**

Run:

```bash
rg -n "station card surfaces price above station name|station card aligns distance label height|station card places price comparison|station card renders brand icon without visible brand label|loading keeps rendered station list visible" feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt
```

Expected output includes these test names:

```text
station card surfaces price above station name on the reference screen
station card aligns distance label height with price label height
station card places price comparison to the right of fuel and brand icon row
station card renders brand icon without visible brand label
loading keeps rendered station list visible while showing top refresh rail
```

- [x] **Step 3: Commit is skipped for this task**

No files are changed in Task 3. Keep the working tree unchanged before the station-list refactor.

---

### Task 4: Migrate Station List Card To The Shared Metric Primitive

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [x] **Step 1: Add shared metric imports to `StationListScreen.kt`**

Add these imports near the other `core.designsystem.component` imports:

```kotlin
import com.gasstation.core.designsystem.component.GasStationMetricBlock
import com.gasstation.core.designsystem.component.GasStationMetricEmphasis
```

- [x] **Step 2: Replace the complete `StationCard` function**

Replace the current `private fun StationCard(...)` implementation in `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt` with this complete function:

```kotlin
@Composable
private fun StationCard(
    station: StationListItemUiModel,
    fuelTypeLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onWatchToggle: () -> Unit,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography

    GasStationCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(
            horizontal = spacing.space16,
            vertical = spacing.space16,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.space16),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Row(
                    modifier = Modifier
                        .testTag(STATION_LIST_METRIC_ROW_TAG)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space24),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    GasStationMetricBlock(
                        modifier = Modifier.fillMaxHeight(),
                        label = "가격",
                        number = station.priceNumberLabel,
                        unit = station.priceUnitLabel,
                        emphasis = GasStationMetricEmphasis.Primary,
                    )
                    GasStationMetricBlock(
                        modifier = Modifier.fillMaxHeight(),
                        label = "거리",
                        number = station.distanceNumberLabel,
                        unit = station.distanceUnitLabel,
                        emphasis = GasStationMetricEmphasis.Secondary,
                    )
                }
                Text(
                    text = station.name,
                    modifier = Modifier.testTag(STATION_LIST_CARD_TITLE_TAG),
                    style = typography.cardTitle,
                    color = ColorBlack,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FuelChip(text = fuelTypeLabel)
                        GasStationBrandIcon(
                            brand = station.brand,
                            contentDescription = "${station.brandLabel} 브랜드",
                        )
                    }
                    PriceDeltaIndicator(
                        modifier = Modifier.testTag(STATION_LIST_PRICE_CHANGE_TAG),
                        label = station.priceDeltaLabel,
                        tone = station.priceDeltaTone,
                    )
                }
            }
            WatchToggleButton(
                watched = station.isWatched,
                onClick = onWatchToggle,
            )
        }
    }
}
```

- [x] **Step 3: Delete the local station-list metric enum and composable**

Delete this entire block from `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`:

```kotlin
private enum class MetricEmphasis {
    Primary,
    Secondary,
}

@Composable
private fun MetricBlock(
    modifier: Modifier = Modifier,
    label: String,
    number: String,
    unit: String,
    emphasis: MetricEmphasis,
) {
    val spacing = GasStationTheme.spacing
    val typography = GasStationTheme.typography
    val numberStyle = when (emphasis) {
        MetricEmphasis.Primary -> typography.priceHero
        MetricEmphasis.Secondary -> typography.metricValue
    }
    val unitBottomPadding = when (emphasis) {
        MetricEmphasis.Primary -> 4.dp
        MetricEmphasis.Secondary -> 3.dp
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.meta,
            color = ColorGray3,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number,
                style = numberStyle,
                color = ColorBlack,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(
                    start = spacing.space4,
                    bottom = unitBottomPadding,
                ),
                style = typography.meta,
                color = ColorGray2,
            )
        }
    }
}
```

- [x] **Step 4: Run an import cleanup command**

Run:

```bash
./gradlew :feature:station-list:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. If Kotlin reports unused imports as warnings, remove these imports from `StationListScreen.kt` when they are no longer referenced:

```kotlin
import com.gasstation.core.designsystem.ColorGray3
```

Keep this import because `FuelChip` still uses it:

```kotlin
import com.gasstation.core.designsystem.ColorGray4
```

- [x] **Step 5: Verify the local metric implementation is gone and the shared primitive is used**

Run:

```bash
rg -n "private enum class MetricEmphasis|private fun MetricBlock|GasStationMetricBlock|GasStationMetricEmphasis" feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt
```

Expected output includes `GasStationMetricBlock` and `GasStationMetricEmphasis`. Expected output does not include `private enum class MetricEmphasis` or `private fun MetricBlock`.

- [x] **Step 6: Run station-list UI tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests com.gasstation.feature.stationlist.StationListScreenTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 7: Commit the station-list migration**

Run:

```bash
git add feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt
git commit -m "refactor: use shared station metric component"
```

Expected: Commit succeeds with only `StationListScreen.kt` staged.

---

### Task 5: Run Slice-Level Regression Checks

**Files:**
- Verify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt`
- Verify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Verify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`
- Verify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [x] **Step 1: Run design-system tests**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run station-list tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run app demo unit tests that compile the integrated graph**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Assemble the demo app**

Run:

```bash
./gradlew :app:assembleDemoDebug
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Inspect the final diff for scope control**

Run:

```bash
git diff --stat HEAD~2..HEAD
git diff --name-only HEAD~2..HEAD
```

Expected changed production/test files:

```text
core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt
core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt
feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt
```

- [x] **Step 6: Commit a verification note only when a docs update is needed**

No docs commit is required when all behavior is unchanged and the test commands above pass. Leave README screenshots unchanged for this slice.

---

## Self-Review

Spec coverage:

- Design-system shared metric primitive: covered by Task 1 and Task 2.
- Station-list metric migration: covered by Task 3 and Task 4.
- Existing UI contract preservation: covered by Task 3 and Task 5.
- Watchlist/settings/status banner/token restyle: explicitly deferred to follow-up plans because they are independent subsystems.

Placeholder scan:

- This plan contains concrete file paths, concrete Kotlin code, exact Gradle commands, expected command outcomes, and commit commands.
- There are no open implementation blanks.

Type consistency:

- `GasStationMetricEmphasis.Primary` and `GasStationMetricEmphasis.Secondary` are defined in Task 2 and used in Task 4.
- `GasStationMetricBlock` is defined in Task 2 and imported in Task 4.
- `ChromeTextRole.PriceHero` and `ChromeTextRole.MetricValue` already exist in `Chrome.kt` and are tested in `ChromeContractsTest`.
