# Nearby Filter Chip Slimming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Nearby filter rail's 48dp accessible targets while reducing each visible black chip to a lighter 40dp, 14dp-corner surface.

**Architecture:** Keep the change inside `feature:station-list`. `FilterActionChip` will split into an outer transparent interaction container that owns click, enabled state, semantics, test tags, and the 48dp target, plus an inner non-clickable `Surface` that owns the 40dp visual height, black/yellow styling, focus border, and content.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric Compose tests, Roborazzi, JUnit 4

## Global Constraints

- The visible black surface uses a 40dp minimum height at default font scale and a 14dp rounded rectangle.
- The outer interaction target remains at least 48dp.
- Horizontal content padding remains 12dp; chip spacing remains 8dp.
- Yellow labels, chevrons, and the expanded 2dp yellow border remain unchanged.
- Text and chevrons must expand without clipping at 200% font scale.
- The trailing brand chip keeps at least 8dp of clear ivory canvas at 360dp.
- Filter order, menu behavior, actions, settings writes, refresh behavior, and demo/prod data paths do not change.
- No new dependency or design-system primitive is introduced.

## File Map

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt`
  - Owns the outer interaction target and inner visual surface split.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`
  - Owns the exact 40dp black-surface pixel regression in the 360dp populated state.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
  - Existing tests continue to protect 48dp touch targets, click actions, menu selection, dismissal, and 200% font-scale behavior; no source edit is expected.
- `feature/station-list/src/test/snapshots/{brand-menu-open,empty,fuel-menu-open,loading-with-cache,populated-dark,populated,radius-menu-open,stale}.png`
  - Records the approved slimmer surface across every Nearby state that renders the filter rail.

---

### Task 1: Split the filter interaction target from its visual surface

**Files:**
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt:194-198`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt:321-340`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt:178-217`
- Modify: `feature/station-list/src/test/snapshots/brand-menu-open.png`
- Modify: `feature/station-list/src/test/snapshots/empty.png`
- Modify: `feature/station-list/src/test/snapshots/fuel-menu-open.png`
- Modify: `feature/station-list/src/test/snapshots/loading-with-cache.png`
- Modify: `feature/station-list/src/test/snapshots/populated-dark.png`
- Modify: `feature/station-list/src/test/snapshots/populated.png`
- Modify: `feature/station-list/src/test/snapshots/radius-menu-open.png`
- Modify: `feature/station-list/src/test/snapshots/stale.png`
- Verify unchanged: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

**Interfaces:**
- Consumes: `FilterActionChip(label: String, onClick: () -> Unit, modifier: Modifier, expanded: Boolean, menuKind: StationListFilterMenuKind?, enabled: Boolean)`
- Produces: The same private `FilterActionChip` signature and existing `station-list-filter-*` semantics, with a 48dp outer click target and a 40dp minimum inner visual surface.
- Preserves: `StationListAction`, `StationListFilterMenu`, expand/collapse descriptions, `STATION_LIST_FILTER_CHEVRON_TAG_PREFIX`, pending-write disabling, and the trailing 8dp pixel-clearance assertion.

- [ ] **Step 1: Add the failing black-surface height regression**

Add the new assertion to `populated_state`:

```kotlin
@Test
fun populated_state() {
    renderAndCapture("populated.png", populatedState)
    assertTrailingFilterChipClearance("src/test/snapshots/populated.png")
    assertSlimFilterChipVisualHeight("src/test/snapshots/populated.png")
}
```

Add this helper beside `assertTrailingFilterChipClearance`:

```kotlin
private fun assertSlimFilterChipVisualHeight(snapshotPath: String) {
    val bitmap = requireNotNull(
        BitmapFactory.decodeFile(snapshotPath, BitmapFactory.Options().apply { inScaled = false }),
    )
    val railBounds = composeRule.onNodeWithTag(STATION_LIST_FILTER_RAIL_TAG, useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
    val brandBounds = composeRule.onNodeWithTag(STATION_LIST_BRAND_FILTER_TAG, useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
    val expectedVisualHeightPx = with(composeRule.density) { 40.dp.roundToPx() }
    val maxBlackHeightPx = (brandBounds.left.toInt() until brandBounds.right.toInt()).maxOf { x ->
        (railBounds.top.toInt() until railBounds.bottom.toInt()).count { y ->
            bitmap.getPixel(x, y) == ColorBlack.toArgb()
        }
    }

    assertEquals(
        "Expected the visible brand filter surface to be 40dp high.",
        expectedVisualHeightPx,
        maxBlackHeightPx,
    )
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest \
  --tests 'com.gasstation.feature.stationlist.RoborazziStationListScreenTest.populated_state' \
  --warning-mode fail
```

Expected: FAIL from `assertSlimFilterChipVisualHeight`; the current full-height black surface is 48dp rather than the expected 40dp. The existing trailing-clearance assertion must pass before this new assertion fails.

- [ ] **Step 3: Implement the 48dp outer target and 40dp inner surface**

Add imports:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
```

Replace only the body of `FilterActionChip` with:

```kotlin
val shape = RoundedCornerShape(14.dp)
Box(
    modifier = modifier
        .defaultMinSize(minHeight = 48.dp)
        .clip(shape)
        .clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
) {
    Surface(
        color = ColorBlack,
        contentColor = ColorYellow,
        shape = shape,
        border = if (expanded) BorderStroke(2.dp, ColorYellow) else null,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = GasStationTheme.typography.chip,
                maxLines = 1,
            )
            menuKind?.let { kind ->
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.station_list_filter_collapse_menu
                        } else {
                            R.string.station_list_filter_expand_menu
                        },
                    ),
                    modifier = Modifier.testTag("$STATION_LIST_FILTER_CHEVRON_TAG_PREFIX${kind.name}"),
                )
            }
        }
    }
}
```

Do not change `FilterMenuChip`, filter ordering, action dispatch, menu contents, or any domain/data file.

- [ ] **Step 4: Record the approved snapshots and verify GREEN**

Run:

```bash
./gradlew :feature:station-list:recordRoborazziDebug --warning-mode fail
```

Expected: PASS. The eight filter-bearing snapshots in the file list change; permission, GPS, and blocking-failure snapshots remain unchanged. The new pixel assertion reports a maximum black height of exactly 40dp, and the existing trailing-clearance assertion remains green.

- [ ] **Step 5: Inspect the generated visual evidence**

Open and inspect:

```text
feature/station-list/src/test/snapshots/populated.png
feature/station-list/src/test/snapshots/populated-dark.png
feature/station-list/src/test/snapshots/radius-menu-open.png
feature/station-list/src/test/snapshots/fuel-menu-open.png
feature/station-list/src/test/snapshots/brand-menu-open.png
```

Confirm all of the following before continuing:

- all four visible black surfaces are 40dp high at default font scale;
- all four use the same 14dp corner;
- the yellow labels and chevrons remain vertically centered;
- the expanded chip keeps its 2dp yellow border;
- the `전체` chip has an intact right edge and at least 8dp ivory clearance;
- menu placement and row styling are unchanged.

- [ ] **Step 6: Run focused Compose and Roborazzi verification**

Run:

```bash
./gradlew \
  :feature:station-list:testDebugUnitTest \
  :feature:station-list:verifyRoborazziDebug \
  --warning-mode fail
```

Expected: PASS. This includes the existing minimum-touch-height, click action, one-menu-at-a-time, dismissal, 320dp containment, and 200% font-scale tests.

- [ ] **Step 7: Run the repository UI gate and diff checks**

Run:

```bash
scripts/agent/verify.sh auto
git diff --check
git status --short
```

Expected:

- `scripts/agent/verify.sh auto` selects the UI scope and exits 0;
- `git diff --check` prints nothing and exits 0;
- `git status --short` lists only `StationListFilterRail.kt`, `RoborazziStationListScreenTest.kt`, and the eight expected snapshot files.

- [ ] **Step 8: Commit the implementation**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt \
  feature/station-list/src/test/snapshots/brand-menu-open.png \
  feature/station-list/src/test/snapshots/empty.png \
  feature/station-list/src/test/snapshots/fuel-menu-open.png \
  feature/station-list/src/test/snapshots/loading-with-cache.png \
  feature/station-list/src/test/snapshots/populated-dark.png \
  feature/station-list/src/test/snapshots/populated.png \
  feature/station-list/src/test/snapshots/radius-menu-open.png \
  feature/station-list/src/test/snapshots/stale.png
git diff --cached --check
git commit -m "feat: slim nearby filter chips"
```

Expected: one implementation commit containing only the production chip layout, the visual-height regression, and the eight refreshed snapshots. Do not push or create a PR unless the user explicitly requests it.
